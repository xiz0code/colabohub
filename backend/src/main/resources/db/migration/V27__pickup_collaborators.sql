alter table pickups
    add column if not exists collaborator_user_id bigint;

alter table pickups
    add column if not exists collaborator_name_snapshot varchar(180);
