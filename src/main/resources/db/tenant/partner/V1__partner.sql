create table partner (
    id                  uuid         primary key,
    name                varchar(300) not null,
    tax_number          varchar(30),
    registration_number varchar(30),
    country             varchar(2)   not null default 'HU',
    postal_code         varchar(20),
    city                varchar(100),
    address             varchar(300),
    email               varchar(200),
    phone               varchar(50),
    active              boolean      not null default true,
    version             bigint       not null default 0,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now()
);

-- Autocomplete: részszavas keresés névre (pg_trgm a provisioner által létrehozott kiterjesztés, public sémában).
create index ix_partner_name_trgm on partner using gin (lower(name) public.gin_trgm_ops);
create index ix_partner_tax on partner (tax_number);

create table idempotency_key (
    client          varchar(200) not null,
    key             varchar(200) not null,
    request_hash    varchar(64)  not null,
    response_status int,
    response_body   jsonb,
    created_at      timestamptz  not null default now(),
    primary key (client, key)
);
