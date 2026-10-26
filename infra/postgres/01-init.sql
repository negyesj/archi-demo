-- Fejlesztői / on-prem Compose környezet: platform és Keycloak adatbázis.
-- A tenant adatbázisokat (t_<kod>) a provisioner hozza létre futás közben.
create role platform_app with login password 'platform_app';
create database platform owner platform_app;
create role keycloak with login password 'keycloak';
create database keycloak owner keycloak;
