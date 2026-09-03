#!/usr/bin/env python3
"""Seed the local database with real electricity prices for backend testing.

This is a developer convenience script, not part of the application. It does the
same job as the scheduled collection (fetch a day of hourly prices per Norwegian
bidding zone from Hva koster strommen and upsert them into ``electricity_prices``),
but on demand and without running the Spring Boot app.

The upsert matches the ``uq_electricity_prices_provider_area_start`` constraint, so
re-running for the same date and area overwrites instead of duplicating.

Requirements: Python 3.8+ (standard library only) and the local Postgres container
from ``docker/postgres/docker-compose.yml`` running. SQL is piped through
``docker exec`` into that container, so no Python database driver is needed.

Examples::

    # Today and tomorrow, all areas (tomorrow may 404 before ~13:00 CET)
    python seed_electricity_prices.py

    # A specific past day, one area
    python seed_electricity_prices.py --from 2025-01-15 --to 2025-01-15 --areas NO1

    # A range, print the SQL instead of running it
    python seed_electricity_prices.py --from 2025-01-10 --to 2025-01-15 --dry-run
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import subprocess
import sys
import urllib.error
import urllib.request
from decimal import Decimal

DEFAULT_BASE_URL = "https://www.hvakosterstrommen.no/api/v1"
DEFAULT_CONTAINER = "wattpilot-postgres"
DEFAULT_DB = "wattpilot"
DEFAULT_USER = "wattpilot"
ALL_AREAS = ["NO1", "NO2", "NO3", "NO4", "NO5"]

PROVIDER = "HVA_KOSTER_STROMMEN"
CURRENCY = "NOK"
USER_AGENT = "WattPilot-dev-seed/1.0 (local backend testing)"


class NotPublishedYet(Exception):
    """The provider has no file for this date and area yet (HTTP 404)."""


def parse_args(argv: list[str]) -> argparse.Namespace:
    today = dt.date.today()
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--from", dest="date_from", type=_iso_date, default=today,
        help="First date to fetch, inclusive (YYYY-MM-DD). Default: today.",
    )
    parser.add_argument(
        "--to", dest="date_to", type=_iso_date, default=today + dt.timedelta(days=1),
        help="Last date to fetch, inclusive (YYYY-MM-DD). Default: tomorrow.",
    )
    parser.add_argument(
        "--areas", nargs="+", metavar="AREA", default=ALL_AREAS,
        help=f"Price areas to fetch. Default: {' '.join(ALL_AREAS)}.",
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Provider API base URL.")
    parser.add_argument("--container", default=DEFAULT_CONTAINER, help="Postgres container name.")
    parser.add_argument("--db", default=DEFAULT_DB, help="Database name.")
    parser.add_argument("--user", default=DEFAULT_USER, help="Database user.")
    parser.add_argument(
        "--dry-run", action="store_true", help="Print the SQL instead of executing it."
    )
    args = parser.parse_args(argv)

    for area in args.areas:
        if area not in ALL_AREAS:
            parser.error(f"unknown area {area!r}; expected one of {', '.join(ALL_AREAS)}")
    if args.date_to < args.date_from:
        parser.error("--to must not be earlier than --from")
    return args


def _iso_date(value: str) -> dt.date:
    try:
        return dt.date.fromisoformat(value)
    except ValueError:
        raise argparse.ArgumentTypeError(f"invalid date {value!r}; expected YYYY-MM-DD")


def date_range(start: dt.date, end: dt.date):
    day = start
    while day <= end:
        yield day
        day += dt.timedelta(days=1)


def fetch_day(base_url: str, area: str, day: dt.date) -> list[dict]:
    url = f"{base_url}/prices/{day.year}/{day.month:02d}-{day.day:02d}_{area}.json"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            payload = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        if error.code == 404:
            raise NotPublishedYet from error
        raise RuntimeError(f"HTTP {error.code} for {url}") from error
    return json.loads(payload, parse_float=Decimal)


def _sql_literal(value: str) -> str:
    if "'" in value:
        raise ValueError(f"unexpected quote in value from provider: {value!r}")
    return f"'{value}'"


def build_rows(slots: list[dict], area: str) -> list[str]:
    rows = []
    for slot in slots:
        starts_at = _sql_literal(str(slot["time_start"]))
        ends_at = _sql_literal(str(slot["time_end"]))
        price = Decimal(str(slot["NOK_per_kWh"]))
        rows.append(
            f"('{PROVIDER}', '{area}', {starts_at}, {ends_at}, {price}, '{CURRENCY}', now())"
        )
    return rows


def build_sql(values: list[str]) -> str:
    joined = ",\n    ".join(values)
    return (
        "INSERT INTO electricity_prices\n"
        "    (provider, price_area, starts_at, ends_at, price_per_kwh, currency, fetched_at)\n"
        f"VALUES\n    {joined}\n"
        "ON CONFLICT (provider, price_area, starts_at) DO UPDATE SET\n"
        "    ends_at = EXCLUDED.ends_at,\n"
        "    price_per_kwh = EXCLUDED.price_per_kwh,\n"
        "    currency = EXCLUDED.currency,\n"
        "    fetched_at = EXCLUDED.fetched_at;\n"
    )


def run_psql(sql: str, container: str, db: str, user: str) -> None:
    command = [
        "docker", "exec", "-i", container,
        "psql", "-U", user, "-d", db, "-v", "ON_ERROR_STOP=1", "-q", "-f", "-",
    ]
    try:
        subprocess.run(command, input=sql, text=True, check=True)
    except FileNotFoundError:
        sys.exit("error: 'docker' not found on PATH")
    except subprocess.CalledProcessError as error:
        sys.exit(f"error: psql failed with exit code {error.returncode}")


def main(argv: list[str]) -> None:
    args = parse_args(argv)

    values: list[str] = []
    collected = 0
    for day in date_range(args.date_from, args.date_to):
        for area in args.areas:
            try:
                slots = fetch_day(args.base_url, area, day)
            except NotPublishedYet:
                print(f"  {day} {area}: not published yet, skipped")
                continue
            except RuntimeError as error:
                print(f"  {day} {area}: {error}")
                continue
            rows = build_rows(slots, area)
            values.extend(rows)
            collected += len(rows)
            print(f"  {day} {area}: {len(rows)} hourly prices")

    if not values:
        sys.exit("no prices fetched; nothing to write")

    sql = build_sql(values)
    if args.dry_run:
        print("\n--- SQL (dry run) ---")
        print(sql)
        return

    run_psql(sql, args.container, args.db, args.user)
    print(f"\nUpserted {collected} rows into {args.db}.electricity_prices")


if __name__ == "__main__":
    main(sys.argv[1:])
