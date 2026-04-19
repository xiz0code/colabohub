CREATE TABLE product_promotion_groups (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    store_id BIGINT NOT NULL REFERENCES stores(id),
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by VARCHAR(120) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'system',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_product_promotion_groups_store_name UNIQUE (store_id, name)
);

ALTER TABLE products
    ADD COLUMN promotion_group_id BIGINT REFERENCES product_promotion_groups(id);

CREATE INDEX idx_products_promotion_group_id ON products(promotion_group_id);
CREATE INDEX idx_product_promotion_groups_tenant_store ON product_promotion_groups(tenant_id, store_id);
