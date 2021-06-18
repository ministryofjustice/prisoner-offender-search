#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2
aws --endpoint-url=http://localhost:4566 sns create-topic --name offender_events

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_search_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_search_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/prisoner_offender_search_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:prisoner_offender_search_dlq\"}"}'

aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_index_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name prisoner_offender_index_queue
aws --endpoint-url=http://localhost:4566 sqs set-queue-attributes --queue-url "http://localhost:4566/queue/prisoner_offender_index_queue" --attributes '{"RedrivePolicy":"{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:prisoner_offender_index_dlq\"}"}'

aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/prisoner_offender_search_queue \
    --attributes '{"FilterPolicy":"{\"eventType\":[ \"OFFENDER-INSERTED\", \"OFFENDER-UPDATED\", \"OFFENDER-DELETED\", \"EXTERNAL_MOVEMENT_RECORD-INSERTED\", \"ASSESSMENT-CHANGED\", \"OFFENDER_BOOKING-REASSIGNED\", \"OFFENDER_BOOKING-CHANGED\", \"OFFENDER_DETAILS-CHANGED\", \"BOOKING_NUMBER-CHANGED\", \"SENTENCE_DATES-CHANGED\", \"IMPRISONMENT_STATUS-CHANGED\", \"BED_ASSIGNMENT_HISTORY-INSERTED\", \"DATA_COMPLIANCE_DELETE-OFFENDER\", \"CONFIRMED_RELEASE_DATE-CHANGED\", \"OFFENDER_ALIAS-CHANGED\", \"OFFENDER_PROFILE_DETAILS-INSERTED\", \"OFFENDER_PROFILE_DETAILS-UPDATED\"] }"}'
echo "Topics and queues created, now wait for elasticsearch to start before connecting"
