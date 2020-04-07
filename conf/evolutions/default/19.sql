# --- MapRoulette Scheme

# --- !Ups
-- Remove unique index on Groups
DROP INDEX IF EXISTS idx_groups_name;;
SELECT create_index_if_not_exists('groups', 'project_id_group_type', '(project_id, group_type)');;

# --- !Downs
DROP INDEX IF EXISTS idx_groups_project_id_group_type;;
-- previously the index was a unique index, but due to changes the database would be invalid and we wouldn't be able to create a unique index at this point
SELECT create_index_if_not_exists('groups', 'name', '(lower(name))');;
