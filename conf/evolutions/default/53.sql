# --- !Ups
-- We need to cleanup some of the task_review data as there was a bug where
-- resetting tasks back to created left entries in task_review

-- Delete rows where review_status is NULL
DELETE FROM tasks
  WHERE id IN (SELECT task_id FROM task_review WHERE review_status IS NULL);;

-- Delete rows in task_review that have no corresponding entries in task table
DELETE FROM task_review
  WHERE task_id NOT IN (SELECT id FROM tasks WHERE task_id = tasks.id);;

-- Delete rows in task_review where the task is in the created status.
DELETE FROM task_review
  WHERE task_id IN (SELECT id FROM tasks where status = 0);;

-- Add a constraint so when a task is deleted the task_review entry is as well
ALTER TABLE task_review
   ADD CONSTRAINT task_task_review_cascade
   FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;;


-- Creates or updates a task. Will also check if task status needs to be updated
-- This change is for when a task is automatically updated back to a created status it
-- will also remove it's entry in the task_review table.
CREATE OR REPLACE FUNCTION update_task(task_name text,
                                         task_parent_id bigint,
                                         task_instruction text,
                                         task_status integer,
                                         geo_json jsonb,
                                         suggestedfix jsonb DEFAULT NULL,
                                         task_id bigint DEFAULT -1,
                                         task_priority integer DEFAULT 0,
                                         task_changeset_id bigint DEFAULT -1,
                                         reset_interval text DEFAULT '7 days',
                                         task_mapped_on timestamp DEFAULT NULL,
                                         task_review_status integer DEFAULT NULL,
                                         task_review_requested_by integer DEFAULT NULL,
                                         task_reviewed_by integer DEFAULT NULL,
                                         task_reviewed_at timestamp DEFAULT NULL
                                       ) RETURNS integer as $$
  DECLARE
    update_id integer;;
    update_modified timestamp without time zone;;
    update_status integer;;
    new_status integer;;
    geojson_geom geometry;;
  BEGIN
    IF (SELECT task_id) = -1 THEN
      SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE name = task_name AND parent_id = task_parent_id;;
    ELSE
      SELECT id, modified, status INTO update_id, update_modified, update_status FROM tasks WHERE id = task_id;;
    END IF;;
    new_status := task_status;;
    -- only reset the status if the task is not currently disabled or set as a false positive and all other criteria is met.
    IF update_status = task_status AND NOT (update_status = 9 OR update_status = 2) AND (SELECT AGE(NOW(), update_modified)) > reset_interval::INTERVAL THEN
      new_status := 0;;
    END IF;;
    -- Make sure we do not have a task_review entry if this task is in the created status.
    IF new_status = 0 THEN
      DELETE FROM task_review WHERE task_review.task_id = update_id;;
    END IF;;
    SELECT ST_COLLECT(ST_SETSRID(ST_MAKEVALID(ST_GEOMFROMGEOJSON(jsonb_array_elements_Text::JSONB->>'geometry')), 4326)) INTO geojson_geom
		FROM JSONB_ARRAY_ELEMENTS_TEXT(geo_json->'features');;
    UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority,
                     changeset_id = task_changeset_id, mapped_on = task_mapped_on, geojson = geo_json,
                     suggestedfix_geojson = suggestedfix,
                     location = ST_Centroid(geojson_geom),
                     geom = geojson_geom
                     WHERE id = update_id;;
    UPDATE task_review SET review_status = task_review_status, review_requested_by = task_review_requested_by,
                           reviewed_by = task_reviewed_by, reviewed_at = task_reviewed_at WHERE task_review.task_id = update_id;;
    RETURN update_id;;
  END
  $$
  LANGUAGE plpgsql VOLATILE;;


# --- !Downs
ALTER TABLE task_review DROP CONSTRAINT task_task_review_cascade;;

DROP FUNCTION IF EXISTS update_task(text,bigint,text,integer,jsonb,jsonb,bigint,integer,bigint,text,timestamp without time zone,integer,integer,integer,timestamp without time zone);;
CREATE FUNCTION update_task(task_name text,
                                         task_parent_id bigint,
                                         task_instruction text,
                                         task_status integer,
                                         geo_json jsonb,
                                         suggestedfix jsonb DEFAULT NULL,
                                         task_id bigint DEFAULT -1,
                                         task_priority integer DEFAULT 0,
                                         task_changeset_id bigint DEFAULT -1,
                                         reset_interval text DEFAULT '7 days',
                                         task_mapped_on timestamp DEFAULT NULL,
                                         task_review_status integer DEFAULT NULL,
                                         task_review_requested_by integer DEFAULT NULL,
                                         task_reviewed_by integer DEFAULT NULL,
                                         task_reviewed_at timestamp DEFAULT NULL
                                       ) RETURNS integer as $$
  DECLARE
    update_id integer;;
    update_modified timestamp without time zone;;
    update_status integer;;
    new_status integer;;
    geojson_geom geometry;;
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
    SELECT ST_COLLECT(ST_SETSRID(ST_MAKEVALID(ST_GEOMFROMGEOJSON(jsonb_array_elements_Text::JSONB->>'geometry')), 4326)) INTO geojson_geom
		FROM JSONB_ARRAY_ELEMENTS_TEXT(geo_json->'features');;
    UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority,
                     changeset_id = task_changeset_id, mapped_on = task_mapped_on, geojson = geo_json,
                     suggestedfix_geojson = suggestedfix,
                     location = ST_Centroid(geojson_geom),
                     geom = geojson_geom
                     WHERE id = update_id;;
    UPDATE task_review SET review_status = task_review_status, review_requested_by = task_review_requested_by,
                           reviewed_by = task_reviewed_by, reviewed_at = task_reviewed_at WHERE task_review.task_id = update_id;;
    RETURN update_id;;
  END
  $$
  LANGUAGE plpgsql VOLATILE;;
