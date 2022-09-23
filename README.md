# Prisoner Offender Search

[![CircleCI](https://circleci.com/gh/ministryofjustice/prisoner-offender-search/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prisoner-offender-search)
[![Docker](https://quay.io/repository/hmpps/prisoner-offender-search/status)](https://quay.io/repository/hmpps/prisoner-offender-search/status)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-offender-search-dev.prison.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config)

The purpose of this service is to:
* API to provides searching of offender records in NOMIS via Elastic search (ES)
* Keep the Elastic Search (ES) prison index up to date with changes from Prison systems (NOMIS)
* Rebuild the index when required without an outage

### Offender updates

This service subscribes to the prison offender events

When this event is received a message is put onto the event queue.  The event queue then processes that message -
the latest offender record is retrieved via the `prison-api` and upserted into the offender index.
If the message processing fails then the message is transferred onto the event dead letter queue (DLQ).

### Index rebuilds

This service maintains two indexes `prison-search-index-a` and `prison-search-index-b` know in the code as `INDEX_A` and `INDEX_B`.

In normal running one of these indexes will be "active" while the other is dormant and not in use.

When we are ready to rebuild the index the "other" non-active index is transitioned into an `in-progress` state of `true`.

```shell
    http PUT /prisoner-index/build-index
```

The entire NOMIS offender base is retrieved and over several hours the other index is fully populated.

Once the index has finished, if there are no errors then the (housekeeping cronjob)[#housekeeping-cronjob] will mark the index as complete and switch to the new index.

If the index build fails - there are messages left on the index dead letter queue - then the new index will remain inactive until the DLQ is empty. It may take user intervention to clear the DLQ if some messages are genuinely unprocessable (rather than just failed due to e.g. network issues).

#### ElasticSearch Runtime exceptions
Two ES runtime exceptions, ElasticsearchException and ElasticSearchIndexingException, are caught during the re-indexing process to safeguard the integrity of the index status. Once caught,
the inError status flag is set on the IndexStatus.  The flag ensures that manipulation of the index is forbidden when in this state.
Only cancelling the index process will reset the flag and subsequently allow a rebuild of the index to be invoked.
```shell
    http PUT /prisoner/index/cancel-index
```

#### Index switch

Given the state of the each index is itself held in ES under the `in-progress` index with a single "document" when the INDEX_A/INDEX_B indexes switch there are actually two changes:
* The document in `offender-index-status` to indicate which index is currently active
* The ES `current-index` is switched to point at the active index. This means external clients can safely use the `offender` index without any knowledge of the INDEX_A/INDEX_B indexes.

Indexes can be switched without rebuilding, if they are both marked as "inProgress": false and "inError":false
```shell
    http PUT /prisoner/index/switch-index
```

### Housekeeping Cronjob
There is a Kubernetes CronJob which runs on a schedule to perform the following tasks:
* Checks if an index build has completed and if so then marks the build as complete (which switches the search to the new index)
* A threshold is set for each environment (in the helm values file) and the index will not be marked as complete until this threshold is met. This is to prevent switching to an index that does not look correct and will require a manual intervention to complete the index build (e.g. calling the `/mark-complete` endpoint manually).

The CronJob calls the endpoint `/prisoner-index/queue-housekeeping` which is not secured by Spring Security. To prevent external calls to the endpoint it has been secured in the ingress instead.

### Running

`localstack` is used to emulate the AWS SQS and Elastic Search service.

#### Running prisoner offender search in Docker
To start up localstack and other dependencies with prisoner offender search running in Docker too:
```shell
docker compose up localstack oauth prisonapi restricted-patients pos-db

Once localstack has started then, in another terminal, run the following command to start prisoner offender search too:
```shell
docker-compose up prisoner-offender-search --detach
```
  To check that it has all started correctly use `docker ps`.

#### Running prisoner offender search in IntelliJ or on the command line
To start up localstack and other dependencies with prisoner offender search running in IntelliJ:
```shell
docker compose up --scale prisoner-offender-search=0
```
To then run prisoner offender search from the command line:
```
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```
Alternatively create a Spring Boot run configuration with active profile of `dev` and main class `uk.gov.justice.digital.hmpps.prisonersearch.PrisonerOffenderSearch`.

#### Running the tests
If just running the tests then the following will just start localstack as the other dependencies are mocked out:

```shell
docker compose -f docker-compose-localstack-tests.yml up
```
Then the following command will run all the tests:
```shell
./gradlew test
```

#### Deleting localstack data between runs
Since localstack persists data between runs it may be necessary to delete the localstack temporary data:

Mac
```shell
rm -rf $TMPDIR/data
```
Linux
```shell
sudo rm -rf /tmp/localstack
```
Docker Desktop for Windows (started using `docker-compose-windows.yml`)
```shell
docker volume rm -f prisoner-offender-search_localstack-vol
```

*Please note the above will not work on a Mac using Docker Desktop since the Docker network host mode is not supported on a Mac*

On Mac it is recommended to run all components *except* prisoner-offender-search (see below). Then run prisoner-offender-search outside of docker using gradle:

```shell
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

### When running locally you can add some prisoners into Elastic with the following:-

#### Get a token
```shell
TOKEN=$(curl --location --request POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" --header "Authorization: Basic $(echo -n prisoner-offender-search-client:clientsecret | base64)" |  jq -r .access_token)
```

#### Start indexing
```shell
curl --location --request PUT "http://localhost:8080/prisoner-index/build-index" --header "Authorization: Bearer $TOKEN" | jq -r
```

#### Check all indexed with
```shell
curl --location --request GET "http://localhost:8080/info" | jq -r
```

#### If 53 records then mark complete
```shell
curl --location --request PUT "http://localhost:8080/prisoner-index/mark-complete" --header "Authorization: Bearer $TOKEN" | jq -r
```

#### Now test a search
```shell
curl --location --request POST "http://localhost:8080/prisoner-search/match" --header "Authorization: Bearer $TOKEN" --header 'Content-Type: application/json' \
 --data-raw '{
    "lastName": "Smith"
 }' | jq -r
```

#### View ES indexes
```shell
curl --location --request POST "http://es1.eu-west-2.es.localhost.localstack.cloud:4566/prisoner-search-a/_search" | jq
```

### Alternative running
Or to just run `localstack` which is useful when running against an a non-local test system, you will  need the `spring.profiles.active=localstack` and `sqs.provider=full-localstack` environment variables:

```shell
TMPDIR=/private$TMPDIR docker-compose up localstack
```

In all of the above the application should use the host network to communicate with `localstack` since AWS Client will try to read messages from localhost rather than the `localstack` network.

### Experimenting with messages

There are two handy scripts to add messages to the queue with data that matches either the dev environment or data in the test Docker version of the apps.

Purging a local queue:
```shell
aws --endpoint-url=http://localhost:4566 sqs purge-queue --queue-url http://localhost:4566/queue/prisoner_offender_index_queue
```

## Regression tests

Recommended regression tests is as follows:

* A partial build of index - see the `Rebuilding an index` instructions below. The rebuild does not need to be completed but expect the info to show something like this:
```
    "index-status": {
    "id": "STATUS",
    "currentIndex": "INDEX_A",
    "startIndexTime": "2020-09-23T10:08:33",
    "inProgress": true
    },
    "index-size": {
    "INDEX_A": 579543,
    "INDEX_B": 521
    },
    "index-queue-backlog": "578975"
```
So long as the index is being populated and the ` "index-queue-backlog"` figure is decreasing after some time (e.g. 10 minutes) it demonstrates the application is working.

Check the health endpoint to show the Index DLQ is not building up with errors (e.g. `https://prisoner-search-dev.hmpps.service.justice.gov.uk/health`):
```
    "indexQueueHealth": {
      "status": "UP",
      "details": {
        "MessagesOnQueue": 41834,
        "MessagesInFlight": 4,
        "dlqStatus": "UP",
        "MessagesOnDLQ": 0
      }
    }
```
The above result indicates a valid state since the `MessagesOnDLQ` would be zero.

The build can either be left to run or cancelled using the following endpoint:
 ```
curl --location --request PUT 'https://prisoner-search-dev.hmpps.service.justice.gov.uk/prisoner-index/cancel-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>'
 ```

## Support

### Custom Alerts

#### Synthetic Monitor

There is a Cronjob called `synthetic-monitor` which performs a simple prisoner search every 10 minutes. It then records the number of results and the request duration as telemetry events on Application Insights.

You can see those telemetry events over time with these App Insights log queries:

```kusto
customEvents
| where cloud_RoleName == "prisoner-offender-search"
| where name == "synthetic-monitor"
| extend timems=toint(customDimensions.timeMs),results=toint(customDimensions.results)
| summarize avg(timems),max(timems) by bin(timestamp, 15m)
| render timechart 
```

```kusto
customEvents
| where cloud_RoleName == "prisoner-offender-search"
| where name == "synthetic-monitor"
| extend timems=toint(customDimensions.timeMs),results=toint(customDimensions.results)
| summarize avg(results),max(results) by bin(timestamp, 15m)
| render timechart 
```

An alert has been created for each metric in Application Insights. 

* `Prisoner Offender Search - response time (synthetic monitor)` - checks if the average response time for the search is higher than an arbitrary limit. This indicates that the system is performing slowly and you should investigate the load on the system.
* `Prisoner Offender Search - result size (synthetic monitor` - checks if the number of results returned by the search has dropped below an arbitrary limit. This indicates that either the data in the system has drastically changed or that there is some kind of bug with the search meaning not all results are being found.

### Raw Elastic Search access

Access to the raw Elastic Search indexes is only possible from the Cloud Platform `prisoner-offender-search` family of namespaces.

For instance, the following curl command in any environment would return a list all indexes e.g.:

```
http http://aws-es-proxy-service:9200/_cat/indices

green open prisoner-search-a     tlGst8dmS2aE8knxfxJsfQ 5 1 2545309 1144511   1.1gb 578.6mb
green open offender-index-status v9traPPRS9uo7Ui0J6ixOQ 1 1       1       0  10.7kb   5.3kb
green open prisoner-search-b     OMcdEir_TgmTP-tzybwp7Q 5 1 2545309  264356 897.6mb 448.7mb
green open .kibana_2             _rVcHdsYQAKyPiInmenflg 1 1      43       1 144.1kb    72kb
green open .kibana_1             f-CWilxMRyyihpBWBON1yw 1 1      39       6 176.3kb  88.1kb
```

### Rebuilding an index

To rebuild an index the credentials used must have the ROLE `PRISONER_INDEX` therefore it is recommend to use client credentials with the `ROLE_PRISONER_INDEX` added and pass in your username when getting a token.
In the test and local dev environments the `prisoner-offender-search-client` has conveniently been given the `ROLE_PRISONER_INDEX`.

The rebuilding of the index can be sped up by increasing the number of pods handling the reindex e.g.:

```
kubectl -n prisoner-offender-search-dev scale --replicas=8 deployment/prisoner-offender-search
```
After obtaining a token for the environment invoke the reindex with a curl command or Postman e.g.:

```
curl --location --request PUT 'https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/prisoner-index/build-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>'
```

For production environments where access is blocked by inclusion lists this will need to be done from within a Cloud Platform pod.

Next monitor the progress of the rebuilding via the info endpoint (e.g. https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/info). This will return details like the following:

```
    "index-status": {
    "id": "STATUS",
    "currentIndex": "INDEX_A",
    "startIndexTime": "2020-09-23T10:08:33",
    "inProgress": true
    },
    "index-size": {
    "INDEX_A": 702344,
    "INDEX_B": 2330
    },
    "index-queue-backlog": "700000"
```

When `"index-queue-backlog": "0"` has reached zero then all indexing messages have been processed. Check the dead letter queue is empty via the health check (e.g https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/health). This should show the queues DLQ count at zero, e.g.:
```
    "indexQueueHealth": {
      "status": "UP",
      "details": {
        "MessagesOnQueue": 0,
        "MessagesInFlight": 0,
        "dlqStatus": "UP",
        "MessagesOnDLQ": 0
      }
    },
```

The indexing is ready to marked as complete using another call to the service e.g:

```
curl --location --request PUT 'https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/prisoner-index/mark-complete' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>'
```

One last check of the info endpoint should confirm the new state, e.g.:

```
    "index-status": {
    "id": "STATUS",
    "currentIndex": "INDEX_B",
    "startIndexTime": "2020-09-23T10:08:33",
    "endIndexTime": "2020-09-25T11:27:22",
    "inProgress": false
    },
    "index-size": {
    "INDEX_A": 702344,
    "INDEX_B": 702344
    },
    "index-queue-backlog": "0"
```

Pay careful attention to `"currentIndex": "INDEX_A"` - this shows the actual index being used by clients.

### Snapshot cronjobs
There are two kubernetes cronjobs
1. A scheduled job runs at 2.30am each day to take a snapshot of the whole cluster.
2. A scheduled job runs every four hours in pre-production only.  This checks to see if there is a newer version of
the NOMIS database since the last restore and if so then does another restore.  Pre-production has access to the
production snapshot s3 bucket and uses that to restore the latest production snapshot created by step 1.

#### Manually running the create snapshot cronjob
```shell
kubectl create job --from=cronjob/prisoner-offender-search-elasticsearch-snapshot prisoner-offender-search-elasticsearch-snapshot-<user>
```
will trigger the job to create a snapshot called latest.
Job progress can then be seen by running `kubectl logs -f` on the newly created pod.

#### Manually running the restore snapshot cronjob
The restore cronjob script only runs if there is a newer NOMIS database so we need to override the configuration to ensure to force the run.
We do that by using `jq` to amend the json and adding in the `FORCE_RUN=true` parameter.

In dev and production there is only one snapshot repository so 
```shell
kubectl create job --dry-run=client --from=cronjob/prisoner-offender-search-elasticsearch-restore prisoner-offender-search-elasticsearch-restore-<user> -o "json" | jq ".spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]" | kubectl apply -f -
```
will trigger the job to restore the snapshot called latest.
Job progress can then be seen by running `kubectl logs -f` on the newly created pod.

The default for the cronjob in pre-production is to restore from production.  If that is required then the command above
will suffice.  However, if it required to restore from the previous pre-production snapshot then we need to clear the 
`NAMESPACE_OVERRIDE` environment variable so that it doesn't try to restore from production instead.
```shell
kubectl create job --dry-run=client --from=cronjob/prisoner-offender-search-elasticsearch-restore prisoner-offender-search-elasticsearch-restore-pgp -o "json" | jq "(.spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]) | (.spec.template.spec.containers[0].env[] | select(.name==\"NAMESPACE_OVERRIDE\").value) |= \"\"" | kubectl apply -f -
```

The last successful restore information is stored in a `restore-status` index.  To find out when the last restore ran:
```
http GET http://localhost:9200/restore-status/_doc/1
```

### Restore from a snapshot (if both indexes have become corrupt/empty)

If we are restoring from the snapshot it means that the current index and other index are broken, we need to delete them to be able to restore from the snapshot.
At 2.30am we have a scheduled job that takes the snapshot of the whole cluster which is called `latest` and this should be restored.

1. To restore we need to port-forward to the es instance (replace NAMESPACE with the affected namespace)
   ```shell
   kubectl -n <NAMESPACE> port-forward $(kubectl -n <NAMESPACE> get pods | grep aws-es-proxy-cloud-platform | grep Running | head -1 | awk '{print $1}') 9200:9200
   ```
2. Delete the current indexes
   ```shell
   http DELETE http://localhost:9200/_all
   ```
3. Check that the indices have all been removed
   ```shell
   http http://localhost:9200/_cat/indices
   ```
   If you wait to long between the delete and restore then the `.kibana` ones might get recreated, you'll need to delete them again otherwise the restore will fail.
4. Then we can start the restore (SNAPSHOT_NAME for the overnight snapshot is `latest`)
   ```shell
   http POST 'http://localhost:9200/_snapshot/<NAMESPACE>/<SNAPSHOT_NAME>/_restore' include_global_state=true
   ```

   The `include_global_state: true` is set true so that we copy the global state of the cluster snapshot over. The default for restoring,
   however, is `include_global_state: false`. If only restoring a single index, it could be bad to overwrite the global state but as we are
   restoring the full cluster we set it to true.

5. The indices will be yellow until they are all restored - again check they are completed with
   ```shell
   http http://localhost:9200/_cat/indices
   ```
#### To view the state of the indexes while restoring from a snapshot

##### Cluster health

`http 'http://localhost:9200/_cluster/health'`

The cluster health status is: green, yellow or red. On the shard level, a red status indicates that the specific shard is not allocated in the cluster, yellow means that the primary shard is allocated but replicas are not, and green means that all shards are allocated. The index level status is controlled by the worst shard status. The cluster status is controlled by the worst index status.

##### Shards
`http 'http://localhost:9200/_cat/shards'`

The shards command is the detailed view of what nodes contain which shards. It will tell you if it’s a primary or replica, the number of docs, the bytes it takes on disk, and the node where it’s located.

##### Recovery
`http 'http://localhost:9200/_cat/recovery'`

Returns information about ongoing and completed shard recoveries

#### To take a manual snapshot, perform the following steps:

1. You can't take a snapshot if one is currently in progress. To check, run the following command:

   `http 'http://localhost:9200/_snapshot/_status'`
2. Run the following command to take a manual snapshot:

   `http PUT 'http://localhost:9200/_snapshot/<NAMESPACE>/snapshot-name'`

you can now use the restore commands above to restore the snapshot if needed

##### To remove a snapshot
`http DELETE 'http://localhost:9200/_snapshot/<NAMESPACE>/snapshot-name'`

#### Other command which will help when looking at restoring a snapshot

To see all snapshot repositories, run the following command (normally there will only be one, as we have one per namespace):

`http 'http://localhost:9200/_snapshot?pretty'`

In the pre-production namespace there will be a pre-production snapshot repository and also the production repository.
The latter is used for the restore and should be set to `readonly` so that it can't be overwritten with 
pre-production data.

To see all snapshots for the namespace run the following command:

`http 'http://localhost:9200/_snapshot/<NAMESPACE>/_all?pretty'`

### Useful App Insights Queries

#### General logs (filtering out the offender update)
``` kusto
traces
| where cloud_RoleName == "prisoner-offender-search"
| where message !startswith "Updating offender"
| order by timestamp desc
```

#### General logs including spring startup
``` kusto
traces
| where cloud_RoleInstance startswith "prisoner-offender-search"
| order by timestamp desc
```

#### Interesting exceptions
``` kusto
exceptions
| where cloud_RoleName == "prisoner-offender-search"
| where operation_Name != "GET /health"
| where customDimensions !contains "health"
| where details !contains "HealthCheck"
| order by timestamp desc
```

#### Indexing requests
``` kusto
requests
| where cloud_RoleName == "prisoner-offender-search"
//| where timestamp between (todatetime("2020-08-06T18:20:00") .. todatetime("2020-08-06T18:22:00"))
| order by timestamp desc
```

#### Prison API requests during index build
``` kusto
requests
| where cloud_RoleName == "prison-api"
| where name == "GET OffenderResourceImpl/getOffenderNumbers"
| where customDimensions.clientId == "prisoner-offender-search-client"
```

```kusto
requests
| where cloud_RoleName == "prison-api"
| where name == "GET OffenderResourceImpl/getOffender"
| where customDimensions.clientId == "prisoner-offender-search-client"
| order by timestamp desc
```
