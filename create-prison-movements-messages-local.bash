#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4575 sns publish \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --message-attributes '{"eventType" : { "DataType":"String", "StringValue":"EXTERNAL_MOVEMENT_RECORD-INSERTED"}}' \
    --message '{"eventType":"EXTERNAL_MOVEMENT_RECORD-INSERTED","eventDatetime":"2020-01-13T11:33:23.790725","bookingId":-1,"movementSeq":1,"offenderIdDisplay":"A1234AA","fromAgencyLocationId":"SHEFCRT","toAgencyLocationId":"LEI","directionCode":"IN","movementType":"ADM","fromAgencyLocationId":"SHEFCRT","toAgencyLocationId":"LEI","directionCode":"IN","movementType":"ADM","nomisEventType":"M1_RESULT"}'
