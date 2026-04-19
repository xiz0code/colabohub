ALTER TABLE product_promotion_groups
    DROP CONSTRAINT IF EXISTS product_promotion_groups_owner_user_id_fkey;

ALTER TABLE product_promotion_groups
    ADD CONSTRAINT product_promotion_groups_owner_user_id_fkey
    FOREIGN KEY (owner_user_id) REFERENCES users(id);
