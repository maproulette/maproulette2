# --- MapRoulette Scheme

# --- !Ups
-- Add display name column for projects
SELECT add_drop_column('projects', 'display_name', 'character varying NULL');;
SELECT add_drop_column('projects', 'owner_id', 'integer NOT NULL DEFAULT -999');;
ALTER TABLE projects
  ADD CONSTRAINT projects_owner_id_fkey FOREIGN KEY (owner_id)
    REFERENCES users(osm_id) MATCH SIMPLE
    ON DELETE SET DEFAULT;;

ALTER TABLE ONLY challenges ALTER COLUMN owner_id SET DEFAULT -999;;
UPDATE challenges SET owner_id = -999 WHERE owner_id = -1;;
ALTER TABLE challenges
  ADD CONSTRAINT challenges_owner_id_fkey FOREIGN KEY (owner_id)
    REFERENCES users(osm_id) MATCH SIMPLE
    ON DELETE SET DEFAULT;;

-- update all display name columns to be the name of the user followed by the project
-- Also update the owner_id to be the actual owner of the project
DO $$
DECLARE
  rec RECORD;;
  owner INT;;
  display VARCHAR;;
BEGIN
  FOR rec IN SELECT id, name FROM projects LOOP
    SELECT NULL INTO display;;
    SELECT NULL INTO owner;;
    IF rec.name LIKE 'Home_%' THEN
      SELECT osm_id FROM users WHERE osm_id = (SELECT REPLACE(rec.name, 'Home_', '')::INT) INTO owner;;
      SELECT name || '''s Project' FROM users WHERE osm_id = owner INTO display;;
    ELSE
      SELECT owner_id FROM challenges WHERE parent_id = rec.id LIMIT 1 INTO owner;;
    END IF;;
    IF owner IS NULL THEN
      SELECT -999 INTO owner;;
    END IF;;
    IF display IS NULL THEN
      SELECT rec.name INTO display;;
    END IF;;
    UPDATE projects SET
      owner_id = owner,
      display_name = display
    WHERE id = rec.id;;
  END LOOP;;
END$$;;


-- Add new bounding box that captures the bounding box for all the tasks within the current challenge
DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'challenges' AND column_name = 'bounding';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('challenges', 'bounding', 4326, 'POLYGON', 2);;
  END IF;;
END$$;;
-- Add spatial index for challenge location
CREATE INDEX IF NOT EXISTS idx_challenges_location ON challenges USING GIST (location);;
-- Add spatial index for challenge bounding box
CREATE INDEX IF NOT EXISTS idx_challenges_bounding ON challenges USING GIST (bounding);;

-- Table for all challenges, which is a child of Project, Surveys are also stored in this table
CREATE TABLE IF NOT EXISTS saved_tasks
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  user_id integer NOT NULL,
  task_id integer NOT NULL,
  challenge_id integer NOT NULL,
  CONSTRAINT saved_tasks_user_id FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT saved_tasks_task_id FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT saved_tasks_challenge_id FOREIGN KEY (challenge_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;
SELECT create_index_if_not_exists('saved_tasks', 'user_id', '(user_id)');;
SELECT create_index_if_not_exists('saved_tasks', 'user_id_task_id', '(user_id, task_id)', true);;
SELECT create_index_if_not_exists('saved_tasks', 'user_id_challenge_id', '(user_id, challenge_id)');;

-- Create trigger on the tasks that if anything changes it updates the challenge modified date
-- Function that is used by a trigger to updated the modified column in the table
CREATE OR REPLACE FUNCTION update_tasks() RETURNS TRIGGER AS $$
DECLARE
  task RECORD;;
BEGIN
  IF TG_OP='DELETE' THEN
    task = OLD;;
  ELSE
 	NEW.modified = NOW();;
    task = NEW;;
  END IF;;
  UPDATE challenges SET modified = NOW() WHERE id = task.parent_id;;
  RETURN task;;
END
$$
LANGUAGE plpgsql VOLATILE;;
DROP TRIGGER IF EXISTS update_tasks_modified ON tasks;;
DROP TRIGGER IF EXISTS update_tasks_trigger ON tasks;;
CREATE TRIGGER update_tasks_trigger BEFORE UPDATE OR INSERT OR DELETE ON tasks
  FOR EACH ROW EXECUTE PROCEDURE update_tasks();;

-- Update the bounding box for all challenges
DO $$
DECLARE
  rec RECORD;;
BEGIN
  FOR rec IN SELECT id FROM challenges LOOP
    BEGIN
      UPDATE challenges SET bounding = (SELECT ST_Envelope(ST_Buffer((ST_SetSRID(ST_Extent(location), 4326))::geography,2)::geometry)
        FROM tasks
        WHERE parent_id = rec.id)
      WHERE id = rec.id;;
    EXCEPTION WHEN SQLSTATE 'XX000' THEN
      RAISE NOTICE 'Failed to create bounding for challenge %', rec.id;;
    END;;
  END LOOP;;
END$$;;

# --- !Downs
SELECT add_drop_column('projects', 'display_name', '', false);;
SELECT add_drop_column('projects', 'owner_id', '', false);;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_owner_id_fkey;;
ALTER TABLE challenges DROP COLUMN bounding;;
DROP TABLE saved_tasks;;
DROP TRIGGER IF EXISTS update_tasks_trigger ON tasks;;
DROP FUNCTION update_tasks();;
CREATE TRIGGER update_tasks_modified BEFORE UPDATE ON tasks
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;
