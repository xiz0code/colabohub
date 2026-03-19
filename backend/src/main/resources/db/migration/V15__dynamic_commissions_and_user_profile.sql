alter table users add column if not exists monthly_rent numeric(19,2);
alter table users add column if not exists start_date date;
alter table users add column if not exists stand_number varchar(80);

insert into app_settings (tenant_id, setting_key, setting_value, value_type, created_at, created_by, updated_at, updated_by)
select t.id, 'useDynamicFixedCommission', 'true', 'BOOLEAN', current_timestamp, 'flyway', current_timestamp, 'flyway'
from tenants t
where not exists (
    select 1
    from app_settings s
    where s.tenant_id = t.id
      and s.setting_key = 'useDynamicFixedCommission'
);
