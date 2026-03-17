alter table markets
    add column if not exists phone varchar(40);

alter table markets
    add column if not exists contact_name varchar(180);

alter table markets
    add column if not exists description varchar(500);

alter table markets
    add column if not exists active boolean not null default true;

update markets
set active = true
where active is null;

alter table users
    add column if not exists phone varchar(40);

alter table users
    add column if not exists contact_name varchar(180);

alter table users
    add column if not exists description varchar(500);
