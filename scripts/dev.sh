#!/usr/bin/env bash
set -euo pipefail

echo "🚀 Starting local development (Maven + local Postgres)..."

# Resolve project root based on script location so it works from anywhere
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

# Ensure .env exists
if [ ! -f .env ]; then
  echo "⚠️  .env file not found. Copying from .env.example..."
  cp .env.example .env
  echo "📝 Please edit .env with your actual values (at least DISCORD_BOT_TOKEN) and ensure Postgres is running, then re-run."
  exit 1
fi

# Load environment variables from .env
set -a
source ./.env
set +a

# Defaults if not provided
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-hangout_db}"
export DB_USERNAME="${DB_USERNAME:-hangout_user}"
export DB_PASSWORD="${DB_PASSWORD:-dev_password}"

echo "🔧 Profile: $SPRING_PROFILES_ACTIVE"
echo "🗄️  DB: postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME} (user: ${DB_USERNAME})"
if [ -z "${DISCORD_BOT_TOKEN:-}" ]; then
  echo "⚠️  DISCORD_BOT_TOKEN is empty or not set. The bot may fail to authenticate."
fi

# Optional connectivity check if pg_isready is available
if command -v pg_isready >/dev/null 2>&1; then
  if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" -q; then
    echo "⚠️  Postgres not reachable at ${DB_HOST}:${DB_PORT}. Ensure it's running and accessible."
  fi
fi

# Run the app with Maven (env is already exported)
./mvnw spring-boot:run

echo "✅ Local development stopped."

