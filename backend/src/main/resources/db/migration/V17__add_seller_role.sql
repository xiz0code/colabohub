insert into roles (code, name, created_at, updated_at, created_by, updated_by)
select 'SELLER', 'Vendedor', current_timestamp, current_timestamp, 'system', 'system'
where not exists (select 1 from roles where code = 'SELLER');
