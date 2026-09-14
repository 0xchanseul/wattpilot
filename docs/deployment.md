# Deployment Strategy

WattPilot V1 will be developed and tested locally first. AWS resources will be provisioned only after the core V1 features are sufficiently complete in order to minimize unnecessary cloud costs during development.

```
Development
React + Spring Boot + PostgreSQL
          ↓
       Local Environment
          ↓
      V1 Completion
          ↓
      AWS Deployment
          ↓
   Production Environment
```

The initial project will use only two long-lived environments:

- **Local** — development and testing
- **Production** — portfolio deployment and public access

A short-lived **Azure test environment** is also used before Production exists, to run the V1 schedulers against real data around the clock. It is described in "Temporary Test Environment (Azure)" below and is torn down once Production is ready.

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

# Temporary Test Environment (Azure)

## Purpose and scope

Before the AWS production environment exists, the V1 schedulers need to run continuously against real
inputs to be validated:

- **Price collection** — fetches Norwegian next-day prices from the public Hva koster strømmen API on
  the real `Europe/Oslo` schedule (`0 15 13-22 * * *`).
- **Mock Charging execution** — drives confirmed reservations through Mock Charging every minute.

Running these on a developer laptop is not practical, and standing up the full AWS stack early is not
cost-effective. A single small Azure VM plus a managed PostgreSQL server covers it while staying inside
Azure's 12-month free grants.

This environment is **temporary and non-authoritative**:

- It is not the production target. The Production architecture (ECS Fargate + RDS) below is unchanged.
- It uses the `cloud` Spring profile, not `prod`.
- It has no domain, no HTTPS, no frontend hosting, and no CI. Those belong to Production.
- It is deleted once Production is ready, or before the free grants expire — whichever comes first.

## Architecture

```
Local machine                         Azure (resource group: wattpilot-test)
─────────────                          ─────────────────────────────────────
React (Vite dev server)  ──HTTP──▶     VM  (Standard_B1s, Ubuntu 24.04)
                                         └─ Docker: wattpilot-backend  (profile: cloud)
docker build + push                              │ JDBC + TLS
        │                                        ▼
        ▼                              PostgreSQL Flexible Server (Standard_B1ms, 32 GiB)
GHCR (ghcr.io/<owner>/wattpilot-backend)
```

| Purpose | Choice |
| --- | --- |
| Backend host | Azure VM `Standard_B1s` (1 vCPU / 1 GiB), 12-month free |
| Database | Azure Database for PostgreSQL Flexible Server `Standard_B1ms` + 32 GiB, 12-month free |
| Image registry | GitHub Container Registry (GHCR), free |
| DB network access | Public access, firewall restricted to the VM's public IP |
| Frontend | Run locally with `npm run dev`, pointed at the VM |

Confirm the currently free-eligible VM size and the PostgreSQL free offer at provisioning time; Azure
adjusts both periodically.

## Files

| File | Role |
| --- | --- |
| `backend/Dockerfile` | Multi-stage build of the backend image (built locally, not on the VM) |
| `backend/src/main/resources/application-cloud.yml` | `cloud` profile: managed DB with TLS, schedulers on, real price API |
| `deploy/azure/docker-compose.yml` | What runs on the VM (backend container only) |
| `deploy/azure/.env.example` | Template for `deploy/azure/.env`, created on the VM and never committed |

## Procedure

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

### 7. Interact with the API

The backend runs over plain HTTP on the VM's IP. The `Authorization: Bearer` access token works
directly, but the refresh-token cookie is `SameSite=Lax; Secure`, so a browser will neither store nor
send it to a non-HTTPS, cross-site host. Consequences:

- **Swagger UI / API clients:** tunnel to the VM so the origin is `localhost`, then the full auth
  flow (including refresh) works:

  ```bash
  ssh -L 8080:localhost:8080 azureuser@$VM_IP
  # open http://localhost:8080/swagger-ui.html
  ```

- **Local React frontend:** set `frontend/.env` to `VITE_API_BASE_URL=http://<VM_IP>:8080/api/v1` and
  run `npm run dev`. Login and Bearer-authenticated calls work; silent refresh does not, so the
  session ends when the 30-minute access token expires and you log in again. Full HTTPS + domain is
  production scope.

To exercise the Mock Charging execution scheduler, create a confirmed charging schedule
(`POST /charging-schedules`) through the tunnelled Swagger UI. Price collection needs no interaction.
`backend/scripts/seed_electricity_prices.py` can seed prices directly against the managed database if a
run has not happened yet.

## Redeploying

```bash
# local
docker build -t ghcr.io/<owner>/wattpilot-backend:latest ./backend
docker push ghcr.io/<owner>/wattpilot-backend:latest

# VM
cd ~/wattpilot && docker compose pull && docker compose up -d
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

# Production Architecture

The production environment will be hosted on AWS.

```
                  User
                   │
             HTTPS Request
                   │
       ┌───────────┴───────────┐
       │                       │
   CloudFront                  ALB
       │                       │
       ▼                       ▼
      S3                  ECS Fargate
React Frontend            Spring Boot
                               │
                               ▼
                        RDS PostgreSQL
```

| Purpose | AWS Service |
| --- | --- |
| Frontend Hosting | Amazon S3 |
| CDN | Amazon CloudFront |
| Backend Hosting | Amazon ECS Fargate |
| Container Registry | Amazon ECR |
| Database | Amazon RDS for PostgreSQL |
| Backend Entry Point | Application Load Balancer |
| Logging | Amazon CloudWatch |
| DNS | Amazon Route 53 |
| HTTPS Certificate | AWS Certificate Manager |

# Frontend Deployment

The React frontend will be built into static files and deployed independently from the backend.

```
React Source
    ↓
npm run build
    ↓
dist/
    ↓
Amazon S3
    ↓
CloudFront
    ↓
User
```

Example domain structure:

```
wattpilot.example
→ Frontend

api.wattpilot.example
→ Backend API
```

# Backend Deployment

The Spring Boot backend will be packaged and deployed as a Docker image.

```
Spring Boot
    ↓
Gradle Build
    ↓
Docker Image
    ↓
Amazon ECR
    ↓
ECS Fargate
```

The backend will use container-based deployment rather than manually installing and running a JAR on a server.

# Database Deployment

The production database will use **Amazon RDS for PostgreSQL**.

```
ECS Fargate
     │
     │ JDBC
     ▼
RDS PostgreSQL
```

The database will not be publicly exposed and should only be accessible from the backend infrastructure.

Production schema changes will also be managed through Flyway.

```
Backend Deployment
       ↓
Spring Boot Startup
       ↓
Flyway Migration
       ↓
RDS Schema Update
```

This keeps local and production database schemas consistent.

# CI/CD

CI/CD will be implemented using **GitHub Actions**. The default deployment trigger will be a merge into the `main` branch.

## Backend

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
Push to ECR
      ↓
Deploy to ECS
```

## Frontend

```
Merge to main
      ↓
GitHub Actions
      ↓
npm ci
      ↓
Frontend Build
      ↓
Upload to S3
      ↓
CloudFront Cache Invalidation
```

The initial AWS deployment should be completed manually once before automating the process with CI/CD. This makes it easier to separate AWS configuration issues from pipeline configuration issues.

# Configuration & Secrets

Environment-specific configuration will use Spring Profiles.

```
local
cloud   (temporary Azure test environment; see below)
prod
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
- AWS credentials

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

Production secrets will be provided through AWS Secrets Manager or ECS-managed environment secrets.

# Monitoring & Logging

V1 will use a lightweight monitoring setup.

- **Spring Boot Actuator** for application health checks
- **Amazon CloudWatch** for application logs

```
Spring Boot Container
        ↓
Application Logs
        ↓
CloudWatch Logs
```

The `/actuator/health` endpoint may be used for health checks. Prometheus and Grafana are not required for V1.

# AWS Cost Strategy

Because WattPilot is a personal portfolio project, cloud cost should be kept as low as reasonably possible.

AWS infrastructure will not be kept running during the main development phase. Production resources will be provisioned after V1 is ready for deployment.

The initial production environment should avoid unnecessary high-cost infrastructure such as:

- NAT Gateway
- Kubernetes / EKS
- Multi-AZ high-availability architecture
- Separate staging infrastructure
- Redis clusters
- Kafka or other message brokers

If long-term hosting costs become too high for a portfolio project, the production hosting model may be simplified while keeping the deployment architecture and implementation experience documented.

# Deployment Implementation Order

1. Set up the local development environment
2. Configure PostgreSQL and Flyway
3. Complete the main V1 backend and frontend features
4. Create the backend Docker image
5. Verify the backend locally with Docker
6. Provision the AWS production infrastructure
7. Create and connect RDS PostgreSQL
8. Create an ECR repository
9. Perform the first backend deployment manually
10. Verify ECS Fargate deployment
11. Deploy the frontend to S3
12. Configure CloudFront
13. Verify frontend-to-backend communication
14. Configure domain and HTTPS
15. Configure GitHub Actions CI
16. Configure GitHub Actions CD
17. Configure CloudWatch logging and health checks

# V1 Deployment Goal

The final deployment goal for WattPilot V1 is an automated production deployment pipeline.

```
GitHub
   │
   │ Merge to main
   ▼
GitHub Actions
   │
   ├──────── Frontend ────────→ S3 → CloudFront
   │
   └──────── Backend ─────────→ ECR → ECS Fargate
                                           │
                                           ▼
                                     RDS PostgreSQL
```

After a successful merge into the `main` branch, tests, builds, and production deployment should run automatically through GitHub Actions.