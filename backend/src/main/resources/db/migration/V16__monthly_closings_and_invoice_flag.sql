alter table users
    add column if not exists factura boolean not null default false;

create table if not exists monthly_closings (
    id bigserial primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    market_id bigint not null references markets(id),
    closing_month date not null,
    sale_count bigint not null,
    total_sales_amount numeric(19,4) not null,
    total_commission_amount numeric(19,4) not null,
    total_net_amount numeric(19,4) not null,
    total_iva_amount numeric(19,4) not null,
    total_iva_to_pay_amount numeric(19,4) not null,
    closed_at timestamp with time zone not null,
    closed_by varchar(120) not null
);

create unique index if not exists ux_monthly_closings_market_month
    on monthly_closings (market_id, closing_month);

create table if not exists monthly_closing_collaborators (
    id bigserial primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    monthly_closing_id bigint not null references monthly_closings(id),
    collaborator_user_id bigint,
    collaborator_name_snapshot varchar(180) not null,
    collaborator_email_snapshot varchar(180),
    factura boolean not null,
    sale_count bigint not null,
    total_items bigint not null,
    total_sales_amount numeric(19,4) not null,
    total_commission_amount numeric(19,4) not null,
    total_net_amount numeric(19,4) not null,
    total_iva_amount numeric(19,4) not null,
    iva_to_pay_amount numeric(19,4) not null
);
