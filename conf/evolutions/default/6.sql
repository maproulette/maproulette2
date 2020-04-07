# --- !Ups
-- Index for cleanOldTasks job
SELECT create_index_if_not_exists('tasks', 'status_modified', '(status, modified)');;

# --- !Downs
DROP INDEX IF EXISTS idx_tasks_status_modified;;
