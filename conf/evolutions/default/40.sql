# --- MapRoulette Scheme

# --- !Ups

-- Creates or updates and task. Will also check if task status needs to be updated
-- This change adds the mapped_on, review_status, review_requested_by, reviewed_by
DROP FUNCTION IF EXISTS create_update_task(text,bigint,text,integer,jsonb,jsonb,bigint,integer,bigint,text,timestamp without time zone,integer,integer,integer,timestamp without time zone);
CREATE FUNCTION create_update_task(task_name text,
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
                                              task_reviewed_at timestamp DEFAULT NULL) RETURNS integer as $$
  DECLARE
    return_id integer;;
    geojson_geom geometry;;
  BEGIN
    return_id := task_id;;
    IF (SELECT task_id) = -1 THEN
      BEGIN
        SELECT ST_COLLECT(ST_SETSRID(ST_MAKEVALID(ST_GEOMFROMGEOJSON(jsonb_array_elements_Text::JSONB->>'geometry')), 4326)) INTO geojson_geom
		FROM JSONB_ARRAY_ELEMENTS_TEXT(geo_json->'features');;

        INSERT INTO tasks (name, parent_id, instruction, priority, geojson, location, geom, suggestedfix_geojson)
        VALUES (task_name, task_parent_id, task_instruction, task_priority, geo_json, ST_Centroid(geojson_geom), geojson_geom, suggestedfix) RETURNING id INTO return_id;;
        EXCEPTION WHEN UNIQUE_VIOLATION THEN
        SELECT INTO return_id update_task(task_name, task_parent_id, task_instruction, task_status, geo_json, suggestedfix, task_id, task_priority,
                                            task_changeset_id, reset_interval, task_mapped_on, task_review_status, task_review_requested_by, task_reviewed_by, task_reviewed_at);;
      END;;
    ELSE
      PERFORM update_task(task_name, task_parent_id, task_instruction, task_status, geo_json, suggestedfix, task_id, task_priority,
                            task_changeset_id, reset_interval, task_mapped_on, task_review_status, task_review_requested_by, task_reviewed_by, task_reviewed_at);;
    END IF;;
    RETURN return_id;;
  END
  $$
  LANGUAGE plpgsql VOLATILE;;

DROP FUNCTION IF EXISTS update_task(text,bigint,text,integer,jsonb,jsonb,bigint,integer,bigint,text,timestamp without time zone,integer,integer,integer,timestamp without time zone);
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

# --- !Downs
