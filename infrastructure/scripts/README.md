# Database Backup & Restore

Automated scripts for backing up and restoring all payment platform databases.

## Scripts

### backup.sh
Creates compressed backup archives of all 5 PostgreSQL databases.

**Usage:**
```bash
./backup.sh [backup_directory]
```

**Example:**
```bash
./backup.sh /backups
# Creates: /backups/payment-platform-backup-20260921_123456.tar.gz
```

**What it does:**
1. Connects to all 5 databases (account_db, payment_db, fraud_db, notification_db, transaction_history_db)
2. Creates SQL dumps using `pg_dump`
3. Compresses all dumps into a single tar.gz archive
4. Stores with timestamp for easy identification

**Environment variables:**
- `DB_HOST` - PostgreSQL host (default: localhost)
- `DB_PORT` - PostgreSQL port (default: 5432)
- `DB_USER` - PostgreSQL user (default: postgres)
- `DB_PASS` - PostgreSQL password (required for production)

**Example with environment variables:**
```bash
DB_HOST=prod-db.example.com \
DB_USER=pg_backup \
DB_PASS=secure_password \
./backup.sh /backups/prod
```

### restore.sh
Restores all databases from a backup archive.

**Usage:**
```bash
./restore.sh <backup_file>
```

**Example:**
```bash
./restore.sh /backups/payment-platform-backup-20260921_123456.tar.gz
```

**What it does:**
1. Extracts the backup archive
2. Drops existing databases
3. Creates fresh databases
4. Restores SQL dumps
5. Prompts for 10-second confirmation before proceeding

**Environment variables:**
Same as backup.sh (DB_HOST, DB_PORT, DB_USER, DB_PASS)

## Best Practices

### Backup Frequency
- **Production:** Every 1-6 hours (depending on data volume)
- **Staging:** Daily
- **Development:** Weekly or on-demand

### Retention Policy
- **Daily backups:** Keep 7 days
- **Weekly backups:** Keep 4 weeks
- **Monthly backups:** Keep 1 year
- **Off-site storage:** For all critical backups

### Automation (Kubernetes CronJob)
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: payment-db-backup
spec:
  schedule: "0 2 * * *"  # Run at 2 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:16-alpine
            env:
            - name: DB_HOST
              value: payment-postgres
            - name: DB_USER
              value: postgres
            - name: DB_PASS
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: password
            command:
            - /bin/sh
            - -c
            - |
              apk add --no-cache bash
              ./backup.sh /mnt/backups
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-pvc
          restartPolicy: OnFailure
```

## Recovery Procedures

### Quick Recovery (< 15 minutes)
```bash
# 1. Stop services
kubectl scale deployment payment-service --replicas=0
kubectl scale deployment account-service --replicas=0
# ... stop all services

# 2. Restore database
DB_HOST=localhost ./restore.sh /backups/latest.tar.gz

# 3. Verify integrity
kubectl run --rm -it db-check --image=postgres:16-alpine -- \
  psql -h payment-postgres -U postgres -c "SELECT version();"

# 4. Restart services
kubectl scale deployment payment-service --replicas=3
# ... restart all services

# 5. Verify health
curl http://localhost:8080/actuator/health
```

### Full Disaster Recovery
1. Restore infrastructure (VPC, load balancers, K8s cluster)
2. Deploy latest Docker images to K8s
3. Restore databases using this script
4. Restore application configuration from ConfigMaps
5. Run database migrations: `kubectl exec -it payment-service -- mvn flyway:info`
6. Verify all services are healthy

## Monitoring & Alerts

### Backup Success Metrics
- Backup completes within 5 minutes
- Backup file size > 1MB (detect empty backups)
- Backup stored in at least 2 locations

### Alert Conditions
- Backup script fails (exit code != 0)
- Last backup older than 24 hours
- Backup file corrupted (can't extract)
- Restore test fails (monthly validation)

## Testing Recovery

**Monthly Restore Test (Dry Run):**
```bash
# 1. Restore to staging database
DB_HOST=staging-db ./restore.sh /backups/latest.tar.gz

# 2. Run integrity checks
SELECT COUNT(*) FROM payments;
SELECT COUNT(*) FROM accounts;

# 3. Run smoke tests
curl http://staging:8080/api/v1/health

# 4. Document results
echo "Restore test $(date): PASSED" >> recovery_log.txt
```

## Troubleshooting

### Backup fails with "pg_dump: error"
- Check PostgreSQL is running: `psql -h $DB_HOST -U $DB_USER -c "SELECT 1"`
- Check credentials: `echo $DB_PASS` (verify password)
- Check disk space: `df -h`

### Restore fails: "database is being accessed by other users"
- Ensure all services are stopped
- Terminate active connections: `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='account_db';`

### Restore incomplete: "ERROR: duplicate key value"
- Database may be partially restored
- Drop and re-create: `DROP DATABASE IF EXISTS account_db; CREATE DATABASE account_db;`
- Re-run restore script

## Files Included

- `backup.sh` - Create backups
- `restore.sh` - Restore backups
- `README.md` - This file

## See Also

- [Kubernetes Backup Documentation](https://kubernetes.io/docs/tasks/administer-cluster/configure-upgrade-cluster/cluster-backup/)
- [PostgreSQL Backup Documentation](https://www.postgresql.org/docs/current/backup-dump.html)
