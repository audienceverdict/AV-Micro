#!/bin/sh
# Provision only SocialMedia-owned storage; safe for populated AV MySQL volumes.
set -eu
: "${MYSQL_HOST:=mysql}"
: "${MYSQL_ROOT_PASSWORD:?MySQL administrative credential is required}"
: "${SOCIAL_MEDIA_DB_PASSWORD_FILE:=/run/secrets/social_media_db_password}"
sync_password=$(cat "$SOCIAL_MEDIA_DB_PASSWORD_FILE")
# setup-local.sh generates 32-byte hex secrets. Validate before SQL interpolation.
case "$sync_password" in
  ''|*[!a-fA-F0-9]*) echo "SocialMedia database secret must be 64 hexadecimal characters" >&2; exit 1 ;;
esac
if [ "${#sync_password}" -ne 64 ]; then
  echo "SocialMedia database secret must be 64 hexadecimal characters" >&2
  exit 1
fi
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
mysql --protocol=tcp --host="$MYSQL_HOST" --user=root <<SQL
CREATE DATABASE IF NOT EXISTS social_media CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER IF NOT EXISTS 'social_media'@'%' IDENTIFIED BY '$sync_password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP ON social_media.* TO 'social_media'@'%';
SQL
unset MYSQL_PWD sync_password
echo "SocialMedia database and grants are ready"
