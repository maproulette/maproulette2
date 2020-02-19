# --- !Ups
-- Add featured column to projects
ALTER TABLE IF EXISTS projects ADD COLUMN featured BOOLEAN DEFAULT(false);;

# --- !Downs
ALTER TABLE IF EXISTS projects DROP COLUMN featured;;
