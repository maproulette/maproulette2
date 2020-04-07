# --- MapRoulette Scheme

# --- !Ups
-- Add checkin_source column to challenges table
SELECT add_drop_column('challenges', 'checkin_source', 'character varying');;

# --- !Downs
SELECT add_drop_column('challenges', 'checkin_source', '', false);;
