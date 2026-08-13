CREATE TABLE payments (
                          id UUID PRIMARY KEY,
                          payer_account_id UUID NOT NULL,
                          payee_account_id UUID NOT NULL,
                          amount DECIMAL(19, 2) NOT NULL,
                          currency VARCHAR(3) NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          failure_reason VARCHAR(255),
                          created_at TIMESTAMP NOT NULL,
                          updated_at TIMESTAMP NOT NULL
);