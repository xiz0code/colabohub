alter table products
    add column if not exists short_barcode varchar(7);

update products
set short_barcode = lpad(id::text, 7, '0')
where short_barcode is null;

alter table products
    alter column short_barcode set not null;

alter table products
    alter column barcode drop not null;

alter table products
    drop constraint if exists uk_products_short_barcode;

alter table products
    add constraint uk_products_short_barcode unique (short_barcode);

create sequence if not exists product_short_barcode_seq start with 1 increment by 1;

select setval(
    'product_short_barcode_seq',
    coalesce((select max(short_barcode::bigint) from products), 1),
    coalesce((select max(short_barcode::bigint) from products), 0) > 0
);
