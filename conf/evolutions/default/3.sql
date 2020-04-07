# --- MapRoulette Scheme

# --- !Ups
DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'challenges' AND column_name = 'location';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('challenges', 'location', 4326, 'POINT', 2);;
  END IF;;
END$$;;

-- Updating all the locations for all the tasks in the system. This process takes about 2 minutes or
-- so depending on the amount of tasks and geometries in the system
DO $$
DECLARE
	rec RECORD;;
BEGIN
	FOR rec IN SELECT task_id, ST_Centroid(ST_Collect(ST_Makevalid(geom))) AS location
              FROM task_geometries tg
              GROUP BY task_id LOOP
		UPDATE tasks SET location = rec.location WHERE tasks.id = rec.task_id;;
	END LOOP;;
END$$;;

-- Update all the challenge locations based on the locations of their respective tasks. This process
-- is fairly quick due to the update of the tasks in the previous statement
DO $$
DECLARE
  rec RECORD;;
BEGIN
  FOR rec IN SELECT id FROM challenges LOOP
	  UPDATE challenges SET location = (SELECT ST_Centroid(ST_Collect(ST_Makevalid(location)))
						FROM tasks
						WHERE parent_id = rec.id)
	  WHERE id = rec.id;;
  END LOOP;;
END$$;;


-- Modifying this function so that if you send in the default status it ignores it and updates it with the current status of the task.
-- This is done primarily because the only way that a existing task should have be reset to the default status (CREATED) is if the
-- task is being uploaded as part of a scheduled upload and it is past the set time that defines that this task should be rechecked
CREATE OR REPLACE FUNCTION update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, task_priority integer DEFAULT 0, reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  update_id integer;;
  update_modified timestamp without time zone;;
  update_status integer;;
  new_status integer;;
BEGIN
  IF (SELECT task_id) = -1 THEN
    SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE name = task_name AND parent_id = task_parent_id;;
  ELSE
    SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE id = task_id;;
  END IF;;
  IF task_status = 0 THEN
    task_status = update_status;;
  END IF;;
  new_status := task_status;;
  IF update_status = task_status AND (SELECT AGE(NOW(), update_modified)) > reset_interval::INTERVAL THEN
    new_status := 0;;
  END IF;;
  UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority WHERE id = update_id;;
  RETURN update_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

# --- !Downs
DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'challenges' AND column_name = 'location';;
  IF FOUND THEN
    PERFORM DropGeometryColumn('challenges', 'location');;
  END IF;;
END$$;;

DROP FUNCTION IF EXISTS update_task(text,bigint,text,integer,bigint,integer,text);;
CREATE OR REPLACE FUNCTION update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, task_priority integer DEFAULT 0, reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  update_id integer;;
  update_modified timestamp without time zone;;
  update_status integer;;
  new_status integer;;
BEGIN
  IF (SELECT task_id) = -1 THEN
    SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE name = task_name AND parent_id = task_parent_id;;
  ELSE
    SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE id = task_id;;
  END IF;;
  new_status := task_status;;
  IF update_status = task_status AND (SELECT AGE(NOW(), update_modified)) > reset_interval::INTERVAL THEN
    new_status := 0;;
  END IF;;
  UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority WHERE id = update_id;;
  RETURN update_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;
