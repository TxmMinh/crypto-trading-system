-- Insert default user
INSERT INTO users (id, username, email, created_at) VALUES (1, 'trader01', 'trader01@crypto.com', CURRENT_TIMESTAMP);

-- Insert initial USDT wallet balance (50,000 USDT)
INSERT INTO wallet (id, user_id, currency, balance, updated_at)
VALUES (1, 1, 'USDT', 50000.00, CURRENT_TIMESTAMP);

-- Insert ETH wallet (starts at 0)
INSERT INTO wallet (id, user_id, currency, balance, updated_at)
VALUES (2, 1, 'ETH', 0.00, CURRENT_TIMESTAMP);

-- Insert BTC wallet (starts at 0)
INSERT INTO wallet (id, user_id, currency, balance, updated_at)
VALUES (3, 1, 'BTC', 0.00, CURRENT_TIMESTAMP);
