alter table sale_items
add column if not exists commission1_amount numeric(19,4);

alter table sale_items
add column if not exists commission2_amount numeric(19,4);

alter table sale_items
add column if not exists commission_iva_amount numeric(19,4);

alter table sale_store_summaries
add column if not exists commission1_amount numeric(19,4);

alter table sale_store_summaries
add column if not exists commission2_amount numeric(19,4);

alter table sale_store_summaries
add column if not exists commission_iva_amount numeric(19,4);
