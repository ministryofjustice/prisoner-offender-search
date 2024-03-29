#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2

aws --endpoint-url=http://localhost:4566 es create-elasticsearch-domain --domain-name es01 >/dev/null

echo "Elasticsearch configured. Please wait until '... Active license is now [BASIC]; Security is disabled' appears before connecting"
