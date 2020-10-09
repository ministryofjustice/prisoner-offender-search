# Prisoner Offender Search

[![CircleCI](https://circleci.com/gh/ministryofjustice/prisoner-offender-search/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prisoner-offender-search)
[![Docker](https://quay.io/repository/hmpps/prisoner-offender-search/status)](https://quay.io/repository/hmpps/prisoner-offender-search/status)

The purpose of this service is to:
* API to provides searching of offender records in NOMIS via Elastic search (ES)
* Keep the Elastic Search (ES) prison index up to date with changes from Prison systems (NOMIS)
* Rebuild the index when required without an outage

### Offender updates

This service subscribes to the prison offender events

When this event is received the latest offender record is retrieved via the `prison-api` and upserted into the offender index.

### Index rebuilds

This service maintains two indexes `prison-search-index-a` and `prison-search-index-b` know in the code as `INDEX_A` and `INDEX_B`.

In normal running one of these indexes will be "active" while the other is dormant and not in use. 

When we are ready to rebuild the index the "other" non-active index is transitioned into an `in-progress` state of `true`.

```
    PUT /prisoner-index/build-index
```
 
The entire NOMIS offender base is retrieved and over several hours the other index is fully populated. 

Once the index has finished, if there are no errors then the (housekeeping cronjob)[#housekeeping-cronjob] will mark the index as complete and switch to the new index.

If the index build fails - there are messages left on the index dead letter queue - then the new index will remain inactive until the DLQ is empty. It may take user intervention to clear the DLQ if some messages are genuinely unprocessable (rather than just failed due to e.g. network issues).  

#### Index switch

Given the state of the each index is itself held in ES under the `in-progress` index with a single "document" when the INDEX_A/INDEX_B indexes switch there are actually two changes:
* The document in `offender-index-status` to indicate which index is currently active
* The ES `current-index` is switched to point at the active index. This means external clients can safely use the `offender` index without any knowledge of the INDEX_A/INDEX_B indexes. 

Indexes can be switched without rebuilding, if they are both marked as "inProgress": false
```
    PUT /prisoner/index/switch-index
```

### Housekeeping Cronjob
There is a Kubernetes CronJob which runs on a schedule to perform the following tasks:
* Checks if an index build has completed and if so then marks the build as complete (which switches the search to the new index)

The CronJob calls the endpoint `/prisoner-index/queue-housekeeping` which is not secured by Spring Security. To prevent external calls to the endpoint it has been secured in the ingress instead. 

### Running

`localstack` is used to emulate the AWS SQS and Elastic Search service. When running the integration test this will be started automatically. If you want the tests to use an already running version of `localstack` run the tests with the environment `AWS_PROVIDER=localstack`. This has the benefit of running the test quicker without the overhead of starting the `localstack` container.

Any commands in `localstack/setup-sns.sh` and `localstack/setup-es.sh` will be run when `localstack` starts, so this should contain commands to create the appropriate queues.

Running all services locally:
```bash
docker-compose up 
```
Since localstack persists data between runs it maybe necessary to delete the localstack temporary data:

Mac
```bash
rm -rf $TMPDIR/data
```
Linux
```bash
sudo rm -rf /tmp/localstack
```

*Please note the above will not work on a Mac using docker desktop since the docker network host mode is not supported on a Mac*

For a Mac it recommended running all components *except* prisoner-offender-search (see below) then running prisoner-offender-search externally:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun 
```

Queues and topics and an ES instance will automatically be created when the `localstack` container starts.

Running all services except this application (hence allowing you to run this in the IDE)

```bash
docker-compose up --scale prisoner-offender-search=0 
```

Depending on the speed of your machine when running all services you may need to scale `prisoner-offender-search=0` until localstack starts. This is a workaround for an issue whereby Spring Boot gives up trying to connect to SQS when the services first starts up.

### Running tests

#### Test containers

`./gradlew test` will run all tests and will by default use test containers to start any required docker containers, e.g localstack
Note that TestContainers will start Elastic Search in its own container rather than using the one built into localstack.

#### External localstack

`AWS_PROVIDER=localstack ./gradlew test` will override the default behaviour and will expect localstack to already be started externally. In this mode the following services must be started `sqs,sns,es`

`docker-compose up localstack` will start the required AWS services.  

### When running locally you can add some prisoners into elastic with the following:-

#### Get a token
```bash
TOKEN=$(curl --location --request POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" --header "Authorization: Basic $(echo -n prisoner-offender-search-client:clientsecret | base64)" |  jq -r .access_token)
```

#### Start indexing
```bash
curl --location --request PUT "http://localhost:8080/prisoner-index/build-index" --header "Authorization: Bearer $TOKEN" | jq -r
```

#### Check all indexed with
```bash
curl --location --request GET "http://localhost:8080/info" | jq -r
```

If 52 records then mark complete
```bash
curl --location --request PUT "http://localhost:8080/prisoner-index/mark-complete" --header "Authorization: Bearer $TOKEN" | jq -r
```

#### Now test a search
```bash
curl --location --request POST "http://localhost:8080/prisoner-search/match" --header "Authorization: Bearer $TOKEN" --header 'Content-Type: application/json' \
 --data-raw '{
    "lastName": "Smith"
 }' | jq -r
```

#### View ES indexes
```bash
curl --location --request POST "http://localhost:4571/prisoner-search-a/_search" | jq
```

### Alternative running
Or to just run `localstack` which is useful when running against an a non-local test system Env need to be `spring.profiles.active=localstack` and `sqs.provider=full-localstack`

```bash
TMPDIR=/private$TMPDIR docker-compose up localstack
```

In all of the above the application should use the host network to communicate with `localstack` since AWS Client will try to read messages from localhost rather than the `localstack` network.

### Experimenting with messages

There are two handy scripts to add messages to the queue with data that matches either the dev environment or data in the test Docker version of the apps

Purging a local queue
```bash
aws --endpoint-url=http://localhost:4576 sqs purge-queue --queue-url http://localhost:4576/queue/prisoner_offender_index_queue
```



## Regression test

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

Check the health endpoint to show the Index DLQ is not building up with errors e.g: `https://prisoner-search-dev.hmpps.service.justice.gov.uk/health`

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
would be a valid state since the `MessagesOnDLQ` would be zero

The build can either be left to run or cancelled using the following endpoint 
 ``` 
curl --location --request PUT 'https://prisoner-search-dev.hmpps.service.justice.gov.uk/prisoner-index/cancel-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>>'

 ```  
## Support

### Raw Elastic Search access

Access to the raw Elastic Search indexes is only possible from the Cloud Platform `prisoner-offender-search` family of namespaces. 

For instance 

```
curl http://aws-es-proxy-service:9200/_cat/indices
```
in any environment would return a list all indexes e.g.
```
green open prisoner-search-a     tlGst8dmS2aE8knxfxJsfQ 5 1 2545309 1144511   1.1gb 578.6mb
green open offender-index-status v9traPPRS9uo7Ui0J6ixOQ 1 1       1       0  10.7kb   5.3kb
green open prisoner-search-b     OMcdEir_TgmTP-tzybwp7Q 5 1 2545309  264356 897.6mb 448.7mb
green open .kibana_2             _rVcHdsYQAKyPiInmenflg 1 1      43       1 144.1kb    72kb
green open .kibana_1             f-CWilxMRyyihpBWBON1yw 1 1      39       6 176.3kb  88.1kb
```

### Rebuilding an index

To rebuild an index the credentials used must have the ROLE `PRISONER_INDEX` therefore it is recommend to use client credentials with the `ROLE_PRISONER_INDEX` added and pass in your username when getting a token.
In the test and local dev environments the `prisoner-offender-search-client` has conveniently been given the `ROLE_PRISONER_INDEX`.

The rebuilding of the index can be sped up by increasing the number of pods handling the reindex e.g.

```
kubectl -n prisoner-offender-search-dev scale --replicas=8 deployment/prisoner-offender-search
``` 
After obtaining a token for the environment invoke the reindex with a curl command or Postman e.g.

```
curl --location --request PUT 'https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/prisoner-index/build-index' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>>'
``` 

For production environments where access is blocked by inclusion lists this will need to be done from within a Cloud Platform pod

Next monitor the progress of the rebuilding via the info endpoint e.g. https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/info
This will return details like the following:

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
 
 when `"index-queue-backlog": "0"` has reached zero then all indexing messages have been processed. Check the dead letter queue is empty via the health check e.g https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/health
 This should show the queues DLQ count at zero, e.g.
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
  
 The indexing is ready to marked as complete using another call to the service e.g
 
 ``` 
curl --location --request PUT 'https://prisoner-offender-search-dev.hmpps.service.justice.gov.uk/prisoner-index/mark-complete' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer <some token>>'

 ```  

One last check of the info endpoint should confirm the new state, e.g.

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

### Useful App Insights Queries
####General logs (filtering out the offender update)
``` kusto
traces
| where cloud_RoleName == "prisoner-offender-search"
| where message !startswith "Updating offender"
| order by timestamp desc
```

####General logs including spring startup
``` kusto
traces
| where cloud_RoleInstance startswith "prisoner-offender-search"
| order by timestamp desc
```

####Interesting exceptions
``` kusto
exceptions
| where cloud_RoleName == "prisoner-offender-search"
| where operation_Name != "GET /health"
| where customDimensions !contains "health"
| where details !contains "HealthCheck"
| order by timestamp desc
```

####Indexing requests
``` kusto
requests
| where cloud_RoleName == "prisoner-offender-search"
//| where timestamp between (todatetime("2020-08-06T18:20:00") .. todatetime("2020-08-06T18:22:00"))
| order by timestamp desc 
```

####Prison API requests during index build
``` kusto
requests
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
| order by timestamp desc ```
```
