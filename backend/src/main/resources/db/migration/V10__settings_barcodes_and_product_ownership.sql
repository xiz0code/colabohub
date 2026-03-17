alter table products
    add column if not exists owner_user_id bigint;

alter table products
    add constraint fk_products_owner_user
        foreign key (owner_user_id) references users (id);

alter table sale_items
    add column if not exists collaborator_user_id bigint;

alter table sale_items
    add column if not exists collaborator_name_snapshot varchar(180);

alter table sales
    add column if not exists commission_uf_value numeric(19,4);

alter table sales
    add column if not exists commission_percentage_value numeric(19,4);

alter table commission_rules
    add column if not exists market_id bigint;

alter table commission_rules
    add constraint fk_commission_rules_market
        foreign key (market_id) references markets (id);

create index if not exists idx_products_owner_user on products (owner_user_id);
create index if not exists idx_sale_items_collaborator_user on sale_items (collaborator_user_id);
create index if not exists idx_commission_rules_market on commission_rules (market_id);
