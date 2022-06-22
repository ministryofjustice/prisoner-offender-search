#!/bin/bash
set -e

# Check that we can connect to elastic search
if ! OUTPUT=$(http --check-status --ignore-stdin "${ELASTICSEARCH_PROXY_URL}"); then
  echo -e "\nUnable to talk to elastic search API"
  echo "$OUTPUT"
  exit 1
fi

http_es() { http --stream --check-status --ignore-stdin --timeout=600 "$@"; }

ENDPOINT_SNAPSHOT_NAMESPACE="$ELASTICSEARCH_PROXY_URL/_snapshot/$NAMESPACE"
ENDPOINT_LATEST="$ENDPOINT_SNAPSHOT_NAMESPACE/latest"

# Register snapshot repo if not already
if ! http_es GET "$ENDPOINT_SNAPSHOT_NAMESPACE" &>/dev/null; then
  # If the snapshot repo with same name as namespace does not exist, create it.
  settings="{
    \"bucket\": \"${BUCKET_NAME}\",
    \"region\": \"eu-west-2\",
    \"role_arn\": \"${SNAPSHOT_ROLE_ARN}\",
    \"base_path\": \"${NAMESPACE}\"
  }"
  http_es --print=Hbh POST "$ENDPOINT_SNAPSHOT_NAMESPACE" type="s3" settings:="${settings}"
fi

# Delete latest snapshot if exists
while http_es GET "$ENDPOINT_LATEST" &>/dev/null; do
  # Delete latest snapshot if exists - the delete will block until completed
  echo -e "\nDeleting existing snapshot"
  http_es --print=Hbh DELETE "$ENDPOINT_LATEST"
  sleep 20
done

# Take snapshot
echo -e "\nCreating snapshot"
http_es --print=Hbh PUT "$ENDPOINT_LATEST"

echo -e "\nWaiting for snapshot to finish"
while ! http_es GET "$ENDPOINT_LATEST" 2>/dev/null | jq -e '.snapshots[] | select(.state == "SUCCESS")' &>/dev/null; do
  echo -n "."
  sleep 20
done
echo -e "\nSnapshot successful"
http_es --print=b GET "$ENDPOINT_LATEST"
