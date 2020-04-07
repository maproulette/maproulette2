# --- MapRoulette Scheme

# --- !Ups
-- Column to keep track of whether old tasks in the challenge should be
SELECT add_drop_column('challenges', 'updateTasks', 'BOOLEAN DEFAULT(false)');;

# --- !Downs
SELECT add_drop_column('challenges', 'updateTasks', '', false);;
