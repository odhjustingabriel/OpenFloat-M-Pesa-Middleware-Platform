#!/usr/bin/env bash
# ==============================================================================
# OpenFloat M-Pesa Middleware — Automated PostgreSQL & pgBackRest Backup Utility
# Performs full/incremental database backup, verifies WAL archiving, and logs
# backup metrics.
# ==============================================================================

set -euo pipefail

BACKUP_TYPE="${1:-incr}" # full or incr
STANZA_NAME="openfloat_db"
BACKUP_DIR="${BACKUP_DIR:-/var/lib/pgbackrest}"

echo "==> Starting pgBackRest ${BACKUP_TYPE} backup for stanza '${STANZA_NAME}' at $(date -u)..."

# Run pgBackRest backup
if command -v pgbackrest &> /dev/null; then
  pgbackrest --stanza="${STANZA_NAME}" --type="${BACKUP_TYPE}" backup
  echo "==> Verifying backup integrity..."
  pgbackrest --stanza="${STANZA_NAME}" check
else
  echo "==> [FALLBACK] pgbackrest CLI not installed, running pg_dumpall fallback..."
  mkdir -p "${BACKUP_DIR}/dump"
  DUMP_FILE="${BACKUP_DIR}/dump/openfloat_backup_$(date +%Y%m%d_%H%M%S).sql.gz"
  pg_dumpall -U "${PGUSER:-openfloat}" -h "${PGHOST:-localhost}" | gzip > "${DUMP_FILE}"
  echo "==> Backup written to ${DUMP_FILE} ($(du -h "${DUMP_FILE}" | cut -f1))"
fi

echo "✅ Backup process completed successfully at $(date -u)!"
