# --- MapRoulette Scheme

# --- !Ups
-- Add leaderboard_opt_out column to users table
SELECT add_drop_column('users', 'leaderboard_opt_out', 'boolean DEFAULT FALSE');;

# --- !Downs
SELECT add_drop_column('users', 'leaderboard_opt_out', '', false);;
