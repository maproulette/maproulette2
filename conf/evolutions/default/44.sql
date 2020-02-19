# --- MapRoulette Scheme

# --- !Ups
ALTER TABLE challenges ADD COLUMN data_origin_date timestamp without time zone;;

UPDATE challenges set data_origin_date = last_task_refresh;

# --- !Downs
ALTER TABLE challenges DROP COLUMN data_origin_date;;
