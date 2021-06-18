#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2

aws --endpoint-url=http://localhost:4566 es create-elasticsearch-domain --domain-name es1 > /dev/null
mkdir /tmp/localstack/es_backup || echo "Failed to create /tmp/localstack/es_backup folder"
chmod -R 777 /tmp/localstack || echo "Failed to chmod /tmp/localstack folder"
echo "Elasticsearch configured, please now wait for Running on http://0.0.0.0:4571 before connecting"
