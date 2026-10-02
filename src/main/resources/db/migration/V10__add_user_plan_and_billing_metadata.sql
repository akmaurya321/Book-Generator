ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS plan_code varchar(32) NOT NULL DEFAULT 'FREE';

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS plan_name varchar(64) NOT NULL DEFAULT 'Free';

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS plan_price numeric(10,2) NOT NULL DEFAULT 0.00;

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS billing_status varchar(32) NOT NULL DEFAULT 'FREE';

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS plan_started_at timestamp without time zone;

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS current_period_end timestamp without time zone;

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS razorpay_customer_id varchar(64);

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS last_payment_id varchar(64);