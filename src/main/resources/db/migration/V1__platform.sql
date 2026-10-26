-- Platform adatbázis (control plane). Ügyféladat nincs benne.
create table tenant (
    code              varchar(30)  primary key,
    name              varchar(200) not null,
    status            varchar(20)  not null,
    settings          jsonb        not null default '{}'::jsonb,
    admin_username    varchar(100) not null,
    admin_email       varchar(200) not null,
    admin_name        varchar(200) not null,
    admin_keycloak_id varchar(64),
    last_error        text,
    version           bigint       not null default 1,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now()
);

create table tenant_service (
    tenant_code varchar(30) not null references tenant (code) on delete cascade,
    service_id  varchar(50) not null,
    primary key (tenant_code, service_id)
);

create table catalog_service (
    id         varchar(50)  primary key,
    name       varchar(200) not null,
    version    varchar(50)  not null,
    manifest   jsonb        not null,
    updated_at timestamptz  not null default now()
);

create table admin_user (
    id          uuid         primary key,
    username    varchar(100) not null unique,
    email       varchar(200) not null,
    full_name   varchar(200) not null,
    role        varchar(30)  not null,
    active      boolean      not null default true,
    keycloak_id varchar(64),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table admin_audit (
    id          bigserial    primary key,
    event_id    uuid         not null unique,
    occurred_at timestamptz  not null,
    actor_id    varchar(100) not null,
    actor_name  varchar(200),
    action      varchar(100) not null,
    target_type varchar(50),
    target_id   varchar(200),
    details     jsonb
);
create index ix_admin_audit_time on admin_audit (occurred_at desc);
create index ix_admin_audit_actor on admin_audit (actor_id, occurred_at desc);
create index ix_admin_audit_target on admin_audit (target_type, target_id);

create table loglevel_setting (
    key        varchar(400) primary key,
    service    varchar(50)  not null,
    logger     varchar(300) not null,
    level      varchar(10)  not null,
    tenant     varchar(30),
    expires_at timestamptz,
    created_by varchar(100) not null,
    created_at timestamptz  not null default now()
);

-- Titoktár (MVP): AES-GCM titkosított értékek. Éles környezetben Secret Manager / Vault adapter váltja ki.
create table secret (
    key        varchar(300) primary key,
    nonce      bytea        not null,
    value_enc  bytea        not null,
    updated_at timestamptz  not null default now()
);
