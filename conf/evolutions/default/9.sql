# --- MapRoulette Scheme

# --- !Ups
-- Column to keep track of whether old tasks in the challenge should be
SELECT add_drop_column('challenges', 'last_updated', 'timestamp without time zone DEFAULT NOW()');;
-- Add new Column to Challenge to allow users to define the checkin comments for Challenges
SELECT add_drop_column('challenges', 'checkin_comment', 'character varying');;
UPDATE challenges SET checkin_comment = '#maproulette #' || replace(name, ' ', '_');;

CREATE TABLE IF NOT EXISTS task_comments
(
  id SERIAL NOT NULL PRIMARY KEY,
  osm_id integer NOT NULL,
  task_id integer NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  comment character varying,
  action_id integer null,
  CONSTRAINT task_comments_tasks_id_fkey FOREIGN KEY (task_id)
  REFERENCES tasks (id) MATCH SIMPLE
  ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT task_comments_actions_id_fkey FOREIGN KEY (action_id)
  REFERENCES actions (id) MATCH SIMPLE
  ON UPDATE CASCADE ON DELETE SET NULL
);;

-- update all the last updated values
DO $$
DECLARE
  rec RECORD;;
BEGIN
  FOR rec IN SELECT id FROM challenges LOOP
    UPDATE challenges SET last_updated = (SELECT MAX(modified)
            FROM tasks
            WHERE parent_id = rec.id)
    WHERE id = rec.id;;
  END LOOP;;
END$$;;
UPDATE challenges SET last_updated = NOW() WHERE last_updated IS NULL;;

# --- !Downs
SELECT add_drop_column('challenges', 'last_updated', '', false);;
SELECT add_drop_column('challenges', 'checkin_comment', '', false);;
DROP TABLE IF EXISTS task_comments;;
