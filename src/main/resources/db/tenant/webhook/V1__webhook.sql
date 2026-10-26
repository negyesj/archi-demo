create table subscription (
    id                   uuid          primary key,
    name                 varchar(200)  not null,
    url                  varchar(1000) not null,
    event_types          jsonb         not null,
    active               boolean       not null default true,
    suspended            boolean       not null default false,
    consecutive_failures int           not null default 0,
    created_by           varchar(200),
    created_at           timestamptz   not null default now(),
    updated_at           timestamptz   not null default now()
);

create table delivery (
    id              uuid          primary key,
    subscription_id uuid          not null references subscription (id) on delete cascade,
    event_id        uuid          not null,
    event_type      varchar(200)  not null,
    payload         jsonb         not null,
    status          varchar(20)   not null check (status in ('PENDING', 'SUCCEEDED', 'DEAD')),
    attempts        int           not null default 0,
    next_attempt_at timestamptz   not null default now(),
    last_status     int,
    last_error      text,
    created_at      timestamptz   not null default now(),
    delivered_at    timestamptz,
    unique (subscription_id, event_id)
);
create index ix_delivery_due on delivery (next_attempt_at) where status = 'PENDING';
create index ix_delivery_sub on delivery (subscription_id, created_at desc);
