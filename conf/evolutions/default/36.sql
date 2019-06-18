# --- MapRoulette Scheme

# --- !Ups
-- Add timestamp to user_leaderboard

ALTER TABLE "user_leaderboard" ADD COLUMN created timestamp without time zone DEFAULT NOW();;


# --- !Downs
ALTER TABLE "user_leaderboard" DROP COLUMN created;;
