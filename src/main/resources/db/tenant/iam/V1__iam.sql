create table app_user (
    id          uuid         primary key,
    keycloak_id varchar(64)  unique,
    username    varchar(100) not null unique,
    email       varchar(200),
    full_name   varchar(200) not null,
    type        varchar(20)  not null default 'HUMAN',
    active      boolean      not null default true,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create index ix_app_user_name on app_user (lower(full_name));

create table user_group (
    id          uuid         primary key,
    name        varchar(200) not null unique,
    description text,
    system      boolean      not null default false,
    version     bigint       not null default 0,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table org_unit (
    id         uuid         primary key,
    code       varchar(50)  not null unique,
    name       varchar(200) not null,
    parent_id  uuid references org_unit (id),
    version    bigint       not null default 0,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table group_member (
    group_id uuid not null references user_group (id) on delete cascade,
    user_id  uuid not null references app_user (id),
    primary key (group_id, user_id)
);

create table org_unit_member (
    org_unit_id uuid not null references org_unit (id) on delete cascade,
    user_id     uuid not null references app_user (id),
    primary key (org_unit_id, user_id)
);

-- A tenantnak engedélyezett szolgáltatások jogosultsági elemei (a manifestekből szinkronizálva).
create table permission (
    code        varchar(100) primary key,
    service_id  varchar(50)  not null,
    name        varchar(200) not null,
    description text,
    active      boolean      not null default true
);

create table permission_grant (
    id              uuid         primary key,
    permission_code varchar(100) not null references permission (code),
    subject_type    varchar(10)  not null check (subject_type in ('USER', 'GROUP')),
    subject_id      uuid         not null,
    created_at      timestamptz  not null default now(),
    created_by      varchar(200),
    unique (permission_code, subject_type, subject_id)
);
create index ix_grant_subject on permission_grant (subject_type, subject_id);

create table api_client (
    id                   uuid         primary key,
    client_id            varchar(100) not null unique,
    name                 varchar(200) not null,
    keycloak_client_uuid varchar(64)  not null,
    user_id              uuid         not null references app_user (id),
    active               boolean      not null default true,
    created_at           timestamptz  not null default now()
);
