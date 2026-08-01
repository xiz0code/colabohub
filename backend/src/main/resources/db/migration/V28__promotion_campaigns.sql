CREATE TABLE IF NOT EXISTS promotion_campaigns (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    store_id BIGINT NOT NULL REFERENCES stores(id),
    owner_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(180) NOT NULL,
    type VARCHAR(40) NOT NULL,
    block_quantity INTEGER,
    block_price NUMERIC(19,4),
    percentage_discount NUMERIC(5,2),
    minimum_purchase_amount NUMERIC(19,4),
    applies_to_cash BOOLEAN NOT NULL DEFAULT FALSE,
    applies_to_debit BOOLEAN NOT NULL DEFAULT FALSE,
    applies_to_credit BOOLEAN NOT NULL DEFAULT FALSE,
    applies_to_transfer BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by VARCHAR(120) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'system',
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_promotion_campaigns_tenant_owner
    ON promotion_campaigns(tenant_id, owner_user_id, active);

ALTER TABLE product_promotions
    ADD COLUMN IF NOT EXISTS promotion_campaign_id BIGINT REFERENCES promotion_campaigns(id);

ALTER TABLE product_promotions
    ADD COLUMN IF NOT EXISTS applies_to_credit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE product_promotions
    ADD COLUMN IF NOT EXISTS applies_to_transfer BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE product_promotions
    ADD COLUMN IF NOT EXISTS minimum_purchase_amount NUMERIC(19,4);

CREATE INDEX IF NOT EXISTS idx_product_promotions_campaign
    ON product_promotions(promotion_campaign_id);

UPDATE product_promotions
SET active = FALSE,
    updated_at = now(),
    updated_by = 'system'
WHERE active = TRUE
  AND promotion_campaign_id IS NULL;
