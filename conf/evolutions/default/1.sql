# --- MapRoulette Scheme

# --- !Ups
-- Function that simply creates an index if it doesn't already exist
CREATE OR REPLACE FUNCTION create_index_if_not_exists(t_name text, i_name text, index_sql text, unq boolean default false) RETURNS void as $$
DECLARE
  full_index_name varchar;;
  schema_name varchar;;
  unqValue varchar;;
BEGIN
  full_index_name = 'idx_' || t_name || '_' || i_name;;
  schema_name = 'public';;
  unqValue = '';;
  IF unq THEN
    unqValue = 'UNIQUE ';;
  END IF;;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relname = full_index_name
    AND n.nspname = schema_name
  ) THEN
    execute 'CREATE ' || unqValue || 'INDEX ' || full_index_name || ' ON ' || schema_name || '.' || t_name || ' ' || index_sql;;
  END IF;;
END
$$
LANGUAGE plpgsql VOLATILE;;

-- Function that is used by a trigger to updated the modified column in the table
CREATE OR REPLACE FUNCTION update_modified() RETURNS TRIGGER AS $$
BEGIN
  NEW.modified = NOW();;
  RETURN NEW;;
END
$$
LANGUAGE plpgsql VOLATILE;;

-- Function to remove locks when a user is deleted
CREATE OR REPLACE FUNCTION on_user_delete() RETURNS TRIGGER AS $$
BEGIN
  DELETE FROM user_groups WHERE osm_user_id = old.osm_id;;
  DELETE FROM locked WHERE user_id = old.id;;
  RETURN old;;
END
$$
LANGUAGE plpgsql VOLATILE;;

-- MapRoulette uses postgis extension for all it's geometries
CREATE EXTENSION IF NOT EXISTS postgis;;
-- MapRoulette uses hstore for the properties of all it's geometries
CREATE EXTENSION IF NOT EXISTS HSTORE;;

-- The user table contains all users that have logged into MapRoulette.
CREATE TABLE IF NOT EXISTS users
(
  id SERIAL NOT NULL PRIMARY KEY,
  osm_id integer NOT NULL UNIQUE,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  osm_created timestamp without time zone NOT NULL,
  name character varying NOT NULL,
  description character varying,
  avatar_url character varying,
  api_key character varying UNIQUE,
  oauth_token character varying NOT NULL,
  oauth_secret character varying NOT NULL,
  theme character varying DEFAULT('skin-blue')
);;

DROP TRIGGER IF EXISTS on_user_delete ON users;;
CREATE TRIGGER on_user_delete BEFORE DELETE ON users
  FOR EACH ROW EXECUTE PROCEDURE on_user_delete();;

DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'home_location';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('users', 'home_location', 4326, 'POINT', 2);;
  END IF;;
END $$;;

DROP TRIGGER IF EXISTS update_users_modified ON users;;
CREATE TRIGGER update_users_modified BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;

-- Top level object that contains all challenges and surveys
CREATE TABLE IF NOT EXISTS projects
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  name character varying NOT NULL,
  description character varying DEFAULT '',
  enabled BOOLEAN DEFAULT(true)
);;

SELECT create_index_if_not_exists('projects', 'name', '(lower(name))', true);;

DROP TRIGGER IF EXISTS update_projects_modified ON projects;;
CREATE TRIGGER update_projects_modified BEFORE UPDATE ON projects
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;

-- Groups for user role management
CREATE TABLE IF NOT EXISTS groups
(
  id SERIAL NOT NULL PRIMARY KEY,
  project_id integer NOT NULL,
  name character varying NOT NULL,
  group_type integer NOT NULL,
  CONSTRAINT groups_project_id_fkey FOREIGN KEY (project_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED
);;

SELECT create_index_if_not_exists('groups', 'name', '(lower(name))', true);;

-- Table to map users to groups
CREATE TABLE IF NOT EXISTS user_groups
(
  id SERIAL NOT NULL PRIMARY KEY,
  osm_user_id integer NOT NULL,
  group_id integer NOT NULL,
  CONSTRAINT ug_user_id_fkey FOREIGN KEY (osm_user_id)
    REFERENCES users(osm_id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED,
  CONSTRAINT ug_group_id_fkey FOREIGN KEY (group_id)
    REFERENCES groups(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('user_groups', 'osm_user_id_group_id', '(osm_user_id, group_id)', true);;

-- Table for all challenges, which is a child of Project, Surveys are also stored in this table
CREATE TABLE IF NOT EXISTS challenges
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  name character varying NOT NULL,
  parent_id integer NOT NULL,
  description character varying DEFAULT '',
  blurb character varying DEFAULT '',
  instruction character varying DEFAULT '',
  difficulty integer DEFAULT 1,
  enabled BOOLEAN DEFAULT(true),
  challenge_type integer NOT NULL DEFAULT(1),
  featured BOOLEAN DEFAULT(false),
  overpass_ql character varying DEFAULT '',
  remote_geo_json CHARACTER VARYING DEFAULT '',
  status integer DEFAULT 0,
  CONSTRAINT challenges_parent_id_fkey FOREIGN KEY (parent_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

DROP TRIGGER IF EXISTS update_challenges_modified ON challenges;;
CREATE TRIGGER update_challenges_modified BEFORE UPDATE ON challenges
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;

SELECT create_index_if_not_exists('challenges', 'parent_id', '(parent_id)');;
SELECT create_index_if_not_exists('challenges', 'parent_id_name', '(parent_id, lower(name))', true);;

-- All the answers for a specific survey
CREATE TABLE IF NOT EXISTS answers
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  survey_id integer NOT NULL,
  answer character varying NOT NULL,
  CONSTRAINT answers_survey_id_fkey FOREIGN KEY (survey_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED
);;

DROP TRIGGER IF EXISTS update_answers_modified ON answers;;
CREATE TRIGGER update_answers_modified BEFORE UPDATE ON answers
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;

SELECT create_index_if_not_exists('answers', 'survey_id', '(survey_id)');;

-- All the tasks for a specific challenge or survey
CREATE TABLE IF NOT EXISTS tasks
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  name character varying NOT NULL,
  instruction character varying NOT NULL,
  parent_id integer NOT NULL,
  status integer DEFAULT 0 NOT NULL,
  CONSTRAINT tasks_parent_id_fkey FOREIGN KEY (parent_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

DROP TRIGGER IF EXISTS update_tasks_modified ON tasks;;
CREATE TRIGGER update_tasks_modified BEFORE UPDATE ON tasks
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;

DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'tasks' AND column_name = 'location';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('tasks', 'location', 4326, 'POINT', 2);;
  END IF;;
END$$;;

SELECT create_index_if_not_exists('tasks', 'parent_id', '(parent_id)');;
SELECT create_index_if_not_exists('tasks', 'parent_id_name', '(parent_id, lower(name))', true);;

-- The answers for a survey from a user
CREATE TABLE IF NOT EXISTS survey_answers
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  osm_user_id integer NOT NULL,
  project_id integer NOT NULL,
  survey_id integer NOT NULL,
  task_id integer NOT NULL,
  answer_id integer NOT NULL,
  CONSTRAINT survey_answers_project_id_fkey FOREIGN KEY (project_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT survey_answers_survey_id_fkey FOREIGN KEY (survey_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT survey_answers_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT survey_answers_answer_id_fkey FOREIGN KEY (answer_id)
    REFERENCES answers(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('survey_answers', 'survey_id', '(survey_id)');;

-- The tags that can be applied to a task
CREATE TABLE IF NOT EXISTS tags
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  name character varying NOT NULL,
  description character varying DEFAULT ''
);;
-- index has the potentially to slow down inserts badly
SELECT create_index_if_not_exists('tags', 'name', '(lower(name))', true);;

-- The tags associated with challenges
CREATE TABLE IF NOT EXISTS tags_on_challenges
(
  id SERIAL NOT NULL PRIMARY KEY,
  challenge_id INTEGER NOT NULL,
  tag_id INTEGER NOT NULL,
  CONSTRAINT challenges_tags_on_challenges_id_fkey FOREIGN KEY (challenge_id)
  REFERENCES challenges (id) MATCH SIMPLE
  ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT tags_tags_on_challenges_id_fkey FOREIGN KEY (tag_id)
  REFERENCES tags (id) MATCH SIMPLE
  ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('tags_on_challenges', 'challenge_id', '(challenge_id)');;
SELECT create_index_if_not_exists('tags_on_challenges', 'tag_id', '(tag_id)');;
-- This index could slow down inserts pretty badly
SELECT create_index_if_not_exists('tags_on_challenges', 'challenge_id_tag_id', '(challenge_id, tag_id)');;

-- The tags associated with a task
CREATE TABLE IF NOT EXISTS tags_on_tasks
(
  id SERIAL NOT NULL PRIMARY KEY,
  task_id integer NOT NULL,
  tag_id integer NOT NULL,
  CONSTRAINT tasks_tags_on_tasks_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks (id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT tags_tags_on_tasks_tag_id_fkey FOREIGN KEY (tag_id)
    REFERENCES tags (id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('tags_on_tasks', 'task_id', '(task_id)');;
SELECT create_index_if_not_exists('tags_on_tasks', 'tag_id', '(tag_id)');;
-- This index could slow down inserts pretty badly
SELECT create_index_if_not_exists('tags_on_tasks', 'task_id_tag_id', '(task_id, tag_id)');;

-- Geometries for a specific task
CREATE TABLE IF NOT EXISTS task_geometries
(
  id SERIAL NOT NULL PRIMARY KEY,
  task_id integer NOT NULL,
  properties HSTORE,
  CONSTRAINT task_geometries_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks (id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED
);;

DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'task_geometries' AND column_name = 'geom';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('task_geometries', 'geom', 4326, 'GEOMETRY', 2);;
  END IF;;
END$$;;

CREATE INDEX IF NOT EXISTS idx_task_geometries_geom ON task_geometries USING GIST (geom);;
SELECT create_index_if_not_exists('task_geometries', 'task_id', '(task_id)');;

-- Actions that are taken in the system, like set the status of a task to 'fixed'
CREATE TABLE IF NOT EXISTS actions
(
  id serial NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  osm_user_id integer,
  type_id integer,
  item_id integer,
  action integer NOT NULL,
  status integer NOT NULL,
  extra character varying
);;

SELECT create_index_if_not_exists('actions', 'item_id', '(item_id)');;
SELECT create_index_if_not_exists('actions', 'osm_user_id', '(osm_user_id)');;
SELECT create_index_if_not_exists('actions', 'created', '(created)');;

-- This contains only the actions related to setting the status of a task
CREATE TABLE IF NOT EXISTS status_actions
(
  id serial NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  osm_user_id integer NOT NULL,
  project_id integer NOT NULL,
  challenge_id integer NOT NULL,
  task_id integer NOT NULL,
  old_status integer NOT NULL,
  status integer NOT NULL,
  CONSTRAINT status_actions_project_id_fkey FOREIGN KEY (project_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT status_actions_challenge_id_fkey FOREIGN KEY (challenge_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT status_actions_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('status_actions', 'challenge_id', '(challenge_id)');;
SELECT create_index_if_not_exists('status_actions', 'challenge_id_status', '(challenge_id,status)');;

-- Table handling locks for any of the objects
CREATE TABLE IF NOT EXISTS locked
(
  id serial NOT NULL PRIMARY KEY,
  locked_time timestamp without time zone DEFAULT NOW(),
  item_type integer NOT NULL,
  item_id integer NOT NULL,
  user_id integer NOT NULL,
  CONSTRAINT locked_users_user_id FOREIGN KEY (user_id)
    REFERENCES users(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('locked', 'item_type_item_id', '(item_type, item_id)', true);;

-- Creates or updates and task. Will also check if task status needs to be updated
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

-- Insert the default root, used for migration and those using the old API
INSERT INTO projects (id, name, description)
SELECT 0, 'SuperRootProject', 'Root Project for super users.' WHERE NOT EXISTS (
    SELECT id FROM projects WHERE id = 0
);;
INSERT INTO groups (id, project_id, name, group_type)
SELECT -999, 0, 'SUPERUSERS', -1 WHERE NOT EXISTS (
    SELECT id FROM groups WHERE id = -999
);;
INSERT INTO users(id, osm_id, osm_created, name, oauth_token, oauth_secret, theme)
SELECT -999, -999, NOW(), 'SuperUser', '', '', 0 WHERE NOT EXISTS (
    SELECT id FROM users WHERE id = -999
);;
INSERT INTO user_groups (osm_user_id, group_id)
SELECT -999, -999 WHERE NOT EXISTS (
    SELECT id FROM user_groups WHERE osm_user_id = -999 AND group_id = -999
);;

# --- !Downs
DROP TABLE IF EXISTS users CASCADE;;
DROP TABLE IF EXISTS projects CASCADE;;
DROP TABLE IF EXISTS groups CASCADE;;
DROP TABLE IF EXISTS user_groups CASCADE;;
DROP TABLE IF EXISTS challenges CASCADE;;
DROP TABLE IF EXISTS answers CASCADE;;
DROP TABLE IF EXISTS tasks CASCADE;;
DROP TABLE IF EXISTS survey_answers CASCADE;;
DROP TABLE IF EXISTS tags CASCADE;;
DROP TABLE IF EXISTS tags_on_challenges CASCADE;;
DROP TABLE IF EXISTS tags_on_tasks CASCADE;;
DROP TABLE IF EXISTS task_geometries CASCADE;;
DROP TABLE IF EXISTS actions CASCADE;;
DROP TABLE IF EXISTS status_actions CASCADE;;
DROP TABLE IF EXISTS locked CASCADE;;
DROP FUNCTION IF EXISTS create_update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint, reset_interval text);;
DROP FUNCTION IF EXISTS update_task(task_name text, task_parent_id bigint, task_instruction text, task_status integer, task_id bigint, reset_interval text);;
DROP FUNCTION IF EXISTS create_index_if_not_exists(t_name text, i_name text, index_sql text, unq boolean);;
DROP FUNCTION IF EXISTS update_modified();;
DROP FUNCTION IF EXISTS on_user_delete();;
