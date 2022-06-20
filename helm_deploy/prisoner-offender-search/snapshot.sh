#!/bin/bash
set -e
HTTPIE_SESSION="$HOME/.httpie_session.json"
HTTPIE_OPTS=("--stream" "--check-status" "--ignore-stdin" "--timeout=600" "--session-read-only=${HTTPIE_SESSION}")
API_ENDPOINT="${ELASTICSEARCH_PROXY_URL}"

# Setup httpie session
if ! OUTPUT=$(http --check-status --ignore-stdin --session="${HTTPIE_SESSION}" "${API_ENDPOINT}"); then
  echo -e "\nUnable to talk to elastic search API"
  echo "$OUTPUT"
  exit 1
fi

http_es() {
  http "${HTTPIE_OPTS[@]}" "$@"
}

# Register snapshot repo if not already
if ! http_es GET "$API_ENDPOINT/_snapshot/$NAMESPACE" &>/dev/null; then
  # If the snapshot repo with same name as namespace does not exist, create it.
  settings="{
    \"bucket\": \"${BUCKET_NAME}\",
    \"region\": \"eu-west-2\",
    \"role_arn\": \"${SNAPSHOT_ROLE_ARN}\",
    \"base_path\": \"${NAMESPACE}\"
  }"
  http_es --print=Hbh POST "$API_ENDPOINT/_snapshot/$NAMESPACE" \
    type="s3" \
    settings:="${settings}"
fi

# Delete latest snapshot if exists
while http_es GET $API_ENDPOINT/_snapshot/$NAMESPACE/latest &>/dev/null; do
  # Delete latest snapshot if exists - the delete will block until completed
  echo -e "\nDeleting existing snapshot"
  http_es --print=Hbh DELETE "$API_ENDPOINT/_snapshot/$NAMESPACE/latest"
  sleep 20
done

# Take snapshot
echo -e "\nCreating snapshot"
http_es --print=Hbh PUT "$API_ENDPOINT/_snapshot/$NAMESPACE/latest"

echo -e "\nWaiting for snapshot to finish"
while ! http_es GET "$API_ENDPOINT/_snapshot/$NAMESPACE/latest" 2>/dev/null | jq -e '.snapshots[] | select(.state == "SUCCESS")' &>/dev/null; do
  echo -n "."
  sleep 20
done
echo -e "\nSnapshot successful"
http_es --print=b GET "$API_ENDPOINT/_snapshot/$NAMESPACE/latest"
