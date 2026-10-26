-- Transactional outbox: minden szolgáltatás ide írja az eseményeit az üzleti adattal egy tranzakcióban.
create table if not exists outbox_event (
    seq          bigserial    not null unique,
    id           uuid         primary key,
    topic        varchar(200) not null,
    msg_key      varchar(300) not null,
    payload      jsonb        not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     int          not null default 0,
    last_error   text
);

create index if not exists ix_outbox_unpublished on outbox_event (seq) where published_at is null;
