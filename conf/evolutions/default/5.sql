# --- MapRoulette Scheme

# --- !Ups
-- Update users table for user settings
SELECT add_drop_column('users', 'default_editor', 'integer DEFAULT -1');;
SELECT add_drop_column('users', 'default_basemap', 'integer DEFAULT -1');;
SELECT add_drop_column('users', 'custom_basemap_url', 'character varying');;
SELECT add_drop_column('users', 'email_opt_in', 'boolean DEFAULT false');;
SELECT add_drop_column('users', 'locale', 'character varying');;
SELECT add_drop_column('users', 'theme', '', false);;
SELECT add_drop_column('users', 'theme', 'integer DEFAULT -1');;

SELECT add_drop_column('challenges', 'default_zoom', 'integer DEFAULT 13');;
SELECT add_drop_column('challenges', 'min_zoom', 'integer DEFAULT 1');;
SELECT add_drop_column('challenges', 'max_zoom', 'integer DEFAULT 19');;
SELECT add_drop_column('challenges', 'default_basemap', 'integer');;
SELECT add_drop_column('challenges', 'custom_basemap', 'character varying');;

-- Table for all challenges, which is a child of Project, Surveys are also stored in this table
CREATE TABLE IF NOT EXISTS saved_challenges
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  user_id integer NOT NULL,
  challenge_id integer NOT NULL,
  CONSTRAINT saved_challenges_user_id FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT saved_challenges_challenge_id FOREIGN KEY (challenge_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;
SELECT create_index_if_not_exists('saved_challenges', 'user_id', '(user_id)');;
SELECT create_index_if_not_exists('saved_challenges', 'user_id_challenge_id', '(user_id, challenge_id)', true);;

# --- !Downs
SELECT add_drop_column('users', 'default_editor', '', false);;
SELECT add_drop_column('users', 'default_basemap', '', false);;
SELECT add_drop_column('users', 'custom_basemap_url', '', false);;
SELECT add_drop_column('users', 'email_opt_in', '', false);;
SELECT add_drop_column('users', 'locale', '', false);;
SELECT add_drop_column('users', 'theme', '', false);;
SELECT add_drop_column('users', 'theme', 'character varying DEFAULT(''skin-blue'')');;

SELECT add_drop_column('challenges', 'default_zoom', '', false);;
SELECT add_drop_column('challenges', 'min_zoom', '', false);;
SELECT add_drop_column('challenges', 'max_zoom', '', false);;
SELECT add_drop_column('challenges', 'default_basemap', '', false);;
SELECT add_drop_column('challenges', 'custom_basemap', '', false);;

DROP TABLE IF EXISTS saved_challenges;;
