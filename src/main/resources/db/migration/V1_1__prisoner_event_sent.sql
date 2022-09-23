ALTER TABLE prisoner_event_sent RENAME TO prisoner_event_hashes;

ALTER TABLE prisoner_event_hashes RENAME COLUMN hashcode to prisoner_hash;
ALTER TABLE prisoner_event_hashes RENAME COLUMN date_time to updated_date_time;