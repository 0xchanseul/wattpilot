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

The initial project will use only two environments:

- **Local** — development and testing
- **Production** — portfolio deployment and public access

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

Local development will use environment variables or `.env` files. Production secrets will be provided through AWS Secrets Manager or ECS-managed environment secrets.

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