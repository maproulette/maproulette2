# --- MapRoulette Scheme

# --- !Ups
-- Updating index so that we can't create duplicate tags with tasks or challenges
DROP INDEX IF EXISTS idx_tags_on_tasks_task_id_tag_id;;
SELECT create_index_if_not_exists('tags_on_tasks', 'task_id_tag_id', '(task_id, tag_id)', true);;
DROP INDEX IF EXISTS idx_tags_on_challenges_challenge_id_tag_id;;
SELECT create_index_if_not_exists('tags_on_challenges', 'challenge_id_tag_id', '(challenge_id, tag_id)', true);;

# --- !Downs
DROP INDEX IF EXISTS idx_tags_on_tasks_task_id_tag_id;;
SELECT create_index_if_not_exists('tags_on_tasks', 'task_id_tag_id', '(task_id, tag_id)');;
DROP INDEX IF EXISTS idx_tags_on_challenges_challenge_id_tag_id;;
SELECT create_index_if_not_exists('tags_on_challenges', 'challenge_id_tag_id', '(challenge_id, tag_id)');;
