# --- !Ups
-- Add preferred_tags to challenges.
ALTER TABLE IF EXISTS challenges ADD COLUMN preferred_tags VARCHAR;;

# --- !Downs
ALTER TABLE IF EXISTS challenges DROP COLUMN preferred_tags;;
