#Upgrade Scripts

Upgrade scripts are only required if you have a database that is built using an older version and needed to convert it to the new version. If it is a brand new fresh database then this would not be required.

### 39_upgrade

This upgrade removed all task_geometries table and placed all the geometries directly in the tasks table, which made everything a lot more efficient. To upgrade you can run either python script or SQL script to move the geometries. Warning this can take a long time if there are a lot of geometries.

### updateOSMKeys

This if not actually an upgrade script, but will update the cert keys if needed.

### v4_play_evolutions

This update csv file is used to manually update all the evolutions. This was between API versions 3.8.4 and 4.0.0. The V4 version added an integration test framework that required fully working evolution files, and unfortunately our scripts were poorly written from the beginning, so each script had to be updated. So this csv file is required if you have an old database that contains data from a 3.8.4 build and needs to be upgraded to 4.0.0. 

To import the data the following steps:
1. Delete all the records in the table play_evolutions using "DELETE FROM play_evolutions"
2. Copy in all the CSV data using "COPY play_evolutions(id,hash,applied_at,apply_script,revert_script,state,last_problem) FROM '/v4_play_evolutions.csv' DELIMITER AS E'\t' CSV QUOTE AS '"' ESCAPE AS '''';"

You can also use PgAdmin and use the import function.
