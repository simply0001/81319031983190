#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/common.sh"

require_command docker
validate_compose

shopt -s nullglob
test_files=("${INFRA_DIR}"/tests/database/*.test.sql)
shopt -u nullglob
((${#test_files[@]} > 0)) || die "no pgTAP test files found"

for test_file in "${test_files[@]}"; do
  test_output=""
  printf 'TEST  %s\n' "$(basename -- "${test_file}")"
  test_output="$(
    compose exec -T db psql \
      --username postgres \
      --dbname postgres \
      --set ON_ERROR_STOP=1 \
      <"${test_file}"
  )"
  printf '%s\n' "${test_output}"
  if [[ "${test_output}" == *"not ok"* ]] \
    || [[ "${test_output}" == *"# Looks like you failed"* ]]; then
    die "pgTAP assertions failed in $(basename -- "${test_file}")"
  fi
done
