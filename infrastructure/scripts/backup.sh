#!/bin/bash

# Database Backup Script
# Backs up all PostgreSQL databases to timestamped archives
# Usage: ./backup.sh [backup_dir]

set -e  # Exit on error

BACKUP_DIR="${1:-.}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="payment-platform-backup-$TIMESTAMP.tar.gz"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
DATABASES=("account_db" "payment_db" "fraud_db" "notification_db" "transaction_history_db")

echo "=== Payment Platform Database Backup ==="
echo "Time: $(date)"
echo "Backup directory: $BACKUP_DIR"
echo "Databases: ${DATABASES[@]}"
echo ""

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

# Temporary directory for dumps
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo "Dumping databases..."
for db in "${DATABASES[@]}"; do
    echo "  - $db..."
    PGPASSWORD="$DB_PASS" pg_dump \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$db" \
        --no-password \
        > "$TEMP_DIR/$db.sql"
done

echo "Compressing backup..."
tar -czf "$BACKUP_DIR/$BACKUP_FILE" -C "$TEMP_DIR" .

echo "✓ Backup complete: $BACKUP_DIR/$BACKUP_FILE"
echo "  Size: $(du -h "$BACKUP_DIR/$BACKUP_FILE" | cut -f1)"
echo ""
echo "RTO: 15 minutes (time to restore)"
echo "RPO: 1 hour (max data loss if backup fails)"
