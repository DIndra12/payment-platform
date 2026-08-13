CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          owner_name VARCHAR(100) NOT NULL,
                          balance NUMERIC(19,4) NOT NULL,
                          version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,
                                account_id UUID NOT NULL,
                                amount NUMERIC(19,4) NOT NULL,
                                entry_type VARCHAR(10) NOT NULL,
                                reference_id UUID NOT NULL,
                                created_at TIMESTAMP NOT NULL,
                                CONSTRAINT uk_reference_entry_type UNIQUE(reference_id, entry_type)
);

-- Seed test accounts
INSERT INTO accounts (id, owner_name, balance, version)
VALUES ('11111111-1111-1111-1111-111111111111', 'John Doe', 10000.00, 0);

INSERT INTO accounts (id, owner_name, balance, version)
VALUES ('22222222-2222-2222-2222-222222222222', 'Jane Smith', 5000.00, 0);