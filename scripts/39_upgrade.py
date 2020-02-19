#!/usr/bin/python
import psycopg2
import sys
import time

hostname = 'localhost'
port = 5433
username = 'osm'
password = 'osm'
database = 'mr_07_11_19'

def main():
    conn = psycopg2.connect(host=hostname, user=username, password=password, dbname=database, port=port)
    cur = conn.cursor()
    cur.execute("SELECT id FROM tasks WHERE geojson IS NULL OR geom IS NULL")
    counter=0
    idList=[]
    for row in cur:
        idList.append(row[0])
        counter = counter + 1
        if counter % 25000 == 0:
            stringIdList = ','.join(str(e) for e in idList)
            start = time.time()
            updateCursor = conn.cursor()
            updateCursor.execute(f"""UPDATE tasks t SET geojson = geoms.geometries FROM (
                                        SELECT task_id, ROW_TO_JSON(fc)::JSONB AS geometries
                                        FROM ( SELECT task_id, 'FeatureCollection' AS type, ARRAY_TO_JSON(array_agg(f)) AS features
                                                FROM ( SELECT task_id, 'Feature' AS type,
                                                                ST_AsGeoJSON(lg.geom)::JSONB AS geometry,
                                                                HSTORE_TO_JSON(lg.properties) AS properties
                                                        FROM task_geometries AS lg
                                                        WHERE task_id IN ({stringIdList})
                                                ) AS f GROUP BY task_id
                                    )  AS fc) AS geoms WHERE id IN ({stringIdList}) AND id = geoms.task_id""")
            updateCursor.execute(f"""
                UPDATE tasks t SET geom = geoms.geometry, location = ST_CENTROID(geoms.geometry)
                FROM (SELECT task_id, ST_COLLECT(ST_MAKEVALID(geom)) AS geometry FROM (
                       SELECT task_id, geom FROM task_geometries WHERE task_id IN ({stringIdList})
                    ) AS innerQuery GROUP BY task_id) AS geoms WHERE id IN ({stringIdList}) AND id = geoms.task_id
            """)
            conn.commit()
            updateCursor.close()
            end = time.time()
            print(f"Updated: {counter} ({len(idList)} in {end - start})")
            #print("For ids: ", stringIdList)
            idList.clear()
    cur.close()

if __name__ == "__main__":
    main()
