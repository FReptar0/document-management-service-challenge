#!/usr/bin/env bash
# One-command bootstrap for the Document Management Service stack.
#
#   ./run.sh             # build + start postgres, minio, the service; wait until /actuator/health is UP
#   ./run.sh down        # stop and remove the stack
#   ./run.sh logs        # tail logs
#   ./run.sh smoke       # POST a small PDF + GET its presigned URL to prove the pipeline
#
# Prereqs: Docker engine + compose plugin v2.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${REPO_ROOT}/docker/docker-compose.yml"
ENV_FILE="${REPO_ROOT}/.env"
ENV_EXAMPLE="${REPO_ROOT}/.env.example"

cd "${REPO_ROOT}"

ensure_env() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    if [[ -f "${ENV_EXAMPLE}" ]]; then
      cp "${ENV_EXAMPLE}" "${ENV_FILE}"
      echo "[run.sh] Created .env from .env.example — defaults are fine for local dev."
    else
      echo "[run.sh] WARNING: no .env or .env.example found; compose may fail to interpolate variables." >&2
    fi
  fi
}

up() {
  ensure_env
  echo "[run.sh] Building and starting the stack…"
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build

  echo "[run.sh] Waiting for /actuator/health to report UP…"
  local port
  port="$(grep -E '^SERVER_PORT=' "${ENV_FILE}" 2>/dev/null | cut -d= -f2 || true)"
  port="${port:-8080}"
  local url="http://localhost:${port}/actuator/health"
  local deadline=$(( $(date +%s) + 90 ))
  while true; do
    if curl -fsS "${url}" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "[run.sh] Service is UP at ${url}."
      echo "[run.sh] Swagger UI:    http://localhost:${port}/swagger-ui.html"
      echo "[run.sh] MinIO console: http://localhost:9001 (creds in .env)"
      return 0
    fi
    if (( $(date +%s) >= deadline )); then
      echo "[run.sh] Health check timed out; recent logs:" >&2
      docker compose -f "${COMPOSE_FILE}" logs --tail=80 document-management-service >&2 || true
      return 1
    fi
    sleep 2
  done
}

down() {
  docker compose -f "${COMPOSE_FILE}" down -v --remove-orphans
}

logs() {
  docker compose -f "${COMPOSE_FILE}" logs -f --tail=100 "$@"
}

smoke() {
  local port pdf body resp id url blob_status
  port="$(grep -E '^SERVER_PORT=' "${ENV_FILE}" 2>/dev/null | cut -d= -f2 || true)"
  port="${port:-8080}"

  pdf="$(mktemp -t dms-smoke.XXXXXX.pdf)"
  printf '%%PDF-1.4\n%%smoke test payload\n%%%%EOF\n' >"${pdf}"

  echo "[smoke] uploading…"
  resp="$(curl -fsS -X POST "http://localhost:${port}/document-management/upload" \
    -F "metadata={\"user\":\"smoke\",\"name\":\"smoke.pdf\",\"tags\":[\"smoke\"]};type=application/json" \
    -F "file=@${pdf};type=application/pdf")"
  echo "[smoke] upload response: ${resp}"
  id="$(printf '%s' "${resp}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')"
  if [[ -z "${id}" ]]; then
    echo "[smoke] FAIL: no id in upload response" >&2
    rm -f "${pdf}"
    return 1
  fi

  echo "[smoke] requesting presigned URL for ${id}…"
  url="$(curl -fsS "http://localhost:${port}/document-management/download/${id}" \
    | sed -n 's/.*"url":"\([^"]*\)".*/\1/p')"
  echo "[smoke] presigned URL: ${url}"

  blob_status="$(curl -s -o /dev/null -w '%{http_code}' "${url}")"
  rm -f "${pdf}"
  if [[ "${blob_status}" != "200" ]]; then
    echo "[smoke] FAIL: presigned URL returned HTTP ${blob_status}" >&2
    return 1
  fi
  echo "[smoke] OK — round-trip succeeded."
}

case "${1:-up}" in
  up) up ;;
  down) down ;;
  logs) shift; logs "$@" ;;
  smoke) smoke ;;
  *) echo "Usage: $0 {up|down|logs|smoke}" >&2; exit 2 ;;
esac
