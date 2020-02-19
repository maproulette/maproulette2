# --- MapRoulette Scheme

# --- !Ups
-- THESE COMMENTED SCRIPTS NEED TO BE RUN BEFORE UPGRADING -------------------------------
-- Add new geojson and geometries into the task table, migrate all the task geometry data to the task table and then drop the task_geometry table
DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'tasks' AND column_name = 'geojson';;
  IF NOT FOUND THEN
    ALTER TABLE tasks ADD COLUMN geojson JSONB;;
  END IF;;
END$$;;
DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'tasks' AND column_name = 'geom';;
  IF NOT FOUND THEN
    PERFORM AddGeometryColumn('tasks', 'geom', 4326, 'GEOMETRY', 2);;
  END IF;;
END$$;;
DO $$
BEGIN
  PERFORM column_name FROM information_schema.columns WHERE table_name = 'tasks' AND column_name = 'suggestedfix_geojson';;
  IF NOT FOUND THEN
    ALTER TABLE tasks ADD COLUMN suggestedfix_geojson JSONB;;
  END IF;;
END$$;;

-- Migrate all the old geometries to the new structure
-- update the geojson columns -- SEE 39_upgrade.py python script
-- THIS PYTHON SCRIPT MUST BE RUN BEFORE UPGRADING
--DO $$
--    DECLARE identifier integer;;
--BEGIN
--    FOR identifier IN SELECT id FROM tasks LOOP
--        UPDATE tasks t SET geojson = geoms.geometries FROM (SELECT ROW_TO_JSON(fc)::JSONB AS geometries
--                      FROM ( SELECT 'FeatureCollection' AS type, ARRAY_TO_JSON(array_agg(f)) AS features
--                               FROM ( SELECT 'Feature' AS type,
--                                              ST_AsGeoJSON(lg.geom)::JSONB AS geometry,
--                                              HSTORE_TO_JSON(lg.properties) AS properties
--                                      FROM task_geometries AS lg
--                                      WHERE task_id = identifier
--                                ) AS f
--                        )  AS fc) AS geoms WHERE id = identifier;;
--        -- Update the geometry and location columns
--        UPDATE tasks t SET geom = geoms.geometry, location = ST_CENTROID(geoms.geometry)
--        FROM (SELECT ST_COLLECT(ST_MAKEVALID(geom)) AS geometry FROM (
--                SELECT geom FROM task_geometries WHERE task_id = identifier
--             ) AS innerQuery) AS geoms WHERE id = identifier;;
--    END LOOP;;
--END$$;;

-- update the geojson columns for suggested fixes
--DO $$
--	  DECLARE id integer;;
--	  DECLARE c integer;;
--BEGIN
--	  FOR id IN SELECT t.id FROM tasks t INNER JOIN task_suggested_fix tf ON t.id = tf.task_id LOOP
--        UPDATE tasks t SET suggestedfix_geojson = geoms.geometries FROM (SELECT ROW_TO_JSON(fc)::JSONB AS geometries
--              FROM ( SELECT 'FeatureCollection' AS type, ARRAY_TO_JSON(array_agg(f)) AS features
--                       FROM ( SELECT 'Feature' AS type,
--                                      ST_AsGeoJSON(lg.geom)::JSONB AS geometry,
--                                      HSTORE_TO_JSON(lg.properties) AS properties
--                              FROM task_suggested_fix AS lg
--                              WHERE task_id = id
--                        ) AS f
--                )  AS fc) AS geoms;
--    END LOOP;;
--END$$;;

--DROP TABLE task_geometries CASCADE;;
--DROP TABLE task_suggested_fix CASCADE;;
----------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_challenge_geometry(challenge_identifier bigint)
    RETURNS VOID AS $$
DECLARE
    identifier BIGINT;;
BEGIN
    FOR identifier IN SELECT id FROM tasks WHERE parent_id = challenge_identifier AND geojson IS NULL LOOP
        PERFORM update_geometry(identifier);;
     END LOOP;;
END
$$ LANGUAGE plpgsql VOLATILE;;

CREATE OR REPLACE FUNCTION update_geometry(task_identifier bigint)
	RETURNS TABLE(geo TEXT, loc TEXT, fix_geo TEXT) AS $$
BEGIN
    UPDATE tasks t SET geojson = geoms.geometries FROM (SELECT ROW_TO_JSON(fc)::JSONB AS geometries
                      FROM ( SELECT 'FeatureCollection' AS type, ARRAY_TO_JSON(array_agg(f)) AS features
                               FROM ( SELECT 'Feature' AS type,
                                              ST_AsGeoJSON(lg.geom)::JSONB AS geometry,
                                              HSTORE_TO_JSON(lg.properties) AS properties
                                      FROM task_geometries AS lg
                                      WHERE task_id = task_identifier
                                ) AS f
                        )  AS fc) AS geoms WHERE id = task_identifier;;
    -- Update the geometry and location columns
    UPDATE tasks t SET geom = geoms.geometry, location = ST_CENTROID(geoms.geometry)
    FROM (SELECT ST_COLLECT(ST_MAKEVALID(geom)) AS geometry FROM (
            SELECT geom FROM task_geometries WHERE task_id = task_identifier
         ) AS innerQuery) AS geoms WHERE id = task_identifier;;
	 RETURN QUERY SELECT geojson::TEXT, ST_AsGeoJSON(location) AS geo_location, suggestedfix_geojson::TEXT FROM tasks
	 WHERE id = task_identifier;;
END
$$ LANGUAGE plpgsql VOLATILE;;

-- Creates or updates and task. Will also check if task status needs to be updated
-- This change adds the mapped_on, review_status, review_requested_by, reviewed_by
CREATE OR REPLACE FUNCTION create_update_task(task_name text,
                                              task_parent_id bigint,
                                              task_instruction text,
                                              task_status integer,
                                              geojson jsonb,
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
		FROM JSONB_ARRAY_ELEMENTS_TEXT(geojson->'features');;

        INSERT INTO tasks (name, parent_id, instruction, priority, geojson, location, geom, suggestedfix_geojson)
        VALUES (task_name, task_parent_id, task_instruction, task_priority, geojson, ST_Centroid(geojson_geom), geojson_geom, suggestedfix) RETURNING id INTO return_id;;
        EXCEPTION WHEN UNIQUE_VIOLATION THEN
        SELECT INTO return_id update_task(task_name, task_parent_id, task_instruction, task_status, geojson, suggestedfix, task_id, task_priority,
                                            task_changeset_id, reset_interval, task_mapped_on, task_review_status, task_review_requested_by, task_reviewed_by, task_reviewed_at);;
      END;;
    ELSE
      PERFORM update_task(task_name, task_parent_id, task_instruction, task_status, geojson, suggestedfix, task_id, task_priority,
                            task_changeset_id, reset_interval, task_mapped_on, task_review_status, task_review_requested_by, task_reviewed_by, task_reviewed_at);;
    END IF;;
    RETURN return_id;;
  END
  $$
  LANGUAGE plpgsql VOLATILE;;

CREATE OR REPLACE FUNCTION update_task(task_name text,
                                         task_parent_id bigint,
                                         task_instruction text,
                                         task_status integer,
                                         geojson jsonb,
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
		FROM JSONB_ARRAY_ELEMENTS_TEXT(geojson->'features');;
    UPDATE tasks SET name = task_name, instruction = task_instruction, status = new_status, priority = task_priority,
                     changeset_id = task_changeset_id, mapped_on = task_mapped_on, geojson = geojson,
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



-- Add tag type for tags
ALTER TABLE "task_review" ADD COLUMN review_started_at timestamp without time zone DEFAULT NULL;;
ALTER TABLE "task_review_history" ADD COLUMN review_started_at timestamp without time zone DEFAULT NULL;;

# --- !Downs
-- The Downs in this version will not work, essentially it is not backwards compatible at this point. Once you upgrade to version 37, you cannot drop back to a previous version

ALTER TABLE "task_review" DROP COLUMN review_started_at;;
ALTER TABLE "task_review_history" DROP COLUMN review_started_at;;
