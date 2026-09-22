#!/usr/bin/env bash
set -euo pipefail

# Run on the production VM by .github/workflows/deploy.yml over SSH.
# Swaps the backend image tag in the runtime env file, restarts the backend
# container, and rolls back to the previous image if the health check fails.
#
# Usage: deploy.sh <image-tag>
#
# Assumes docker-compose.yml's env_file already points at ENV_FILE (see
# deploy/azure/docker-compose.yml) and that this account can already pull
# from GHCR and run docker compose here, same as the existing manual
# redeploy procedure in docs/deployment.md.

ENV_FILE="/etc/wattpilot/wattpilot.env"
COMPOSE_DIR="/app/wattpilot"
IMAGE_REPO="ghcr.io/0xchanseul/wattpilot-backend"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"
HEALTH_RETRIES=10
HEALTH_DELAY_SECONDS=3

NEW_TAG="${1:?usage: deploy.sh <image-tag>}"
NEW_IMAGE="${IMAGE_REPO}:${NEW_TAG}"

set_image() {
  sed -i "s#^BACKEND_IMAGE=.*#BACKEND_IMAGE=$1#" "$ENV_FILE"
}

restart_backend() {
  (cd "$COMPOSE_DIR" && docker compose pull && docker compose up -d)
}

health_check() {
  local attempt
  for attempt in $(seq 1 "$HEALTH_RETRIES"); do
    if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$HEALTH_DELAY_SECONDS"
  done
  return 1
}

PREVIOUS_IMAGE="$(grep '^BACKEND_IMAGE=' "$ENV_FILE" | cut -d= -f2-)"
echo "Deploying ${NEW_IMAGE} (previous: ${PREVIOUS_IMAGE})"

set_image "$NEW_IMAGE"
restart_backend

if health_check; then
  echo "Health check passed: ${NEW_IMAGE} is live."
  exit 0
fi

echo "Health check failed for ${NEW_IMAGE}, rolling back to ${PREVIOUS_IMAGE}"
set_image "$PREVIOUS_IMAGE"
restart_backend

if health_check; then
  echo "Rollback to ${PREVIOUS_IMAGE} succeeded, but the deploy of ${NEW_IMAGE} failed."
else
  echo "Rollback to ${PREVIOUS_IMAGE} also failed its health check. Manual intervention required."
fi
exit 1
