do $$
	declare idlist int[];
begin
	select array(select id from tasks where geojson is null limit 1000000) into idlist;
	UPDATE tasks t SET geojson = geoms.geometries FROM (
		SELECT task_id, ROW_TO_JSON(fc)::JSONB AS geometries
		FROM ( SELECT task_id, 'FeatureCollection' AS type, ARRAY_TO_JSON(array_agg(f)) AS features
				FROM ( SELECT task_id, 'Feature' AS type,
								ST_AsGeoJSON(lg.geom)::JSONB AS geometry,
								HSTORE_TO_JSON(lg.properties) AS properties
						FROM task_geometries AS lg
						WHERE task_id = ANY (idlist)
				) AS f GROUP BY task_id
	)  AS fc) AS geoms WHERE id = ANY (idlist) AND id = geoms.task_id;
	UPDATE tasks t SET geom = geoms.geometry, location = ST_CENTROID(geoms.geometry)
	FROM (SELECT task_id, ST_COLLECT(ST_MAKEVALID(geom)) AS geometry FROM (
		   SELECT task_id, geom FROM task_geometries WHERE task_id = ANY (idlist)
		) AS innerQuery GROUP BY task_id) AS geoms WHERE id = ANY (idlist) AND id = geoms.task_id;
end $$;

