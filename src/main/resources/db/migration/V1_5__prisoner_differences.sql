CREATE TABLE prisoner_differences (
    prisoner_differences_id uuid constraint prisoner_differences_pk PRIMARY KEY,
    noms_number varchar(7) not null,
    differences varchar not null,
    date_time   timestamp not null
);

CREATE INDEX prisoner_differences_noms_number_idx on prisoner_differences(date_time);
CREATE INDEX prisoner_differences_date_time_idx on prisoner_differences(noms_number);
