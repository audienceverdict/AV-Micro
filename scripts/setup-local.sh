#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p secrets/social-media
if [[ ! -f secrets/identity-private.pem ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/identity-private.pem
fi
openssl pkey -in secrets/identity-private.pem -pubout -out secrets/identity-public.pem
if [[ ! -f .env ]]; then
  { printf 'CONFIG_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'EUREKA_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'INTERNAL_SERVICE_TOKEN=%s\n' "$(openssl rand -hex 32)"
    printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    cat .env.example
  } > .env
fi
chmod 700 secrets
social_media_secret_path="secrets/social-media-db-password"
if [[ ! -f "$social_media_secret_path" ]]; then
  openssl rand -hex 32 > "$social_media_secret_path"
fi
chmod 444 "$social_media_secret_path"
printf 'Local keys and .env ready. Fill in WhatsApp, SMTP, and admin settings in .env.\n'
