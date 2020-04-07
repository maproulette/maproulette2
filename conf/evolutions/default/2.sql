# --- MapRoulette Scheme

# --- !Ups
-- Helper function to add/drop columns safely
CREATE OR REPLACE FUNCTION add_drop_column(tablename varchar, colname varchar, coltype varchar, addcolumn boolean default true) RETURNS varchar AS $$
DECLARE
  col_name varchar;;
BEGIN
  EXECUTE 'SELECT column_name FROM information_schema.columns WHERE table_name =' || quote_literal(tablename) || ' and column_name = ' || quote_literal(colname) into col_name;;
  RAISE INFO ' the val : % ', col_name;;
  IF (col_name IS NULL AND addcolumn) THEN
    EXECUTE 'ALTER TABLE IF EXISTS ' || tablename || ' ADD COLUMN ' || colname || ' ' || coltype;;
  ELSEIF (col_name IS NOT NULL AND NOT addcolumn) THEN
    EXECUTE 'ALTER TABLE IF EXISTS ' || tablename || ' DROP COLUMN ' || colname;;
  END IF;;
  RETURN col_name;;
END
$$
LANGUAGE plpgsql VOLATILE;;

-- adding new column for task to set it in priority 1 (HIGH), 2 (MEDIUM) or 3 (LOW), defaults to 1
SELECT add_drop_column('tasks', 'priority', 'integer DEFAULT 0');;
-- enabled defaults to false now
ALTER TABLE IF EXISTS projects ALTER COLUMN enabled SET DEFAULT false;;
ALTER TABLE IF EXISTS challenges ALTER COLUMN enabled SET DEFAULT false;;
-- New options for challenges
SELECT add_drop_column('challenges', 'default_priority', 'integer DEFAULT 0');;
SELECT add_drop_column('challenges', 'high_priority_rule', 'character varying');;
SELECT add_drop_column('challenges', 'medium_priority_rule', 'character varying');;
SELECT add_drop_column('challenges', 'low_priority_rule', 'character varying');;
SELECT add_drop_column('challenges', 'extra_options', 'HSTORE');;

-- DROP the old create_update_task function
DROP FUNCTION IF EXISTS create_update_task(text,bigint,text,integer,bigint,text);;
-- Creates or updates and task. Will also check if task status needs to be updated
-- This change simply adds the priority
CREATE OR REPLACE FUNCTION create_update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, task_priority integer DEFAULT 0, reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  return_id integer;;
BEGIN
  return_id := task_id;;
  IF (SELECT task_id) = -1 THEN
    BEGIN
      INSERT INTO tasks (name, parent_id, instruction, priority) VALUES (task_name, task_parent_id, task_instruction, task_priority) RETURNING id INTO return_id;;
      EXCEPTION WHEN UNIQUE_VIOLATION THEN
      SELECT INTO return_id update_task(task_name, task_parent_id, task_instruction, task_status, task_id, task_priority, reset_interval);;
    END;;
  ELSE
    PERFORM update_task(task_name, task_parent_id, task_instruction, task_status, task_id, task_priority, reset_interval);;
  END IF;;
  RETURN return_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

DROP FUNCTION IF EXISTS update_task(text,bigint,text,integer,bigint,text);;
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

# --- !Downs
SELECT add_drop_column('tasks', 'priority', '', false);;
ALTER TABLE IF EXISTS projects ALTER COLUMN enabled SET DEFAULT true;;
ALTER TABLE IF EXISTS challenges ALTER COLUMN enabled SET DEFAULT true;;
SELECT add_drop_column('challenges', 'default_priority', '', false);;
SELECT add_drop_column('challenges', 'high_priority_rule', '', false);;
SELECT add_drop_column('challenges', 'medium_priority_rule', '', false);;
SELECT add_drop_column('challenges', 'low_priority_rule', '', false);;
SELECT add_drop_column('challenges', 'extra_options', '', false);;
DROP FUNCTION IF EXISTS add_drop_column(varchar,varchar,varchar,boolean);;

-- Creates or updates and task. Will also check if task status needs to be updated
DROP FUNCTION IF EXISTS create_update_task(text,bigint,text,integer,bigint,integer,text);;
CREATE OR REPLACE FUNCTION create_update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, reset_interval text DEFAULT '7 days') RETURNS integer as $$
DECLARE
  return_id integer;;
BEGIN
  return_id := task_id;;
  IF (SELECT task_id) = -1 THEN
    BEGIN
      INSERT INTO tasks (name, parent_id, instruction) VALUES (task_name, task_parent_id, task_instruction) RETURNING id INTO return_id;;
      EXCEPTION WHEN UNIQUE_VIOLATION THEN
      SELECT INTO return_id update_task(task_name, task_parent_id, task_instruction, task_status, task_id, reset_interval);;
    END;;
  ELSE
    PERFORM update_task(task_name, task_parent_id, task_instruction, task_status, task_id, reset_interval);;
  END IF;;
  RETURN return_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;

DROP FUNCTION IF EXISTS update_task(text,bigint,text,integer,bigint,integer,text);;
CREATE OR REPLACE FUNCTION update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint DEFAULT -1, reset_interval text DEFAULT '7 days') RETURNS integer as $$
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
  UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status WHERE id = update_id;;
  RETURN update_id;;
END
$$
LANGUAGE plpgsql VOLATILE;;
