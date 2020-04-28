    {{/* vim: set filetype=mustache: */}}
{{/*
Environment variables for web and worker containers
*/}}
{{- define "deployment.envs" }}
env:
  - name: SERVER_PORT
    value: "{{ .Values.image.port }}"

  - name: JAVA_OPTS
    value: "{{ .Values.env.JAVA_OPTS }}"

  - name: SPRING_PROFILES_ACTIVE
    value: "logstash"

  - name: API_BASE_URL_OAUTH
    value: "{{ .Values.env.API_BASE_URL_OAUTH }}"

  - name: API_BASE_URL_NOMIS
    value: "{{ .Values.env.API_BASE_URL_NOMIS }}"

  - name: APPLICATION_INSIGHTS_IKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: OAUTH_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: PRISONER_OFFENDER_SEARCH_CLIENT_ID

  - name: OAUTH_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: PRISONER_OFFENDER_SEARCH_SECRET

  - name: SQS_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: pos-sqs-instance-output
        key: access_key_id

  - name: SQS_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: pos-sqs-instance-output
        key: secret_access_key

  - name: SQS_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: pos-sqs-instance-output
        key: sqs_pos_name

  - name: SQS_AWS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: pos-sqs-dl-instance-output
        key: access_key_id

  - name: SQS_AWS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: pos-sqs-dl-instance-output
        key: secret_access_key

  - name: SQS_DLQ_URL
    valueFrom:
      secretKeyRef:
        name: pos-sqs-dl-instance-output
        key: sqs_pos_name

  - name: SQS_INDEX_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: pos-idx-sqs-instance-output
        key: access_key_id

  - name: SQS_INDEX_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: pos-idx-sqs-instance-output
        key: secret_access_key

  - name: SQS_INDEX_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: pos-idx-sqs-instance-output
        key: sqs_pos_name

  - name: SQS_INDEX_AWS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: pos-idx-sqs-dl-instance-output
        key: access_key_id

  - name: SQS_INDEX_AWS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: pos-idx-sqs-dl-instance-output
        key: secret_access_key

  - name: SQS_INDEX_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: pos-idx-sqs-dl-instance-output
        key: sqs_pos_name

{{- end -}}
