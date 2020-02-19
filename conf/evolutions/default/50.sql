# --- !Ups
-- Add configurable feature properties to treat as osm id
ALTER TABLE IF EXISTS challenges ADD COLUMN osm_id_property VARCHAR;;

# --- !Downs
ALTER TABLE IF EXISTS challenges DROP COLUMN osm_id_property;;
