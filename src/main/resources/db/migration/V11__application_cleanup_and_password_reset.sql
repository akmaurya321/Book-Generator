-- Password reset support for local accounts.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone
);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user
    ON password_reset_tokens (user_id);

-- Remove legacy subscription/payment metadata from databases upgraded from
-- versions that contained the retired billing flow.
ALTER TABLE app_users DROP COLUMN IF EXISTS plan_code;
ALTER TABLE app_users DROP COLUMN IF EXISTS plan_name;
ALTER TABLE app_users DROP COLUMN IF EXISTS plan_price;
ALTER TABLE app_users DROP COLUMN IF EXISTS billing_status;
ALTER TABLE app_users DROP COLUMN IF EXISTS plan_started_at;
ALTER TABLE app_users DROP COLUMN IF EXISTS current_period_end;
ALTER TABLE app_users DROP COLUMN IF EXISTS razorpay_customer_id;
ALTER TABLE app_users DROP COLUMN IF EXISTS last_payment_id;
