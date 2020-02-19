# --- MapRoulette Scheme

# --- !Ups
-- Add completion_responses for tasks
ALTER TABLE tasks ADD COLUMN completion_responses jsonb;;
ALTER TABLE challenges ADD COLUMN exportable_properties text;;

# --- !Downs
ALTER TABLE tasks DROP COLUMN completion_responses;;
ALTER TABLE challenges DROP COLUMN exportable_properties;;
