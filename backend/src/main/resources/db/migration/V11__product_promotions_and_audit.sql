alter table markets
    add column if not exists global_promotion_enabled boolean not null default false;

alter table markets
    add column if not exists global_promotion_percentage numeric(5,2);

alter table product_promotions
    add column if not exists percentage_discount numeric(5,2);

create table if not exists product_audit_logs (
    id bigserial primary key,
    created_at timestamp not null,
    updated_at timestamp not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    tenant_id bigint not null,
    product_id bigint not null,
    field_name varchar(80) not null,
    previous_value varchar(1024),
    new_value varchar(1024),
    constraint fk_product_audit_logs_tenant foreign key (tenant_id) references tenants (id),
    constraint fk_product_audit_logs_product foreign key (product_id) references products (id)
);

create index if not exists idx_product_audit_logs_product on product_audit_logs (product_id, created_at desc);
