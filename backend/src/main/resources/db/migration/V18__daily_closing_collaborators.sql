create table if not exists daily_closing_collaborators (
    id bigserial primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    created_by varchar(120) not null,
    updated_by varchar(120) not null,
    daily_closing_id bigint not null references daily_closings(id),
    collaborator_user_id bigint,
    collaborator_name_snapshot varchar(180) not null,
    collaborator_email_snapshot varchar(180),
    sale_count bigint not null,
    total_items bigint not null,
    total_sales_amount numeric(19,4) not null,
    total_commission_amount numeric(19,4) not null,
    total_net_amount numeric(19,4) not null
);

create index if not exists idx_daily_closing_collaborators_closing
    on daily_closing_collaborators (daily_closing_id);
