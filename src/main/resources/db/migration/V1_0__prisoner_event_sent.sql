CREATE TABLE prisoner_event_sent(
    noms_number varchar(7) constraint prisoner_event_sent_pk PRIMARY KEY,
    hashcode integer,
    date_time timestamp
);