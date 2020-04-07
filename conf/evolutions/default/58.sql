# --- !Ups
-- Add requires_local.
SELECT add_drop_column('challenges', 'requires_local', 'BOOLEAN DEFAULT FALSE');;

# --- !Downs
SELECT add_drop_column('challenges', 'requires_local', '', false);;
