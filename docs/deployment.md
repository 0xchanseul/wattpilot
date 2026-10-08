# Deployment Strategy

WattPilot V1 will be developed and tested locally first. Cloud resources will be provisioned only after the core V1 features are sufficiently complete in order to minimize unnecessary cloud costs during development.

```
Development
React + Spring Boot + PostgreSQL
          ↓
       Local Environment
          ↓
      V1 Completion
          ↓
     Azure Deployment
          ↓
   Production Environment
```

The initial project will use only two long-lived environments:

- **Local** — development and testing
- **Production** — portfolio deployment and public access, hosted on Azure

Production started out as a short-lived **Azure test environment** used to run the V1 schedulers against real data before a domain and HTTPS existed. Once a domain (`wattpilot.dev` / `www.wattpilot.dev`) and a Let's Encrypt certificate were obtained, the same VM was promoted to Production in place — see "Production Architecture" below. There is no separate AWS environment.

A separate staging environment may be added later if needed.

# Local Development Environment

During development, the frontend and backend will run locally, while PostgreSQL will run in Docker.

```
React
  ↓
Spring Boot
  ↓
PostgreSQL (Docker)
```

Main components:

- React + Vite
- Spring Boot
- PostgreSQL
- Docker Compose
- Flyway

Database schema changes are managed through **Flyway migration scripts** rather than manual schema changes in tools such as DBeaver.

# Production Architecture (Azure)

## Purpose and scope

This is WattPilot's one production environment. It started as a throwaway environment to run the V1
schedulers continuously against real inputs before a domain existed:

- **Price collection** — fetches Norwegian next-day prices from the public Hva koster strømmen API on
  the real `Europe/Oslo` schedule (`0 15 13-22 * * *`).
- **Mock Charging execution** — drives confirmed reservations through Mock Charging every minute.

Running these on a developer laptop is not practical, and a multi-service cloud stack is not
cost-effective for a portfolio project. A single small Azure VM plus a managed PostgreSQL server covers
both the scheduler workload and, now that a domain and certificate exist, public production traffic —
while staying inside Azure's 12-month free grants.

Current state:

- It uses the `prod` Spring profile (see `application-prod.yml`): Swagger/OpenAPI disabled, only
  `/actuator/health` exposed, datasource pointed at the managed PostgreSQL server over TLS.
- It has a real domain (`wattpilot.dev`, `www.wattpilot.dev`) with a Let's Encrypt/certbot certificate,
  fronted by nginx.
- nginx also serves the built React frontend as static files and reverse-proxies `/api/` to the backend
  container — see "Frontend Deployment" and "Backend Deployment" below.
- There is no separate AWS environment; this VM is the deployment target referenced throughout this
  document as "Production".

## Architecture

```
                              User
                               │
                        HTTPS (wattpilot.dev)
                               │
                               ▼
                 Azure VM (resource group: wattpilot_rg)
                 ┌─────────────────────────────────────────┐
                 │  nginx (TLS termination, Let's Encrypt)  │
                 │    /            → static React build     │
                 │    /api/        → wattpilot-backend:8080 │
                 │    /actuator/*  → wattpilot-backend:8080 │
                 └───────────────────┬───────────────────────┘
                                     │ Docker: wattpilot-backend (profile: prod)
                                     │ JDBC + TLS
                                     ▼
                    PostgreSQL Flexible Server (Standard_B1ms, 32 GiB)

Local machine
─────────────
docker build + push  ──▶  GHCR (ghcr.io/<owner>/wattpilot-backend)
npm run build         ──▶  dist/ copied to the VM for nginx to serve
```

| Purpose | Choice |
| --- | --- |
| Backend host | Azure VM `Standard_B1s` (1 vCPU / 1 GiB), 12-month free |
| Reverse proxy / TLS | nginx + Let's Encrypt (certbot) on the VM |
| Domain | `wattpilot.dev`, `www.wattpilot.dev` |
| Database | Azure Database for PostgreSQL Flexible Server `Standard_B1ms` + 32 GiB, 12-month free |
| Image registry | GitHub Container Registry (GHCR), free |
| DB network access | Private access (VNet integration): public network access disabled, reachable only from inside the VNet (see "Database Deployment") |
| Frontend | Static build served by nginx from the same VM, same origin as the API |

Confirm the currently free-eligible VM size and the PostgreSQL free offer at provisioning time; Azure
adjusts both periodically. Cost guardrails and teardown notes for the underlying VM/DB grants are still
in "Cost guardrails and teardown" below — teardown no longer applies automatically once this environment
is Production, but the free-grant expiry still needs a plan (upgrade to a paid tier, or migrate).

## Files

| File | Role |
| --- | --- |
| `backend/Dockerfile` | Multi-stage build of the backend image (built locally, not on the VM) |
| `backend/src/main/resources/application-prod.yml` | `prod` profile: managed DB with TLS, Swagger disabled, only `/actuator/health` exposed |
| `backend/src/main/resources/application-cloud.yml` | `cloud` profile: currently unused, kept for a possible future throwaway environment |
| `deploy/azure/docker-compose.yml` | Source of truth for what runs on the VM (backend container, bound to `127.0.0.1:8080`). Lives at `/app/wattpilot/docker-compose.yml` on the VM — must be copied/edited there by hand, it is not synced automatically |
| `deploy/azure/.env.example` | Template for the real runtime env file, which lives at `/etc/wattpilot/wattpilot.env` on the VM (not `.env` next to the compose file) and is never committed. The template uses the `POSTGRES_*` variables read by `application-prod.yml`; the production VM's file instead sets `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`, which take precedence over the `POSTGRES_*` values (the database user there is the application user, not the server administrator) |
| `deploy/azure/nginx/wattpilot.conf` | nginx site config: TLS termination, static frontend (served from `/app/wattpilot/frontend/dist`), `/api/` reverse proxy |

## Procedure

The steps below are kept as a record of the initial bootstrap. Steps 1–6 provisioned the VM and
database under the `cloud` profile before a domain existed; step 7 covers the later cutover to
`prod` with a domain, TLS, nginx, and the frontend.

### 1. Build and push the image (local machine)

```bash
cd backend
docker build -t ghcr.io/<owner>/wattpilot-backend:latest .

# CR_PAT: a GitHub personal access token with write:packages (read:packages is enough on the VM)
echo $CR_PAT | docker login ghcr.io -u <owner> --password-stdin
docker push ghcr.io/<owner>/wattpilot-backend:latest
```

Tests are skipped inside the image build because they need a Docker daemon (Testcontainers); run
`./gradlew test` locally before pushing.

### 2. Provision the database

```bash
RG=wattpilot_rg
LOC=swedencentral

az group create -n $RG -l $LOC

az postgres flexible-server create \
  -g $RG -n <pg-server-name> -l $LOC \
  --tier Burstable --sku-name Standard_B1ms \
  --storage-size 32 --version 16 \
  --admin-user wattpilot --admin-password '<admin-password>' \
  --database-name wattpilot \
  --public-access None
```

### 3. Provision the VM

```bash
az vm create \
  -g $RG -n wattpilot-vm -l $LOC \
  --image Ubuntu2404 --size Standard_B1s \
  --admin-username azureuser --generate-ssh-keys

az vm open-port -g $RG -n wattpilot-vm --port 8080

VM_IP=$(az vm show -d -g $RG -n wattpilot-vm --query publicIps -o tsv)

# Restrict the database to the VM only
az postgres flexible-server firewall-rule create \
  -g $RG -n <pg-server-name> --rule-name allow-vm \
  --start-ip-address $VM_IP --end-ip-address $VM_IP
```

The firewall rule above is how the bootstrap was originally described. The running production server
was set up with private access (VNet integration) instead, so this step does not apply to it; see
"Database Deployment".

### 4. Install Docker on the VM

```bash
ssh azureuser@$VM_IP
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
exit   # re-connect so the group membership applies
```

### 5. Configure and run

```bash
ssh azureuser@$VM_IP
mkdir -p ~/wattpilot && cd ~/wattpilot

# copy deploy/azure/docker-compose.yml here (scp or paste), then:
cp .env.example .env   # or create it from deploy/azure/.env.example
#   BACKEND_IMAGE=ghcr.io/<owner>/wattpilot-backend:latest
#   POSTGRES_HOST=<pg-server-name>.postgres.database.azure.com
#   POSTGRES_USER / POSTGRES_PASSWORD / POSTGRES_DB=wattpilot
#   JWT_SECRET=$(openssl rand -base64 32)
#   CORS_ALLOWED_ORIGINS=http://localhost:5173

echo $CR_PAT | docker login ghcr.io -u <owner> --password-stdin
docker compose pull
docker compose up -d
docker compose logs -f
```

### 6. Verify

- Logs show Flyway applying the migrations, then `Started WattpilotBackendApplication`.
- `curl http://$VM_IP:8080/v3/api-docs` returns the OpenAPI JSON.
- After 13:15 Oslo, logs show a price-collection run; every minute, a Mock Charging execution tick.

### 7. Production cutover: domain, TLS, nginx, frontend

This is the step that turned the bootstrap VM above into Production, completed and verified
2026-09-15. It assumes DNS for `wattpilot.dev` and `www.wattpilot.dev` already points at the VM's
public IP, and that a Let's Encrypt certificate has already been issued via certbot.

**VM path note:** on this VM, the backend compose project actually lives at **`/app/wattpilot`**,
not `~/wattpilot` as the earlier bootstrap steps above assume — check where `docker-compose.yml`
really is (`sudo find / -maxdepth 3 -name docker-compose.yml 2>/dev/null`) before following the
paths below blindly on a different VM. The frontend static files also ended up at
`/app/wattpilot/frontend/dist` rather than `/var/www/...`, to keep everything under one directory.
Whichever path you use, `deploy/azure/nginx/wattpilot.conf`'s `root` line must match exactly.

1. **Open 80/443, close 8080 externally.** nginx becomes the only public entry point.

   ```bash
   az vm open-port -g $RG -n wattpilot-vm --port 80
   az vm open-port -g $RG -n wattpilot-vm --port 443
   az network nsg rule delete -g $RG --nsg-name <vm-nsg-name> -n open-port-8080
   ```

2. **Switch the backend to the `prod` profile and bind it to localhost.** `deploy/azure/docker-compose.yml`
   in this repo already sets `SPRING_PROFILES_ACTIVE: prod` and `ports: ["127.0.0.1:8080:8080"]` —
   but that file has to actually be copied/edited into place on the VM; a repo-side edit does
   nothing by itself. On the VM:

   ```bash
   cd /app/wattpilot
   sudo nano docker-compose.yml   # confirm SPRING_PROFILES_ACTIVE: prod and the 127.0.0.1:8080 port binding
   sudo nano /etc/wattpilot/wattpilot.env   # confirm CORS_ALLOWED_ORIGINS=https://wattpilot.dev,https://www.wattpilot.dev
   docker compose pull && docker compose up -d   # `up -d`, not just `restart` — env/config changes need a recreate
   docker exec wattpilot-backend env | grep -E "SPRING_PROFILES_ACTIVE|CORS"   # confirm both took effect
   ```

3. **Install nginx and install the site config** (`deploy/azure/nginx/wattpilot.conf`):

   ```bash
   sudo apt update && sudo apt install -y nginx
   scp deploy/azure/nginx/wattpilot.conf azureuser@$VM_IP:~/wattpilot.conf
   ssh azureuser@$VM_IP
   sudo cp ~/wattpilot.conf /etc/nginx/sites-available/wattpilot
   sudo ln -s /etc/nginx/sites-available/wattpilot /etc/nginx/sites-enabled/wattpilot
   sudo rm -f /etc/nginx/sites-enabled/default
   sudo certbot certificates   # confirm the live/ directory name matches the conf file's paths
   sudo nginx -t && sudo systemctl reload nginx
   ```

   Two gotchas hit while doing this: `cp file /path/that/is/a/directory` silently copies the file
   *into* that directory instead of creating `/path/that/is/a/directory` as a file — if
   `sites-available/wattpilot` already existed as a directory, delete it (`sudo rm -rf`) before
   `cp`, and make sure `sites-enabled/wattpilot` ends up a symlink to a *file*. Also, nginx 1.24.x
   (Ubuntu 24.04's packaged version) doesn't support the newer `http2 on;` directive — this repo's
   conf already uses the older `listen 443 ssl http2;` form for that reason.

4. **Build and deploy the frontend** (`frontend/.env.production` already points at
   `https://www.wattpilot.dev/api/v1`):

   ```bash
   # local
   cd frontend
   npm run build
   scp -r dist\* azureuser@$VM_IP:/tmp/wattpilot-dist/

   # VM — target directory must match the conf file's `root`
   ssh azureuser@$VM_IP
   sudo mkdir -p /app/wattpilot/frontend/dist
   sudo rsync -a --delete /tmp/wattpilot-dist/ /app/wattpilot/frontend/dist/
   ls -la /app/wattpilot/frontend/dist   # must show index.html — an empty target causes an
                                          # nginx 500 ("internal redirection cycle") on every request
   ```

   `/app` is not a locked-down home directory, but nginx's worker still runs as `www-data` and
   needs explicit traverse/read permission down to the target:

   ```bash
   sudo chmod o+x /app /app/wattpilot /app/wattpilot/frontend
   sudo chmod -R o+rX /app/wattpilot/frontend/dist
   sudo -u www-data test -x /app/wattpilot/frontend/dist && echo OK || echo FAIL
   ```

5. **Verify:**

   - `curl -I https://www.wattpilot.dev` returns `200` and serves the SPA shell.
   - `curl https://www.wattpilot.dev/actuator/health` returns `{"status":"UP"}`.
   - `curl -I https://www.wattpilot.dev/v3/api-docs` returns `404` (Swagger is disabled in `prod`).
     If this instead returns `200` (Swagger enabled), the `prod` profile isn't actually active —
     recheck step 2.
   - Sign up / log in through the real domain; the refresh-token cookie now works normally
     (`Secure` + real HTTPS + same-origin, no more tunnel needed). A fresh sign-up is required even
     if you already have a local-dev account — the production PostgreSQL Flexible Server is a
     separate database from local Docker Postgres.
   - Certbot's systemd timer (`systemctl list-timers | grep certbot`) handles renewal; nginx just
     needs a reload after a renewal (`certbot renew` does this automatically via its nginx hook).

To exercise the Mock Charging execution scheduler, create a confirmed charging schedule
(`POST /charging-schedules`) through the deployed frontend or Swagger UI (tunnel to `localhost:8080`
if the raw API needs poking, since `/v3/api-docs`/`/swagger-ui` are disabled in `prod`). Price
collection needs no interaction. `backend/scripts/seed_electricity_prices.py` can seed prices
directly against the managed database if a run has not happened yet.

## Redeploying

The normal path is the `Deploy` GitHub Actions workflow (manual `workflow_dispatch` only; see
"CI/CD" below). The commands here are the manual fallback for when Actions or the VM's SSH access
is unavailable.

```bash
# Backend: local
docker build -t ghcr.io/<owner>/wattpilot-backend:latest ./backend
docker push ghcr.io/<owner>/wattpilot-backend:latest

# Backend: VM (docker-compose.yml lives at /app/wattpilot on this VM, see step 7 above)
cd /app/wattpilot && docker compose pull && docker compose up -d

# Frontend: local
cd frontend && npm run build
scp -r dist\* azureuser@$VM_IP:/tmp/wattpilot-dist/

# Frontend: VM
ssh azureuser@$VM_IP 'sudo rsync -a --delete /tmp/wattpilot-dist/ /app/wattpilot/frontend/dist/'
```

## Cost guardrails and teardown

- The VM and the Flexible Server are covered by **12-month** free grants tied to the account creation
  date. After that they bill at pay-as-you-go (roughly USD 25–30 / month combined).
- Standard public IP, egress above the free allowance, and extra disk are **not** covered by the
  compute free grant and can produce small charges even in year one.
- Set a Cost Management **budget alert** at a low threshold (e.g. USD 5) right after provisioning.
- Add a calendar reminder to migrate or delete before the 12-month mark.
- Full teardown removes every resource. All Azure resources (VM, network, database) live in the single
  resource group `wattpilot_rg`, so this deletes Production, including the database and its automated
  backups:

  ```bash
  az group delete -n wattpilot_rg --yes --no-wait
  ```

# Frontend Deployment

The React frontend is built into static files and served by nginx from the same Azure VM as the
backend — see "Production Architecture (Azure)" above for the full picture.

```
React Source
    ↓
npm run build
    ↓
dist/
    ↓
rsync to the VM
    ↓
nginx (static files)
    ↓
User
```

Domain structure:

```
www.wattpilot.dev
→ Frontend (nginx static files) + Backend API under /api/ (nginx reverse proxy)

wattpilot.dev
→ 301 redirect to www.wattpilot.dev
```

The frontend and backend intentionally share one origin (rather than a separate `api.` subdomain)
so the browser never needs CORS or a cross-site cookie for the refresh token.

# Backend Deployment

The Spring Boot backend is packaged as a Docker image and run with Docker Compose on the VM.

```
Spring Boot
    ↓
Gradle Build
    ↓
Docker Image
    ↓
GitHub Container Registry (GHCR)
    ↓
Azure VM (docker compose pull && up, `prod` profile)
```

The backend uses container-based deployment rather than manually installing and running a JAR on
the server. It listens on `127.0.0.1:8080` only; nginx is the sole public entry point.

# Database Deployment

The production database is **Azure Database for PostgreSQL Flexible Server**.

```
Azure VM (backend container)
     │
     │ JDBC + TLS
     ▼
PostgreSQL Flexible Server
```

The database is not publicly exposed. The server uses private access (VNet integration): it sits in
a delegated subnet (`wattpilot-db-snet` in `wattpilot-vnet`), resolves through a private DNS zone
(`wattpilot-postgres.private.postgres.database.azure.com`), and has public network access disabled.
Only the application VM inside the VNet can connect. Consequences:

- There are no firewall rules; `az postgres flexible-server firewall-rule` commands are rejected
  ("Firewall rule operations are not supported for a server without public access enabled").
- Ad-hoc queries (for example `psql`) must be run from the VM, not from a laptop or Azure Cloud Shell.

Production values, as read from the running environment on 2026-09-25: server `wattpilot-postgres`,
region Sweden Central, PostgreSQL 16, `Standard_B1ms`, 32 GiB. All Azure resources, including the VM,
are in the single resource group `wattpilot_rg`.

Production schema changes are managed through Flyway.

```
Backend Deployment
       ↓
Spring Boot Startup
       ↓
Flyway Migration
       ↓
PostgreSQL Schema Update
```

This keeps local and production database schemas consistent.

# Database Backup and Restore

## Backup policy

Backups are the automated backups of Azure Database for PostgreSQL Flexible Server. Nothing runs on
the VM for backups.

| Item | Value |
| --- | --- |
| Mechanism | Azure automated backups with point-in-time restore (PITR) |
| Frequency | Daily; the Azure portal backup list shows one completed backup per day at about 05:45 UTC |
| Retention | 7 days (`backupRetentionDays: 7`) |
| Geo-redundant backup | Disabled |

Decision (2026-09-25): no additional manual `pg_dump` backup. The data is portfolio data, the
electricity prices can be fetched again from the external API, and the built-in PITR was verified by
the rehearsal below. Known limits of this choice:

- Data cannot be recovered to a point older than the retention window (7 days).
- Automated backups belong to the server, so they must not be relied on after the server itself is
  deleted.
- With geo-redundant backup disabled, a regional outage is not covered.

Revisit this decision if any of these limits becomes unacceptable.

## Restore procedure

A restore always creates a **new** server; the existing server is never overwritten. Because the
production server uses private access, the new server must be placed in the same delegated subnet
and private DNS zone, and it can only be reached from the VM.

1. Choose the restore point (UTC) inside the retention window.
2. Create the restored server (Azure Cloud Shell or any machine with the Azure CLI):

   ```bash
   RG=wattpilot_rg
   SRV=wattpilot-postgres
   RESTORE=<new-server-name>

   SUBNET_ID=$(az postgres flexible-server show -g $RG -n $SRV --query network.delegatedSubnetResourceId -o tsv)
   DNS_ID=$(az postgres flexible-server show -g $RG -n $SRV --query network.privateDnsZoneArmResourceId -o tsv)

   az postgres flexible-server restore -g $RG -n $RESTORE \
     --source-server $SRV --restore-time "<UTC timestamp, e.g. 2026-09-25T07:16:09Z>" \
     --subnet "$SUBNET_ID" --private-dns-zone "$DNS_ID"
   ```

   The restored server keeps the source server's administrator login and database roles. Its host name
   is `<new-server-name>.postgres.database.azure.com`. The server name must be unique, so it cannot
   reuse the name of the server it was restored from while that server still exists.
3. Verify the restored data **from the VM** (the only place that can reach it). Connect with `psql`
   using the application's database user and compare with the source server:

   ```sql
   select installed_rank, version, description, success from flyway_schema_history order by installed_rank;

   select table_name,
          (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text::int as row_count
   from information_schema.tables
   where table_schema = 'public' and table_type = 'BASE TABLE'
   order by 1;
   ```

   The Flyway history must match exactly. Row counts may be lower on the restored server only by the
   rows written after the restore point.
4. Cutover (for a real recovery only; this step was **not** exercised in the rehearsal). On the VM,
   point the backend at the restored server and recreate the container:

   ```bash
   sudo nano /etc/wattpilot/wattpilot.env   # change the datasource host to the restored server
   cd /app/wattpilot && docker compose up -d   # `up -d` is required; a plain restart does not reload env
   curl https://www.wattpilot.dev/actuator/health
   ```

   On the production VM the datasource is configured through `SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`, not the `POSTGRES_*` variables that
   `application-prod.yml` and `deploy/azure/.env.example` use. Check which variables the file actually
   contains and change the host in `SPRING_DATASOURCE_URL`.
5. After the recovery is confirmed, decide what to do with the old server. A rehearsal server must be
   deleted right away, because it is billed while it exists:

   ```bash
   az postgres flexible-server delete -g $RG -n $RESTORE --yes
   ```

## Restore rehearsal record

| Item | Result |
| --- | --- |
| Date | 2026-09-25 |
| Source server | `wattpilot-postgres`, PostgreSQL 16, `Standard_B1ms`, 32 GiB, Sweden Central |
| Restore point | 2026-09-25T07:16:09Z (one hour before the restore command) |
| Restore duration | 7 min 19.9 s, measured with `time` around `az postgres flexible-server restore` until it returned with the server in state `Ready` |
| Verification | From the VM: Flyway history V1-V5 identical (all `success = t`); row counts identical for all 10 tables |
| Cleanup | Rehearsal server deleted after verification |

Limits of this rehearsal:

- Nothing was written to production between the restore point and the verification, so the identical
  row counts confirm that the restore is complete and consistent, but do not prove that the chosen
  point in time was applied exactly.
- The cutover step (repointing the backend to the restored server) was not exercised.
- The measured time covers only creating the restored server. A real recovery adds the verification
  and the cutover.

# CI/CD

Implemented as two GitHub Actions workflows, both targeting the Azure VM described above rather
than AWS services.

## CI (`.github/workflows/ci.yml`)

Runs on every push to `main` (`workflow_dispatch` also available for a manual run). It does not
gate merges yet — WattPilot currently pushes directly to `main` rather than using pull requests
(see TODO.md, section 2.1, for the branch-protection upgrade path).

```
Push to main
      ↓
┌─────────────────┐   ┌──────────────────┐
│ backend job      │   │ frontend job     │
│ Gradle build     │   │ npm ci           │
│ + unit tests     │   │ lint             │
│ + Testcontainers │   │ build (tsc+vite) │
│   integration    │   │                  │
│   tests          │   │                  │
└─────────────────┘   └──────────────────┘
```

## CD (`.github/workflows/deploy.yml`)

Manual only (`workflow_dispatch`, triggered from the Actions tab). Re-runs the same backend/frontend
checks as CI against the exact commit being deployed, then deploys over SSH.

```
"Run workflow" clicked
      ↓
┌─────────────────────────┐   ┌───────────────────────┐
│ backend job              │   │ frontend job           │
│ Gradle build + tests     │   │ npm ci, lint, build    │
│ Docker build             │   │ upload dist/ artifact  │
│ Push ghcr.io/…:<sha>     │   │                        │
│      and :latest         │   │                        │
└─────────────────────────┘   └───────────────────────┘
      ↓                              ↓
            deploy job (needs both)
      ↓
SSH to the VM, run deploy/azure/deploy.sh <sha>:
  - update BACKEND_IMAGE in /etc/wattpilot/wattpilot.env
  - docker compose pull && up -d
  - poll /actuator/health; on failure, revert BACKEND_IMAGE to the
    previous tag, restart, and fail the workflow run
      ↓
rsync frontend-dist/ to /app/wattpilot/frontend/dist on the VM
      ↓
smoke check: curl https://www.wattpilot.dev/actuator/health
```

Deployment is versioned by commit SHA (`ghcr.io/0xchanseul/wattpilot-backend:<sha>`), so a bad
deploy's automatic rollback returns to the previous image tag. This covers the running container
only — it does not attempt to undo an already-applied Flyway migration; see TODO.md, section 2,
for planned failure-handling/runbook work.

Required GitHub Actions secrets: `VM_HOST`, `VM_USER`, `VM_SSH_KEY` (a deploy-only SSH key; its
public half must be added to the VM's `~/.ssh/authorized_keys`). GHCR push uses the built-in
`GITHUB_TOKEN`. The VM must already be able to pull from GHCR, same as the manual procedure above.

# Configuration & Secrets

Environment-specific configuration uses Spring Profiles.

```
local
cloud   (currently unused — see application-cloud.yml)
prod    (the Azure VM described above)
```

Example configuration files:

```
application.yml
application-local.yml
application-prod.yml
```

Sensitive values must not be stored in the Git repository, including:

- Database passwords
- JWT secrets
- External API tokens
- Cloud provider credentials

Local development uses a single git-ignored `.env` file in the repository root, shared by both the local PostgreSQL container and the backend:

```
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
POSTGRES_PORT
```

- Only `.env.example` (placeholder values) is committed. Each developer copies it to `.env`.
- PostgreSQL container: `docker compose --env-file .env -f docker/postgres/docker-compose.yml up -d`
- Backend: `application-local.yml` imports the file via `spring.config.import: optional:file:../../.env[.properties]` and maps the `POSTGRES_*` values onto the datasource. The import is optional, so plain environment variables also work.

Production secrets live in `/etc/wattpilot/wattpilot.env` on the VM (never committed) — see
"Production Architecture (Azure)" → "Files" above.

## Smartcar vehicle telemetry (V1.5, optional)

Off by default everywhere (`SMARTCAR_ENABLED` defaults to false). Only needed if you want to
exercise the read-only vehicle-connection feature; charging itself never depends on it. See
TODO.md (not tracked by Git), section 3.3, for why this replaced the originally-planned Tibber
integration.

1. Create a free app at [dashboard.smartcar.com](https://dashboard.smartcar.com) (no credit card
   required).
2. Under Vehicle Access, select only `read_battery` and `read_charge` — never `control_charge`.
   Charging execution stays on Mock Charging regardless of this feature; requesting a control
   permission here would not do anything on the backend, since `SmartcarClient` never calls a
   command endpoint.
3. Register the redirect URI exactly as WattPilot's frontend origin plus
   `/vehicle-connections/callback`:
   - Local: `http://localhost:5173/vehicle-connections/callback`
   - Production: `https://www.wattpilot.dev/vehicle-connections/callback`
4. Set these variables (local: root `.env`, see `.env.example`; production: append to
   `/etc/wattpilot/wattpilot.env` on the VM, then `docker compose up -d` to pick them up — see
   `deploy/azure/.env.example`):
   ```
   SMARTCAR_ENABLED=true
   SMARTCAR_CLIENT_ID=...
   SMARTCAR_CLIENT_SECRET=...
   SMARTCAR_APPLICATION_ID=...
   SMARTCAR_REDIRECT_URI=...   # exactly the URI registered in step 3
   SMARTCAR_MODE=simulated     # or "live" for a real vehicle account
   ```
5. Use `mode=simulated` (the default, and the mode used in production) to test end-to-end without a
   real car. Create the vehicle in the Smartcar dashboard's Vehicle Simulator (Simulator -> Add
   simulated vehicle), then set the vehicle's signal values in the simulator and Publish them; until
   then every signal returns `SIGNAL_NOT_FOUND`.
   - To connect the vehicle through WattPilot's own "Connect vehicle" flow, log in on the Smartcar
     Connect screen with that vehicle's own credentials: in the dashboard, open the vehicle's
     "Connect this simulated vehicle to your application" dialog, "Connect with Smartcar Connect" tab,
     and use its Username (`sim-vid-<vehicle>@smartcar.dev`) and Password with any brand. Choose
     the country flag matching the region the vehicle was created for, otherwise the login fails.
     The legacy simulator shows the same credentials behind its "Connect Credentials" button.
   - Logging in with any other credentials succeeds but creates a Smartcar user that owns no
     vehicle, so the callback ends with "No vehicles were found in this Smartcar account."
   - The dialog's "Connect automatically" tab instead connects the vehicle to the app directly.
     That connection belongs to the simulator's own user, not to the user of a WattPilot Connect
     flow, so WattPilot does not pick it up.
   - `mode=live` requires a real vehicle account from a supported manufacturer, so it is not used
     for the shared demo.

## Demo login (optional)

Off by default everywhere (`DEMO_ENABLED` defaults to false). When on, the login page's "Try the
demo" button calls `POST /auth/demo`, which creates a temporary account for the visitor, loaded with
copies of a template account's EVs and finished charging history, and logs it in. Visitors never
share an account, so one visitor's charging schedules cannot block another's. Each account is deleted
automatically by an hourly cleanup job once it is older than `DEMO_TTL`.

### The template account

The template is an ordinary account that already exists in the database; its active EVs, and the
finished charges recorded for them, are what every visitor receives a copy of. Prepare it once:

1. Sign up a new account in the app. It must keep `demo = false` (the default): the cleanup job
   deletes only accounts with `demo = true`, so the template can never be removed by it.
2. Give it at least one ACTIVE EV, registered through the app or copied from another account (see
   below). The demo is unavailable (503 `DEMO_UNAVAILABLE`) if the template has none.
3. Lock its EVs, so a visitor cannot edit, deactivate or disconnect them:
   ```sql
   UPDATE evs SET locked = TRUE
   WHERE user_id = (SELECT id FROM users WHERE email = '<template-email>');
   ```
   Every visitor copy is created locked regardless; locking the template's own EVs protects the
   template itself.
4. Optionally connect an EV to a Smartcar simulator vehicle through the normal "Connect vehicle"
   flow (see "Smartcar vehicle telemetry" above). The connection row is copied to every visitor's
   EV, so each demo account shows live telemetry without a Connect login.
5. Give it charging history, so the history, dashboard and savings screens are not empty for a
   visitor. Either complete a few charges on the template through the app, or copy them from
   another account (see below). Only finished charges are copied to visitors: a schedule whose
   session is `COMPLETED` or `FAILED`. Cancelled, waiting and running schedules are never copied. A
   template with no history is fine; visitors then start with empty history screens.
6. Check the result:
   ```sql
   SELECT e.id, e.name, e.status, e.locked, (c.id IS NOT NULL) AS connected
   FROM evs e
   JOIN users u ON u.id = e.user_id
   LEFT JOIN vehicle_connections c ON c.ev_id = e.id
   WHERE u.email = '<template-email>';

   SELECT ss.status, count(*)
   FROM charging_sessions ss
   JOIN charging_schedules s ON s.id = ss.charging_schedule_id
   JOIN charging_plans p ON p.id = s.charging_plan_id
   JOIN users u ON u.id = p.user_id
   WHERE u.email = '<template-email>'
   GROUP BY ss.status;
   ```

To reuse an EV, with its Smartcar connection, from another account instead of connecting it again,
copy its `evs` row and its `vehicle_connections` row to the template. Only those two tables are
involved; the connection row keeps the same Smartcar ids and gets the new EV's id. Run it in a
transaction, check the result, then commit:

```sql
BEGIN;

WITH original AS (
    SELECT e.*
    FROM evs e
    JOIN users u ON u.id = e.user_id
    WHERE u.email = '<original-email>' AND e.name = '<ev-name>'
),
new_ev AS (
    INSERT INTO evs (user_id, name, manufacturer, model, battery_capacity_kwh,
                     max_ac_charging_power_kw, default_charger_power_kw, locked, created_at, updated_at)
    SELECT t.id, o.name, o.manufacturer, o.model, o.battery_capacity_kwh,
           o.max_ac_charging_power_kw, o.default_charger_power_kw, TRUE, now(), now()
    FROM original o, users t
    WHERE t.email = '<template-email>'
    RETURNING id, user_id
)
INSERT INTO vehicle_connections (user_id, ev_id, smartcar_user_id, smartcar_vehicle_id,
                                 smartcar_connection_id, vehicle_make, vehicle_model, vehicle_year,
                                 created_at, updated_at)
SELECT n.user_id, n.id, c.smartcar_user_id, c.smartcar_vehicle_id, c.smartcar_connection_id,
       c.vehicle_make, c.vehicle_model, c.vehicle_year, now(), now()
FROM new_ev n
JOIN original o ON TRUE
JOIN vehicle_connections c ON c.ev_id = o.id;

-- Expect "INSERT 0 1" (more means the name matched several EVs). Check the template with the
-- first query in step 6, then COMMIT; or ROLLBACK;
```

To reuse the finished charging history of another account, copy it onto the template's EV once that
EV exists. This is the same statement the demo login runs for each visitor, so the copy is exact: all
values and timestamps are kept, and the history screens order by those timestamps. Four tables are
involved: `charging_plans`, `charging_plan_slots`, `charging_schedules` and `charging_sessions`. The
copied slots point at the same `electricity_prices` rows as the originals. Find the four ids first
(`SELECT id FROM users WHERE email = ...`, `SELECT id, name FROM evs WHERE user_id = ...`), then in `psql`:

```sql
\set sourceUserId <original-user-id>
\set sourceEvId <original-ev-id>
\set targetUserId <template-user-id>
\set targetEvId <template-ev-id>

BEGIN;

WITH src AS (
    SELECT p.id AS old_plan_id,
           nextval(pg_get_serial_sequence('charging_plans', 'id')) AS new_plan_id,
           s.id AS old_schedule_id,
           nextval(pg_get_serial_sequence('charging_schedules', 'id')) AS new_schedule_id,
           ss.id AS old_session_id
    FROM charging_plans p
    JOIN charging_schedules s ON s.charging_plan_id = p.id
    JOIN charging_sessions ss ON ss.charging_schedule_id = s.id
    WHERE p.user_id = :sourceUserId
      AND p.ev_id = :sourceEvId
      AND ss.status IN ('COMPLETED', 'FAILED')
),
new_plans AS (
    INSERT INTO charging_plans (id, user_id, ev_id, current_battery_percent, target_battery_percent,
        price_area, earliest_start_at, required_completion_at, ev_name, ev_manufacturer, ev_model,
        battery_capacity_kwh, max_ac_charging_power_kw, default_charger_power_kw, calculated_energy_kwh,
        effective_charging_power_kw, estimated_duration_minutes, recommended_start_at,
        recommended_end_at, expected_energy_kwh, estimated_cost_nok, baseline_cost_nok,
        expected_savings_nok, status, failure_reason, created_at, updated_at)
    SELECT src.new_plan_id, :targetUserId, :targetEvId, p.current_battery_percent,
        p.target_battery_percent, p.price_area, p.earliest_start_at, p.required_completion_at,
        p.ev_name, p.ev_manufacturer, p.ev_model, p.battery_capacity_kwh, p.max_ac_charging_power_kw,
        p.default_charger_power_kw, p.calculated_energy_kwh, p.effective_charging_power_kw,
        p.estimated_duration_minutes, p.recommended_start_at, p.recommended_end_at,
        p.expected_energy_kwh, p.estimated_cost_nok, p.baseline_cost_nok, p.expected_savings_nok,
        p.status, p.failure_reason, p.created_at, p.updated_at
    FROM src
    JOIN charging_plans p ON p.id = src.old_plan_id
),
new_plan_slots AS (
    INSERT INTO charging_plan_slots (charging_plan_id, electricity_price_id, slot_start_at,
        slot_end_at, price_per_kwh, planned_energy_kwh, expected_cost_nok, sequence_no)
    SELECT src.new_plan_id, sl.electricity_price_id, sl.slot_start_at, sl.slot_end_at,
        sl.price_per_kwh, sl.planned_energy_kwh, sl.expected_cost_nok, sl.sequence_no
    FROM src
    JOIN charging_plan_slots sl ON sl.charging_plan_id = src.old_plan_id
),
new_schedules AS (
    INSERT INTO charging_schedules (id, charging_plan_id, scheduled_start_at, scheduled_end_at,
        expected_energy_kwh, estimated_cost_nok, status, retry_count, next_retry_at, created_at,
        updated_at)
    SELECT src.new_schedule_id, src.new_plan_id, s.scheduled_start_at, s.scheduled_end_at,
        s.expected_energy_kwh, s.estimated_cost_nok, s.status, s.retry_count, s.next_retry_at,
        s.created_at, s.updated_at
    FROM src
    JOIN charging_schedules s ON s.id = src.old_schedule_id
)
INSERT INTO charging_sessions (charging_schedule_id, started_at, completed_at, actual_energy_kwh,
    actual_cost_nok, baseline_cost_nok, optimized_cost_nok, estimated_savings_nok, status,
    failure_code, failure_reason, created_at, updated_at)
SELECT src.new_schedule_id, ss.started_at, ss.completed_at, ss.actual_energy_kwh, ss.actual_cost_nok,
    ss.baseline_cost_nok, ss.optimized_cost_nok, ss.estimated_savings_nok, ss.status, ss.failure_code,
    ss.failure_reason, ss.created_at, ss.updated_at
FROM src
JOIN charging_sessions ss ON ss.id = src.old_session_id;

-- Expect "INSERT 0 N", N being the number of finished charges copied. Check the template with the
-- second query in step 6, then COMMIT; or ROLLBACK;
```

Run it once per EV, and only once: running it again copies the same charges a second time. Copied
charges keep the dates they were recorded on. The dashboard's savings trend covers the last 30 days,
so once the template's newest charges are older than that, a visitor's dashboard shows no trend while
the history list still shows every charge. Complete or copy some newer charges onto the template
before that happens.

**Never disconnect or delete a vehicle that other accounts share.** The template, its visitor copies
and the account the EV was copied from all use one Smartcar connection id. Disconnecting the EV, or
deactivating it (which also removes the connection), asks Smartcar to delete that connection and
ends telemetry for every one of them. Lock the original EV too to make the API refuse it:
```sql
UPDATE evs SET locked = TRUE WHERE id = <original-ev-id>;
```

### Enabling it

Append to `/etc/wattpilot/wattpilot.env` on the VM, then `docker compose up -d` (see
`deploy/azure/.env.example`):

```
DEMO_ENABLED=true
DEMO_TEMPLATE_EMAIL=<template-email>
# Optional; defaults shown.
# DEMO_TTL=24h
# DEMO_MAX_ACTIVE_ACCOUNTS=200
# DEMO_CLEANUP_CRON=0 0 * * * *
# DEMO_CLEANUP_BATCH_SIZE=200
```

Only the template's email is configured. The application never logs in as the template, so no
password or token is needed. The backend refuses to start if `DEMO_ENABLED=true` and
`DEMO_TEMPLATE_EMAIL` is empty.

### Limits and cleanup

- At most `DEMO_MAX_ACTIVE_ACCOUNTS` demo accounts exist at once; further demo logins get
  429 `DEMO_CAPACITY_REACHED`.
- nginx limits `POST /api/v1/auth/demo` to 5 requests per minute per client IP, with a burst of 5, so
  a few quick clicks pass and a script does not (`deploy/azure/nginx/wattpilot.conf`). Over the limit
  nginx answers 429 itself, with a JSON body the frontend can show. Visitors behind one NAT share
  an IP and therefore the limit. After changing the file, install it as described in its header and
  reload: `sudo nginx -t && sudo systemctl reload nginx`.
- The cleanup runs hourly, so an account is deleted between `DEMO_TTL` and `DEMO_TTL` plus one hour
  after it was created. An account with a charge in progress is skipped until the charge ends.
  Deleting an account removes its EVs, vehicle connections and charging data through
  `ON DELETE CASCADE`; Smartcar is never contacted.

### Verifying

```
curl -i -X POST https://www.wattpilot.dev/api/v1/auth/demo
```

Expect `201` with an `accessToken` and a `wp_refresh_token` cookie. `503 DEMO_UNAVAILABLE` means the
demo is off, or the template account is missing or has no active EV. Each call creates a real demo
account that the cleanup deletes later.

# Request Limits and Security Headers

`deploy/azure/nginx/wattpilot.conf` is the first line of defence against abusive traffic; the
application adds per-account limits behind it. After changing the file, install it as described in its
header and reload: `sudo nginx -t && sudo systemctl reload nginx`.

| Control | Setting | Why |
| --- | --- | --- |
| Request body size | `client_max_body_size 32k` | A legitimate request body is a few hundred bytes; stops oversized JSON before it reaches the parser |
| `POST /api/v1/auth/login`, `/signup` | 10 requests/min per IP, burst 10 | Each attempt costs a BCrypt verification: bounds password guessing and CPU exhaustion |
| `POST /api/v1/auth/refresh` | 60 requests/min per IP, burst 30 | Runs on every page load of a signed-in visitor, so it is looser |
| Other `/api/` calls | 20 requests/s per IP, burst 40 | Coarse ceiling for write endpoints and the Smartcar telemetry fan-out |
| `POST /api/v1/auth/demo` | 5 requests/min per IP, burst 5 | See "Demo login" |

Over any limit nginx answers 429 with a JSON body. Visitors behind one NAT share an IP and therefore
the limits.

Application-level limits (see `docs/openapi.yaml`): at most 20 EVs per account (409 `EV_LIMIT_REACHED`),
at most 5 feedback messages per rolling 24 hours (429 `FEEDBACK_LIMIT_REACHED`), and at most 400 days
per savings query (400 `VALIDATION_ERROR`).

The backend container runs with a read-only root filesystem (`/tmp` is a tmpfs), no Linux capabilities and
`no-new-privileges` (see `deploy/azure/docker-compose.yml`). The VM's copy of that file is not synced by
the deploy workflow, so copy the change over by hand and run `docker compose up -d`. A restart is a short
backend outage. If a feature ever has to write files, give it a volume or tmpfs instead of removing the
restriction.

Fonts are bundled with the frontend (`@fontsource/*`), not loaded from Google Fonts, so the
Content-Security-Policy needs no external origin and no visitor IP goes to a third party.

Expired refresh tokens are deleted once a day at 03:30 Europe/Oslo by `RefreshTokenCleanupScheduler`;
tokens that were only revoked are kept until their session would have expired, because reuse detection
needs them.

Security headers are set once at the `www` server level: `Strict-Transport-Security`,
`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` and a
`Content-Security-Policy`. nginx only inherits `add_header` into a location that declares none of its
own, so do not add `add_header` inside a location. If the CSP blocks a new third-party resource (for
example another font host), the browser console shows a "Refused to ..." message; extend the matching
directive in the file. Check the live headers with `curl -sI https://www.wattpilot.dev/`.

# Monitoring & Logging

V1 uses a lightweight monitoring setup.

- **Spring Boot Actuator** (the `health` endpoint only) for application health checks:
  - `/actuator/health` - aggregate status, including the database. Proxied by nginx, so it is
    reachable from outside the VM (deploy smoke check, external uptime probe).
  - `/actuator/health/liveness` - the application process itself; does not depend on the database,
    because restarting the application does not fix a database outage.
  - `/actuator/health/readiness` - whether the application can serve requests; includes the
    database.

  The liveness and readiness paths are not proxied by nginx and the backend port is bound to
  `127.0.0.1`, so the backend answers them only on the VM itself (for example a container health
  check). Through the public domain they fall through to the SPA fallback and return the frontend's
  `index.html` with HTTP 200 whatever the backend state, so never point an external monitor at them.
- **Container health check** (`healthcheck` in `deploy/azure/docker-compose.yml`): every 30 s Docker
  runs `curl` against `/actuator/health/liveness` inside the backend container (10 s timeout, 3
  consecutive failures mark it `unhealthy`, 60 s start period). Check the state with
  `docker ps` or `docker inspect -f '{{.State.Health.Status}}' wattpilot-backend`.

  The VM's `/app/wattpilot/docker-compose.yml` is not synced by the deploy workflow (it only
  swaps the image tag), so a change to this block takes effect only after the same edit is made on
  the VM by hand, followed by `docker compose up -d` (this recreates the container, so expect a
  short backend restart). A container that shows no health status in `docker ps` is still running
  without the check.

  Docker only reports this state. `restart: unless-stopped` restarts the container when the process
  exits, but it does not restart an `unhealthy` container, so a hung application stays running
  until someone acts on the alert or restarts it by hand (`docker compose restart backend`).
- **External uptime monitoring** (UptimeRobot, free plan, 5-minute interval, e-mail alerts). It runs
  outside the VM, so it also catches a dead VM, nginx, an expired certificate, or a DNS problem,
  which the checks above cannot see:

  | Monitor | Type | URL | Healthy when |
  | --- | --- | --- | --- |
  | API health | Keyword | `https://www.wattpilot.dev/actuator/health` | body contains `"status":"UP"` |
  | Web | HTTP(s) | `https://www.wattpilot.dev/` | HTTP 200 |
  | Apex redirect (optional) | HTTP(s) | `https://wattpilot.dev/` | redirect followed, HTTP 200 |

  The API monitor matches the body instead of only the status code because nginx serves the SPA
  for every unknown path: if the `/actuator/health` proxy rule were lost, the URL would still
  answer 200 with HTML.

  When the API monitor reports Down: check `docker ps` and `docker logs wattpilot-backend` on the
  VM, then the database (`/actuator/health` also fails when the database is unreachable, and in a
  local test it did not answer within 20 s in that case).

  Verified: the alert e-mail is delivered (UptimeRobot's built-in notification test). Not
  exercised: an actual outage being detected by the monitors.
- **Docker container logs** (`docker compose logs`) and nginx access/error logs on the VM

```
Spring Boot Container
        ↓
   docker compose logs
```

Prometheus, Grafana, and a managed log aggregator are not required for V1.

# Cost Strategy

Because WattPilot is a personal portfolio project, cloud cost should be kept as low as reasonably possible.

The production environment avoids unnecessary high-cost infrastructure such as:

- Load balancers or API gateways in front of the single VM
- Kubernetes / container orchestration platforms
- Multi-AZ / multi-region high-availability architecture
- Separate staging infrastructure
- Redis clusters
- Kafka or other message brokers

See "Cost guardrails and teardown" above for the VM/database free-grant tracking. If long-term
hosting costs become too high for a portfolio project, the hosting model may be simplified further
while keeping the deployment architecture and implementation experience documented.

# Deployment Implementation Order

1. Set up the local development environment
2. Configure PostgreSQL and Flyway
3. Complete the main V1 backend and frontend features
4. Create the backend Docker image
5. Verify the backend locally with Docker
6. Provision the Azure VM and managed PostgreSQL (see "Production Architecture (Azure)" → "Procedure")
7. Perform the first backend deployment manually (`cloud` profile, no domain yet)
8. Purchase a domain and obtain a TLS certificate (Let's Encrypt/certbot)
9. Switch the backend to the `prod` profile and bind it to localhost
10. Install and configure nginx as the reverse proxy and TLS terminator
11. Build and deploy the frontend to the VM
12. Verify frontend-to-backend communication over HTTPS
13. Configure GitHub Actions CI
14. Configure GitHub Actions CD (deploy to the VM)
15. Set up a Cost Management budget alert and a free-grant expiry reminder

# V1 Deployment Goal

The final deployment goal for WattPilot V1 is an automated production deployment pipeline targeting
the Azure VM described above.

```
GitHub
   │
   │ Merge to main
   ▼
GitHub Actions
   │
   ├──────── Frontend ────────→ Build → rsync dist/ to the Azure VM (nginx)
   │
   └──────── Backend ─────────→ Build → GHCR → SSH to VM: docker compose pull && up
                                                              │
                                                              ▼
                                                PostgreSQL Flexible Server
```

After a successful merge into the `main` branch, tests, builds, and production deployment should run automatically through GitHub Actions.