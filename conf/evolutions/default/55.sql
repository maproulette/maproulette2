# --- !Ups
-- Add task_styles to challenges.
SELECT add_drop_column('challenges', 'task_styles', 'VARCHAR DEFAULT NULL');;
-- add task_id index for status_actions, as it causes deletes to run slower.
SELECT create_index_if_not_exists('status_actions', 'task_id', '(task_id)');;
-- Add bundle_id index for when we are updating the bundle id for a task
SELECT create_index_if_not_exists('tasks', 'bundle_id', '(bundle_id)');;

# --- !Downs
SELECT add_drop_column('challenges', 'task_styles', '', false);;
DROP INDEX IF EXISTS idx_status_actions_task_id;;
DROP INDEX IF EXISTS idx_tasks_bundle_id;;
