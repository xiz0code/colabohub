alter table product_promotions
    add column if not exists applies_to_cash boolean not null default false;

alter table product_promotions
    add column if not exists applies_to_debit boolean not null default false;
