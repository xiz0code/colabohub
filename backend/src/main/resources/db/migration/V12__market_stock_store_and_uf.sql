alter table markets
    add column if not exists uf_value numeric(19,4);

alter table markets
    add column if not exists uf_updated_at timestamp;
