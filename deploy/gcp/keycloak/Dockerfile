# Keycloak a Cloud Run-hoz: optimalizált (előre buildelt) produktív mód, PostgreSQL-lel, a platform realm beépítve.
# A realm fájl az admin-ui visszatérési címét és a platform admin jelszavát ${ADMIN_UI_REDIRECT} és
# ${PLATFORM_ADMIN_PASSWORD} helyőrzőként tartalmazza (a 03-build.sh cseréli be); a Keycloak import
# a környezeti változókból oldja fel őket.
FROM quay.io/keycloak/keycloak:26.4 AS builder
ENV KC_DB=postgres KC_HEALTH_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.4
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --chown=1000:0 platform-realm.json /opt/keycloak/data/import/platform-realm.json
EXPOSE 8080
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
