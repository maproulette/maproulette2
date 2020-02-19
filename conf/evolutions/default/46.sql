# --- MapRoulette Scheme

# --- !Ups

-- To allow us to know how long a lock has been in existence once we allow lock
-- times to be extended, add a created column to the "locked" table and update
-- existing locks to match the creation timestamp with their current
-- locked_time
ALTER TABLE "locked" ADD COLUMN created timestamp without time zone DEFAULT NOW();;
UPDATE locked set created=locked_time;

# --- !Downs
ALTER TABLE "locked" DROP COLUMN created;;
