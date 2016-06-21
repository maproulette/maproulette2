# --- Map Roulette Scheme

# --- !Ups
SELECT AddGeometryColumn('challenges', 'location', 4326, 'POINT', 2);

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
