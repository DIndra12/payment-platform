#!/bin/bash

# Database Restore Script
# Restores all PostgreSQL databases from a backup archive
# Usage: ./restore.sh <backup_file>
# Example: ./restore.sh payment-platform-backup-20260921_123456.tar.gz

set -e  # Exit on error

if [ -z "$1" ]; then
    echo "Usage: $0 <backup_file>"
    echo "Example: $0 payment-platform-backup-20260921_123456.tar.gz"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "✗ Error: Backup file not found: $BACKUP_FILE"
    exit 1
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-postgres}"
DATABASES=("account_db" "payment_db" "fraud_db" "notification_db" "transaction_history_db")

echo "=== Payment Platform Database Restore ==="
echo "Backup file: $BACKUP_FILE"
echo "Database host: $DB_HOST"
echo "Databases: ${DATABASES[@]}"
echo ""
echo "WARNING: This will REPLACE all data in the databases above."
echo "Press Ctrl+C to cancel, or wait 10 seconds to continue..."
sleep 10

# Temporary directory for extraction
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo "Extracting backup..."
tar -xzf "$BACKUP_FILE" -C "$TEMP_DIR"

echo "Restoring databases..."
for db in "${DATABASES[@]}"; do
    if [ -f "$TEMP_DIR/$db.sql" ]; then
        echo "  - Dropping $db (if exists)..."
        PGPASSWORD="$DB_PASS" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            --no-password \
            -c "DROP DATABASE IF EXISTS $db;" 2>/dev/null || true

        echo "  - Creating $db..."
        PGPASSWORD="$DB_PASS" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            --no-password \
            -c "CREATE DATABASE $db;"

        echo "  - Restoring $db..."
        PGPASSWORD="$DB_PASS" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            -d "$db" \
            --no-password \
            < "$TEMP_DIR/$db.sql"
    else
        echo "  ✗ Warning: $db.sql not found in backup"
    fi
done

echo ""
echo "✓ Restore complete!"
echo "Database integrity check: Run migrations with 'mvn flyway:info'"
echo ""
echo "Next: Start all services and verify:"
echo "  curl http://localhost:8080/actuator/health"
