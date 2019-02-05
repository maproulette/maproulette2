# --- MapRoulette Scheme

# --- !Ups
-- Add country_code column to user_leaderboard
ALTER TABLE "user_leaderboard" ADD COLUMN country_code character varying NULL;;

-- Add country_code column to user_top_challenges
ALTER TABLE "user_top_challenges" ADD COLUMN country_code character varying NULL;;

-- Geometries for a specific task
CREATE TABLE IF NOT EXISTS task_suggested_fix
(
  id SERIAL NOT NULL PRIMARY KEY,
  task_id integer NOT NULL,
  properties HSTORE,
  CONSTRAINT task_suggested_fix_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks (id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED
);;

DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'task_suggested_fix' AND column_name = 'geom';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('task_suggested_fix', 'geom', 4326, 'GEOMETRY', 2);;
  END IF;;
END$$;;

CREATE INDEX IF NOT EXISTS idx_task_geometries_geom ON task_suggested_fix USING GIST (geom);;
SELECT create_index_if_not_exists('task_suggested_fix', 'task_id', '(task_id)');;

# --- !Downs
ALTER TABLE "user_leaderboard" DROP COLUMN country_code;;
ALTER TABLE "user_top_challenges" DROP COLUMN country_code;;
--DROP TABLE task_suggested_fix
