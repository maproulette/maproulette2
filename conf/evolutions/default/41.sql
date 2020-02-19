# --- MapRoulette Scheme

# --- !Ups
-- Add suggested_fix for tasks
ALTER TABLE challenges ADD COLUMN has_suggested_fixes boolean DEFAULT false;;

# --- !Downs
ALTER TABLE challenges DROP COLUMN has_suggested_fixes;;
