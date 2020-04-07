# --- MapRoulette Scheme

# --- !Ups
-- Column to keep track of whether old tasks in the challenge should be
SELECT add_drop_column('challenges', 'owner_id', 'integer NOT NULL DEFAULT -1');;

# --- !Downs
SELECT add_drop_column('challenges', 'owner_id', '', false);;
