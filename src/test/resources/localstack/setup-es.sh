#!/usr/bin/env bash
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2
aws --endpoint-url=http://localhost:4578 es create-elasticsearch-domain --domain-name es1
