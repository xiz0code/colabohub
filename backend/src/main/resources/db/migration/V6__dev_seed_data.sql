insert into tenants (
    code,
    name,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by
)
select
    'main',
    'ColaborApp Principal',
    'ACTIVE',
    current_timestamp,
    current_timestamp,
    'system',
    'system'
where not exists (
    select 1
    from tenants
    where code = 'main'
);

insert into markets (
    tenant_id,
    name,
    email,
    city,
    currency,
    uf_enabled,
    created_at,
    updated_at,
    created_by,
    updated_by
)
select
    t.id,
    'Mercado Creativo',
    'admin@mercado.cl',
    'Santiago',
    'CLP',
    true,
    current_timestamp,
    current_timestamp,
    'system',
    'system'
from tenants t
where t.code = 'main'
  and not exists (
      select 1
      from markets m
      where m.tenant_id = t.id
        and m.name = 'Mercado Creativo'
  );

insert into stores (
    tenant_id,
    market_id,
    code,
    name,
    type,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by
)
select
    t.id,
    m.id,
    seed.code,
    seed.name,
    'COLLABORATOR',
    'ACTIVE',
    current_timestamp,
    current_timestamp,
    'system',
    'system'
from tenants t
join markets m
    on m.tenant_id = t.id
   and m.name = 'Mercado Creativo'
join (
    select 'ANA-STORE' as code, 'Tienda Ana' as name
    union all
    select 'LUCIA-STORE', 'Tienda Lucia'
    union all
    select 'MARIA-STORE', 'Tienda Maria'
) seed
    on 1 = 1
where t.code = 'main'
  and not exists (
      select 1
      from stores s
      where s.tenant_id = t.id
        and s.code = seed.code
  );

insert into products (
    tenant_id,
    store_id,
    name,
    sku,
    description,
    sale_price,
    cost,
    stock,
    status,
    barcode,
    created_at,
    updated_at,
    created_by,
    updated_by,
    version
)
select
    t.id,
    s.id,
    seed.name,
    seed.sku,
    seed.description,
    seed.sale_price,
    seed.cost,
    10,
    'ACTIVE',
    seed.barcode,
    current_timestamp,
    current_timestamp,
    'system',
    'system',
    0
from tenants t
join stores s
    on s.tenant_id = t.id
join (
    select 'ANA-STORE' as store_code, 'Aro Flor' as name, 'ANA-ARO-001' as sku, 'Aro Flor de Tienda Ana' as description, 12000.0000 as sale_price, 6000.0000 as cost, '7500000000101' as barcode
    union all
    select 'ANA-STORE', 'Pulsera Rosa', 'ANA-PUL-001', 'Pulsera Rosa de Tienda Ana', 15000.0000, 7000.0000, '7500000000102'
    union all
    select 'LUCIA-STORE', 'Collar Luna', 'LUC-COL-001', 'Collar Luna de Tienda Lucia', 18000.0000, 9000.0000, '7500000000103'
    union all
    select 'MARIA-STORE', 'Anillo Sol', 'MAR-ANI-001', 'Anillo Sol de Tienda Maria', 13000.0000, 6500.0000, '7500000000104'
    union all
    select 'MARIA-STORE', 'Aro Estrella', 'MAR-ARO-001', 'Aro Estrella de Tienda Maria', 14000.0000, 6800.0000, '7500000000105'
) seed
    on seed.store_code = s.code
where t.code = 'main'
  and not exists (
      select 1
      from products p
      where p.tenant_id = t.id
        and p.sku = seed.sku
  );
