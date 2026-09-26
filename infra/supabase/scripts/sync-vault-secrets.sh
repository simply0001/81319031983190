#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

SECRETS=(
  'DISCORD_LIMIT_REQUESTS_WEBHOOK_URL|discord_limit_requests_webhook|Discord webhook for developer limit requests|discord'
  'DISCORD_SUPPORTERS_WEBHOOK_URL|discord_supporters_webhook|Discord webhook for Ko-fi payments|discord'
  'KOFI_VERIFICATION_TOKEN|kofi_verification_token|Ko-fi webhook verification token|opaque'
)

sync_secret() {
  local env_name="$1" secret_name="$2" description="$3" kind="$4" value

  value="$(read_env_value "${env_name}" 2>/dev/null || true)"

  if [[ -z "${value}" ]]; then
    compose exec -T db psql --username postgres --dbname postgres --quiet --set ON_ERROR_STOP=1 \
      --command "delete from vault.secrets where name = '${secret_name}'" </dev/null
    printf '%s is empty; the Vault secret %s was removed.\n' "${env_name}" "${secret_name}"
    return 0
  fi

  case "${kind}" in
    discord)
      if [[ "${value}" != https://discord.com/api/webhooks/* && "${value}" != https://discordapp.com/api/webhooks/* ]]; then
        die "${env_name} must be a Discord webhook URL"
      fi
      ;;
    opaque)
      if (( ${#value} < 8 || ${#value} > 128 )) || [[ "${value}" =~ [[:space:]] ]]; then
        die "${env_name} must be 8 to 128 characters without whitespace"
      fi
      ;;
    *)
      die "unknown secret kind ${kind}"
      ;;
  esac

  compose exec -T db psql --username postgres --dbname postgres --quiet --set ON_ERROR_STOP=1 \
    --set "secret=${value}" --set "secret_name=${secret_name}" --set "description=${description}" <<'SQL'
select set_config('pocketpass.sync_secret', :'secret', false);
select set_config('pocketpass.sync_secret_name', :'secret_name', false);
select set_config('pocketpass.sync_secret_description', :'description', false);
do $$
declare
  v_id uuid;
  v_secret text := current_setting('pocketpass.sync_secret');
  v_name text := current_setting('pocketpass.sync_secret_name');
  v_description text := current_setting('pocketpass.sync_secret_description');
begin
  select id into v_id from vault.secrets where name = v_name;
  if v_id is null then
    perform vault.create_secret(v_secret, v_name, v_description);
  else
    perform vault.update_secret(v_id, v_secret, v_name, v_description);
  end if;
end
$$;
SQL

  printf 'Vault secret %s is in sync with %s.\n' "${secret_name}" "${env_name}"
}

for entry in "${SECRETS[@]}"; do
  IFS='|' read -r env_name secret_name description kind <<<"${entry}"
  sync_secret "${env_name}" "${secret_name}" "${description}" "${kind}"
done
