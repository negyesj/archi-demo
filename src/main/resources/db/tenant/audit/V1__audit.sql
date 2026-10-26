-- Audit napló: csak beszúrás. Az alkalmazás felhasználója nem módosíthat és nem törölhet.
create table audit_event (
    id          bigserial    primary key,
    event_id    uuid         not null unique,
    occurred_at timestamptz  not null,
    service     varchar(50)  not null,
    entity      varchar(100) not null,
    entity_id   varchar(200) not null,
    action      varchar(50)  not null,
    version     bigint,
    actor_id    varchar(100),
    actor_name  varchar(200),
    actor_type  varchar(20),
    trace_id    varchar(64),
    before      jsonb,
    after       jsonb,
    changes     jsonb,
    recorded_at timestamptz  not null default now()
);

create index ix_audit_time on audit_event (occurred_at desc);
create index ix_audit_entity on audit_event (entity, entity_id, occurred_at desc);
create index ix_audit_actor on audit_event (actor_id, occurred_at desc);
create index ix_audit_service on audit_event (service, occurred_at desc);
create index ix_audit_changes on audit_event using gin (changes jsonb_path_ops);

revoke update, delete, truncate on audit_event from current_user;
