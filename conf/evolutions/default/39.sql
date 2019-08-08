# --- MapRoulette Scheme

# --- !Ups
-- Column to keep track of whether changeset/checkin comments should have the
-- challenge URL appended to them.
SELECT add_drop_column('challenges', 'include_checkin_url', 'BOOLEAN DEFAULT(false)');

# --- !Downs
--SELECT add_drop_column('challenges', 'include_checkin_url', '', false);
