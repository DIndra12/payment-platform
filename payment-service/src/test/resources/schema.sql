CREATE TABLE IF NOT EXISTS outbox_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  aggregate_id VARCHAR(255),
  aggregate_type VARCHAR(255),
  created_at TIMESTAMP,
  event_type VARCHAR(255),
  payload CLOB,
  published BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  amount NUMERIC(19,2),
  status VARCHAR(50)
);
