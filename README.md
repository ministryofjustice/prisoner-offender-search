# Prisoner Offender Search

[![CircleCI](https://circleci.com/gh/ministryofjustice/prisoner-offender-search/tree/master.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prisoner-offender-search)
[![Docker](https://quay.io/repository/hmpps/prisoner-offender-search/status)](https://quay.io/repository/hmpps/prisoner-offender-search/status)


Self-contained fat-jar micro-service to listen for events from Prison systems (NOMIS) and update elastic search cache

### Pre-requisite

`Docker` Even when running the tests docker is used by the integration test to load `localstack` (for AWS services). The build will automatically download and run `localstack` on your behalf.

### Building

```./gradlew build```

### Running

`localstack` is used to emulate the AWS SQS service. When running the integration test this will be started automatically. If you want the tests to use an already running version of `locastack` run the tests with the environment `SQS_PROVIDER=localstack`. This has the benefit of running the test quicker without the overhead of starting the `localstack` container.

Any commands in `localstack/setup-sns.sh` will be run when `localstack` starts, so this should contain commands to create the appropriate queues.

Running all services locally:
```bash
TMPDIR=/private$TMPDIR docker-compose up
```
Queues and topics will automatically be created when the `localstack` container starts.

Running all services except this application (hence allowing you to run this in the IDE)

```bash
TMPDIR=/private$TMPDIR docker-compose up --scale prisoner-offender-search=0
```

### Add localstack to your /etc/hosts
```
127.0.0.1 localstack
```
Check the docker-compose file for sample environment variables to run the application locally.

### You can add some prisoners into elastic with the following:-

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

If 48 records then mark complete
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

