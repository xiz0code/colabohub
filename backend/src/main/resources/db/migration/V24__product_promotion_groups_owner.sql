ALTER TABLE product_promotion_groups
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES app_users(id);

DROP INDEX IF EXISTS idx_product_promotion_groups_tenant_store;

ALTER TABLE product_promotion_groups
    DROP CONSTRAINT IF EXISTS uk_product_promotion_groups_store_name;

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_promotion_groups_owner_name
    ON product_promotion_groups(store_id, owner_user_id, name);

CREATE INDEX IF NOT EXISTS idx_product_promotion_groups_tenant_store_owner
    ON product_promotion_groups(tenant_id, store_id, owner_user_id);
