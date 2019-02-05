# --- MapRoulette Scheme

# --- !Ups
-- Add country_code column to user_leaderboard
ALTER TABLE "user_leaderboard" ADD COLUMN country_code character varying NULL;;

-- Add country_code column to user_top_challenges
ALTER TABLE "user_top_challenges" ADD COLUMN country_code character varying NULL;;

# --- !Downs
ALTER TABLE "user_leaderboard" DROP COLUMN country_code;;
ALTER TABLE "user_top_challenges" DROP COLUMN country_code;;
