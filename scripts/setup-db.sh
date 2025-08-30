#!/usr/bin/env bash
set -euo pipefail

echo "🛠️  Setting up local PostgreSQL database..."

# Resolve project root and enter it
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

if [ ! -f .env ]; then
  echo "⚠️  .env not found. Copying from .env.example..."
  cp .env.example .env
  echo "📝 Edit .env with desired DB values, then re-run this script."
  exit 1
fi

set -a
source ./.env
set +a

DB_NAME="${DB_NAME:-hangout_db}"
DB_USER="${DB_USERNAME:-hangout_user}"
DB_PASS="${DB_PASSWORD:-dev_password}"

# Connection for admin user (can create roles/db). Override via env if needed.
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"

if ! command -v psql >/dev/null 2>&1; then
  echo "❌ psql not found. Install PostgreSQL client tools and try again."
  exit 1
fi

echo "🔌 Connecting as ${PGUSER}@${PGHOST}:${PGPORT}"

# Create role if missing
ROLE_EXISTS=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" || true)
if [ "$ROLE_EXISTS" != "1" ]; then
  echo "👤 Creating role '${DB_USER}'..."
  psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -v ON_ERROR_STOP=1 -c "CREATE USER \"${DB_USER}\" WITH PASSWORD '${DB_PASS}';"
else
  echo "👤 Role '${DB_USER}' already exists."
fi

# Create database if missing
DB_EXISTS=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" || true)
if [ "$DB_EXISTS" != "1" ]; then
  echo "🗄️  Creating database '${DB_NAME}'..."
  psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"${DB_NAME}\" OWNER \"${DB_USER}\";"
else
  echo "🗄️  Database '${DB_NAME}' already exists."
fi

echo "🎉 Local PostgreSQL setup complete."

