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
                 Azure VM (resource group: wattpilot-test)
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
| DB network access | Public access, firewall restricted to the VM's public IP |
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
| `deploy/azure/.env.example` | Template for the real runtime env file, which lives at `/etc/wattpilot/wattpilot.env` on the VM (not `.env` next to the compose file) and is never committed |
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
RG=wattpilot-test
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
- Full teardown removes every resource:

  ```bash
  az group delete -n wattpilot-test --yes --no-wait
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

The database is not publicly exposed — its firewall allows only the VM's public IP.

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

# CI/CD

CI/CD is not yet implemented; deployments are currently manual (see "Production Architecture
(Azure)" → "Procedure" and "Redeploying" above). GitHub Actions is expected to automate this later,
targeting the same VM rather than AWS services:

## Backend (planned)

```
Merge to main
      ↓
GitHub Actions
      ↓
Backend Tests
      ↓
Gradle Build
      ↓
Docker Build
      ↓
Push to GHCR
      ↓
SSH to VM: docker compose pull && up
```

## Frontend (planned)

```
Merge to main
      ↓
GitHub Actions
      ↓
npm ci
      ↓
Frontend Build
      ↓
rsync dist/ to the VM
```

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

Production secrets live in `deploy/azure/.env` on the VM (git-ignored, never committed) — see
"Production Architecture (Azure)" → "Files" above.

# Monitoring & Logging

V1 uses a lightweight monitoring setup.

- **Spring Boot Actuator** (`/actuator/health` only) for application health checks
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