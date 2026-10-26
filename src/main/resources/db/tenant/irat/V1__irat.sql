-- Sorfolytonos iktatószám: számláló iktatókönyvenként és évenként. A növelés az iktatás tranzakciójában
-- sorzárat tesz a számlálóra; visszagörgetéskor a növelés is visszagördül, így nem keletkezik lyuk.
create table iktato_szamlalo (
    konyv          varchar(20) not null,
    ev             int         not null,
    utolso_sorszam bigint      not null,
    primary key (konyv, ev)
);

create table kuldemeny (
    id                 uuid         primary key,
    allapot            varchar(20)  not null check (allapot in ('ERKEZTETETT', 'BONTOTT', 'IKTATOTT')),
    erkezes_datum      date         not null,
    targy              varchar(500),
    partner_id         uuid         not null,
    partner_nev        varchar(300) not null,
    partner_cim        varchar(500),
    erkeztette_id      varchar(100),
    erkeztette_nev     varchar(200),
    bontas_datum       date,
    mellekletek_szama  int          check (mellekletek_szama is null or mellekletek_szama >= 0),
    bontotta_id        varchar(100),
    bontotta_nev       varchar(200),
    iktato_konyv       varchar(20),
    iktato_ev          int,
    iktato_sorszam     bigint,
    iktatoszam         varchar(60)  unique,
    iktatas_ideje      timestamptz,
    ugyintezo_id       uuid,
    ugyintezo_nev      varchar(200),
    acl_org_units      jsonb        not null default '[]'::jsonb,
    version            bigint       not null default 0,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    unique (iktato_konyv, iktato_ev, iktato_sorszam)
);
create index ix_kuldemeny_allapot on kuldemeny (allapot, erkezes_datum desc);

create table idempotency_key (
    client          varchar(200) not null,
    key             varchar(200) not null,
    request_hash    varchar(64)  not null,
    response_status int,
    response_body   jsonb,
    created_at      timestamptz  not null default now(),
    primary key (client, key)
);
