FROM nginx:1.27-alpine

COPY templates /etc/nginx/meetple-templates
COPY 10-select-config.sh /docker-entrypoint.d/10-select-config.sh

RUN mkdir -p /etc/nginx/templates \
    && chmod +x /docker-entrypoint.d/10-select-config.sh
