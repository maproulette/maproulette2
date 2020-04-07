# --- MapRoulette Scheme

# --- !Ups
-- Add default_basemap_id column to challenges and users tables
SELECT add_drop_column('challenges', 'default_basemap_id', 'character varying');;
SELECT add_drop_column('users', 'default_basemap_id', 'character varying');;

# --- !Downs
SELECT add_drop_column('challenges', 'default_basemap_id', '', false);;
SELECT add_drop_column('users', 'default_basemap_id', '', false);;

