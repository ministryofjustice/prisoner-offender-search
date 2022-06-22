#!/bin/bash
set -e

# Snapshot restores only happen every other week, crontab doesn't really support this
week=$(date +%U)
if [[ $(("$week" % 2)) == 0 ]]; then
  echo "Not running restore this week, restores only happens on odd week numbers, check back next week."
  exit 0
fi

# Check that we can connect to elastic search
if ! OUTPUT=$(http --check-status --ignore-stdin "${ELASTICSEARCH_PROXY_URL}"); then
  echo -e "\nUnable to talk to elastic search API"
  echo "$OUTPUT"
  exit 1
fi

http_es() { http --stream --check-status --ignore-stdin --timeout=600 "$@"; }

# we would normally restore to a different namespace than the one we've taken the snapshot from
SNAPSHOT_NAMESPACE="${NAMESPACE_OVERRIDE:-${NAMESPACE}}"
ENDPOINT_SNAPSHOT_NAMESPACE="$ELASTICSEARCH_PROXY_URL/_snapshot/$SNAPSHOT_NAMESPACE"
ENDPOINT_LATEST="$ENDPOINT_SNAPSHOT_NAMESPACE/latest"
INDICES="prisoner-search-a,prisoner-search-b,offender-index-status"

# Register restore snapshot repo if not already
if ! http_es GET "$ENDPOINT_SNAPSHOT_NAMESPACE" &>/dev/null; then
  echo -e "\Creating restore snapshot repository"
  # If the snapshot repo with same name as namespace does not exist, create it.
  # Note that we set to readonly so that we can't accidentally override the snapshot during restore.
  settings="{
    \"bucket\": \"${BUCKET_NAME}\",
    \"region\": \"eu-west-2\",
    \"role_arn\": \"${SNAPSHOT_ROLE_ARN}\",
    \"base_path\": \"${SNAPSHOT_NAMESPACE}\",
    \"readonly\": \"true\"
  }"
  http_es --print=Hbh POST "$ENDPOINT_SNAPSHOT_NAMESPACE" type="s3" settings:="${settings}"
fi

# Get the original count of indices
INDEX_COUNT=$(http_es GET "$ELASTICSEARCH_PROXY_URL/_cat/indices?format=json" | jq length)
EXPECTED_INDEX_COUNT=$((INDEX_COUNT - 3)) # we're deleting 3 indices

echo -e "\nDeleting indices"
# the delete will return immediately with an acknowledged
http_es --print=Hbh DELETE "$ELASTICSEARCH_PROXY_URL/$INDICES"
sleep 10

# so have to then wait until we have removed the correct amount of indices
# can't just GET on our indices since elastic will return 404 if ANY of them exist, whereas we need to check ALL
echo -e "\nWaiting for indices to be removed"
COUNT=0
while [[ $(http_es GET "$ELASTICSEARCH_PROXY_URL/_cat/indices?format=json" | jq length) -ne $EXPECTED_INDEX_COUNT ]]; do
  echo -n "."
  COUNT=$((COUNT+1))
  if [[ $COUNT -gt 20 ]]; then
    echo "Timed out waiting for $EXPECTED_INDEX_COUNT to remain"
    http_es --print=Hbh GET "$ELASTICSEARCH_PROXY_URL/_cat/indices"
    exit 1
  fi
  sleep 10
done

# Restore snapshot
echo -e "\nRestoring snapshot"
http_es --print=Hbh POST "$ENDPOINT_LATEST/_restore" indices="$INDICES"

# We have to now wait for the cluster to then go from yellow state to green
echo -e "\nWaiting for cluster to become healthy"
COUNT=0
while [[ $(http_es GET "$ELASTICSEARCH_PROXY_URL/_cluster/health" | jq --raw-output .status) != "green" ]]; do
  echo -n "."
  COUNT=$((COUNT+1))
  if [[ $COUNT -gt 30 ]]; then
    echo -e "\nTimed out waiting for cluster to be healthy"
    http_es GET --print=Hbh "$ELASTICSEARCH_PROXY_URL/_cluster/health?pretty=true"
    exit 1
  fi
  sleep 10
done

echo -e "\nRestore successful"
http_es --print=b GET "$ELASTICSEARCH_PROXY_URL/_cat/indices/$INDICES"
