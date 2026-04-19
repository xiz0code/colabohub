delete from daily_closing_collaborators
where daily_closing_id in (
    select dc.id
    from daily_closings dc
    join markets m on m.id = dc.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from daily_closing_stores
where daily_closing_id in (
    select dc.id
    from daily_closings dc
    join markets m on m.id = dc.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from monthly_closing_collaborators
where monthly_closing_id in (
    select mc.id
    from monthly_closings mc
    join markets m on m.id = mc.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from product_audit_logs
where product_id in (
    select p.id
    from products p
    join stores s on s.id = p.store_id
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from product_promotions
where product_id in (
    select p.id
    from products p
    join stores s on s.id = p.store_id
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or tenant_id in (
    select m.tenant_id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from stock_movements
where product_id in (
    select p.id
    from products p
    join stores s on s.id = p.store_id
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from sale_store_summaries
where sale_id in (
    select sa.id
    from sales sa
    join markets m on m.id = sa.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from sale_items
where sale_id in (
    select sa.id
    from sales sa
    join markets m on m.id = sa.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or product_id in (
    select p.id
    from products p
    join stores s on s.id = p.store_id
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from user_store_access
where store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from user_market_access
where market_id in (
    select m.id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from commission_rules
where market_id in (
    select m.id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
)
or store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from products
where store_id in (
    select s.id
    from stores s
    join markets m on m.id = s.market_id
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from sales
where market_id in (
    select m.id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from monthly_closings
where market_id in (
    select m.id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from daily_closings
where market_id in (
    select m.id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from stores
where market_id in (
    select m.id
    from markets m
    where m.name = 'Mercado Creativo'
      and m.email = 'admin@mercado.cl'
);

delete from markets
where name = 'Mercado Creativo'
  and email = 'admin@mercado.cl';
