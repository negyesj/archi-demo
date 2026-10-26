# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine
ENV API_UPSTREAM=http://gateway-ui:8080 \
    KEYCLOAK_PUBLIC_URL=http://localhost:8180
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /src/dist/frontend-admin/browser /usr/share/nginx/html
RUN rm -f /etc/nginx/conf.d/default.conf && chown -R nginx:nginx /usr/share/nginx/html
EXPOSE 8080
