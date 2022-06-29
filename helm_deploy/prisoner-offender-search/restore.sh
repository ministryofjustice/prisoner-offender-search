#!/bin/bash
set -e

check_http() { http --stream --check-status --ignore-stdin --timeout=600 "$@"; }

# grab last restore date from Prison API
if ! DATABASE_RESTORE_INFO=$(check_http GET "$PRISON_API_BASE_URL/api/restore-info"); then
  echo -e "\nUnable to find any restore information.  For environments other than pre-prod this is expected"
  if [[ -z "$FORCE_RUN" ]]; then
    echo -e "\nTo force a run set the FORCE_RUN environment variable when creating the job (see README.md)"
    echo "$DATABASE_RESTORE_INFO"
    exit 0
  fi
fi
DATABASE_RESTORE_DATE=$(echo "$DATABASE_RESTORE_INFO" | jq -r .)

# Check that we can connect to elastic search
if ! OUTPUT=$(http --check-status --ignore-stdin "$ELASTICSEARCH_PROXY_URL"); then
  echo -e "\nUnable to talk to elastic search API"
  echo "$OUTPUT"
  exit 1
fi

# Grab last restore date from elastic search
SAVED_RESTORE_DATE=$(check_http GET "$ELASTICSEARCH_PROXY_URL/restore-status/_doc/1" | jq -r '._source.date')

# we've found a date, check to see if we've had a newer restore
if [[ $SAVED_RESTORE_DATE != "null" && ! $DATABASE_RESTORE_DATE > $SAVED_RESTORE_DATE ]]; then
  echo -e "\nExisting restore date of $SAVED_RESTORE_DATE no newer than $DATABASE_RESTORE_DATE"
  if [[ -z "$FORCE_RUN" ]]; then
    echo -e "\nTo force a run set the FORCE_RUN environment variable when creating the job (see README.md)"
    exit 0
  fi
fi

# we would normally restore to a different namespace than the one we've taken the snapshot from
SNAPSHOT_NAMESPACE="${NAMESPACE_OVERRIDE:-${NAMESPACE}}"
ENDPOINT_SNAPSHOT_NAMESPACE="$ELASTICSEARCH_PROXY_URL/_snapshot/$SNAPSHOT_NAMESPACE"
ENDPOINT_LATEST="$ENDPOINT_SNAPSHOT_NAMESPACE/latest"
INDICES="prisoner-search-a,prisoner-search-b,offender-index-status"

# Register restore snapshot repo if not already
if ! check_http GET "$ENDPOINT_SNAPSHOT_NAMESPACE" &>/dev/null; then
  echo -e "\nCreating restore snapshot repository"
  # If the snapshot repo with same name as namespace does not exist, create it.
  # Note that we set to readonly so that we can't accidentally override the snapshot during restore.
  settings="{
    \"bucket\": \"${BUCKET_NAME}\",
    \"region\": \"eu-west-2\",
    \"role_arn\": \"${SNAPSHOT_ROLE_ARN}\",
    \"base_path\": \"${SNAPSHOT_NAMESPACE}\",
    \"readonly\": \"true\"
  }"
  check_http --print=Hbh POST "$ENDPOINT_SNAPSHOT_NAMESPACE" type="s3" settings:="${settings}"
fi

# Check that we have a snapshot to restore
if ! check_http "$ENDPOINT_LATEST"; then
  echo -e "\nUnable to find a snapshot at $ENDPOINT_LATEST"
  exit 1
fi

# Get the original count of indices
INDEX_COUNT=$(check_http GET "$ELASTICSEARCH_PROXY_URL/_cat/indices?format=json" | jq length)
EXPECTED_INDEX_COUNT=$((INDEX_COUNT - 3)) # we're deleting 3 indices

echo -e "\nDeleting indices"
# the delete will return immediately with an acknowledged
check_http --print=Hbh DELETE "$ELASTICSEARCH_PROXY_URL/$INDICES"
sleep 10

# so have to then wait until we have removed the correct amount of indices
# can't just GET on our indices since elastic will return 404 if ANY of them exist, whereas we need to check ALL
echo -e "\nWaiting for indices to be removed"
COUNT=0
while [[ $(check_http GET "$ELASTICSEARCH_PROXY_URL/_cat/indices?format=json" | jq length) -ne $EXPECTED_INDEX_COUNT ]]; do
  echo -n "."
  COUNT=$((COUNT+1))
  if [[ $COUNT -gt 20 ]]; then
    echo "Timed out waiting for $EXPECTED_INDEX_COUNT to remain"
    check_http --print=Hbh GET "$ELASTICSEARCH_PROXY_URL/_cat/indices"
    exit 1
  fi
  sleep 10
done

# Restore snapshot
echo -e "\nRestoring snapshot"
check_http --print=Hbh POST "$ENDPOINT_LATEST/_restore" indices="$INDICES"

# We have to now wait for the cluster to then go from yellow state to green
echo -e "\nWaiting for cluster to become healthy"
COUNT=0
while [[ $(check_http GET "$ELASTICSEARCH_PROXY_URL/_cluster/health" | jq --raw-output .status) != "green" ]]; do
  echo -n "."
  COUNT=$((COUNT+1))
  if [[ $COUNT -gt 30 ]]; then
    echo -e "\nTimed out waiting for cluster to be healthy"
    check_http GET --print=Hbh "$ELASTICSEARCH_PROXY_URL/_cluster/health?pretty=true"
    exit 1
  fi
  sleep 10
done

echo -e "\nRestore successful"
check_http --print=b GET "$ELASTICSEARCH_PROXY_URL/_cat/indices/$INDICES"

# now stash away the restore status in an elastic search index
check_http --print=Hbh POST "$ELASTICSEARCH_PROXY_URL/restore-status/_doc/1" "date=$DATABASE_RESTORE_DATE"