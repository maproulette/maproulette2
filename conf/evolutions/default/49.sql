# --- MapRoulette Scheme

# --- !Ups
-- Creates or updates and task. Will also check if task status needs to be updated
-- This change makes sure not to reset the task status if a task is disabled or declared a false positive
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
