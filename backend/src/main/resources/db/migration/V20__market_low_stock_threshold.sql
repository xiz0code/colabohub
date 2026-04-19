alter table markets
    add column if not exists low_stock_alert_threshold integer not null default 2;
