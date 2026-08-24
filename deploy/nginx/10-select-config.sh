#!/bin/sh
set -eu

certificate_path="/etc/letsencrypt/live/${API_DOMAIN}/fullchain.pem"

if [ -f "$certificate_path" ]; then
  cp /etc/nginx/meetple-templates/https.conf.template /etc/nginx/templates/default.conf.template
else
  cp /etc/nginx/meetple-templates/http.conf.template /etc/nginx/templates/default.conf.template
fi
