-- infrastructure/postgres/init.sql
--
-- NOTE: Postgres only runs this script when the data volume is empty (first
-- container start). If you add a database here after already running
-- docker-compose up, either create it by hand or recreate the volume:
--   docker-compose down -v && docker-compose up -d

CREATE DATABASE account_db;
CREATE DATABASE fraud_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
CREATE DATABASE transaction_history_db;
