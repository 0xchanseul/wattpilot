#!/usr/bin/env bash
set -euo pipefail

# Run on the production VM by .github/workflows/deploy.yml over SSH.
# Installs the repository's docker-compose.yml, swaps the backend image tag in
# the runtime env file, restarts the backend container, and rolls back to the
# previous image and compose file if the health check fails.
#
# Usage: deploy.sh <image-tag> [compose-file]
#   compose-file: the repository's deploy/azure/docker-compose.yml, uploaded by the
#   workflow. Without it the compose file already on the VM is left as it is.
#
# Assumes docker-compose.yml's env_file already points at ENV_FILE (see
# deploy/azure/docker-compose.yml) and that this account can already pull
# from GHCR and run docker compose here, same as the existing manual
# redeploy procedure in docs/deployment.md.

ENV_FILE="/app/wattpilot/wattpilot.env"
COMPOSE_DIR="/app/wattpilot"
IMAGE_REPO="ghcr.io/0xchanseul/wattpilot-backend"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"
HEALTH_RETRIES=10
HEALTH_DELAY_SECONDS=3

NEW_TAG="${1:?usage: deploy.sh <image-tag> [compose-file]}"
NEW_COMPOSE_FILE="${2:-}"
NEW_IMAGE="${IMAGE_REPO}:${NEW_TAG}"
COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml"
PREVIOUS_COMPOSE_FILE="${COMPOSE_DIR}/docker-compose.yml.prev"

set_image() {
  sed -i "s#^BACKEND_IMAGE=.*#BACKEND_IMAGE=$1#" "$ENV_FILE"
}

restart_backend() {
  # --env-file is required: compose resolves ${BACKEND_IMAGE} from its own project .env, not from the
  # service's env_file, so without it the image tag written by set_image would be ignored.
  (cd "$COMPOSE_DIR" && docker compose --env-file "$ENV_FILE" pull && docker compose --env-file "$ENV_FILE" up -d)
}

# Keeps the compose file on the VM identical to the repository's, so a compose change cannot silently
# stay undeployed. The copy is validated before it replaces the live file.
install_compose() {
  [ -n "$NEW_COMPOSE_FILE" ] || return 0
  cp "$COMPOSE_FILE" "$PREVIOUS_COMPOSE_FILE"
  cp "$NEW_COMPOSE_FILE" "$COMPOSE_FILE"
  if ! (cd "$COMPOSE_DIR" && docker compose --env-file "$ENV_FILE" config -q); then
    echo "The new docker-compose.yml is invalid, keeping the existing one."
    cp "$PREVIOUS_COMPOSE_FILE" "$COMPOSE_FILE"
    exit 1
  fi
}

restore_compose() {
  [ -n "$NEW_COMPOSE_FILE" ] || return 0
  cp "$PREVIOUS_COMPOSE_FILE" "$COMPOSE_FILE"
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

install_compose
set_image "$NEW_IMAGE"
restart_backend

if health_check; then
  echo "Health check passed: ${NEW_IMAGE} is live."
  exit 0
fi

echo "Health check failed for ${NEW_IMAGE}, rolling back to ${PREVIOUS_IMAGE}"
set_image "$PREVIOUS_IMAGE"
restore_compose
restart_backend

if health_check; then
  echo "Rollback to ${PREVIOUS_IMAGE} succeeded, but the deploy of ${NEW_IMAGE} failed."
else
  echo "Rollback to ${PREVIOUS_IMAGE} also failed its health check. Manual intervention required."
fi
exit 1
