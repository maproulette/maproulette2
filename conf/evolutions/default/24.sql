# --- MapRoulette Scheme

# --- !Ups
-- New table for virtual challenges
CREATE TABLE IF NOT EXISTS user_leaderboard
(
  month_duration integer NOT NULL,
  user_id integer NOT NULL,
  user_name character varying NULL,
  user_avatar_url character varying NULL,
  user_ranking integer NOT NULL,
  user_score integer NOT NULL
);;

CREATE TABLE IF NOT EXISTS user_top_challenges
(
  month_duration integer NOT NULL,
  user_id integer NOT NULL,
  challenge_id integer NOT NULL,
  challenge_name character varying NULL,
  activity integer NOT NULL
);;


# --- !Downs
--DROP TABLE IF EXISTS user_leaderboard;;
--DROP TABLE IF EXISTS user_top_challenges;;
