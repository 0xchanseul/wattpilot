#!/usr/bin/env bash
set -euo pipefail

# Run on the production VM to refill electricity prices after the scheduler was down.
# The scheduled collection only fetches tomorrow's prices, so days missed during an
# outage are never retried. Safe to re-run: existing hours are overwritten, not duplicated.
#
# Usage: backfill-prices.sh [days] [extra seed_electricity_prices.py args]
#   backfill-prices.sh            # last 7 days through tomorrow
#   backfill-prices.sh 14         # last 14 days
#   backfill-prices.sh 7 --dry-run
#
# Needs python3, psql (postgresql-client) and seed_electricity_prices.py next to this
# script. Database credentials are read from the same env file the backend uses.

ENV_FILE="/app/wattpilot/wattpilot.env"
SEED_SCRIPT="$(dirname "$(readlink -f "$0")")/seed_electricity_prices.py"

DAYS="${1:-7}"
shift || true

read_env() {
  local value
  value="$(grep "^$1=" "$ENV_FILE" | cut -d= -f2-)"
  if [ -z "$value" ]; then
    echo "error: $1 is missing or empty in $ENV_FILE" >&2
    exit 1
  fi
  printf '%s' "$value"
}

# The backend stores a JDBC URL; psql takes the same URL once the "jdbc:" prefix is removed.
# Credentials go through PG* variables so special characters in the password need no escaping.
JDBC_URL="$(read_env SPRING_DATASOURCE_URL)"
PGUSER="$(read_env SPRING_DATASOURCE_USERNAME)"
PGPASSWORD="$(read_env SPRING_DATASOURCE_PASSWORD)"
export PGUSER PGPASSWORD
export WATTPILOT_DATABASE_URL="${JDBC_URL#jdbc:}"

exec python3 "$SEED_SCRIPT" --days "$DAYS" "$@"
