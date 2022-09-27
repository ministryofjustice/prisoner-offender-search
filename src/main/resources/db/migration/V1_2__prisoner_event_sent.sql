DELETE FROM prisoner_event_hashes;

ALTER TABLE prisoner_event_hashes ALTER COLUMN prisoner_hash TYPE varchar(24);
