# --- MapRoulette Scheme

# --- !Ups
SELECT add_drop_column('task_comments', 'challenge_id', 'integer NOT NULL DEFAULT -1');;
SELECT add_drop_column('task_comments', 'project_id', 'integer NOT NULL DEFAULT -1');;
-- update current projects before adding the constraints
UPDATE task_comments SET challenge_id = (SELECT parent_id FROM tasks WHERE task_comments.task_id = id),
                          project_id = (SELECT c.parent_id FROM challenges c
                                        INNER JOIN tasks t ON t.parent_id = c.id
                                        WHERE task_comments.task_id = t.id);;

ALTER TABLE task_comments DROP CONSTRAINT IF EXISTS task_comments_challenge_id_fkey;;
ALTER TABLE task_comments ADD CONSTRAINT task_comments_challenge_id_fkey
   FOREIGN KEY (challenge_id) REFERENCES challenges (id) MATCH SIMPLE
   ON UPDATE CASCADE ON DELETE CASCADE;;
ALTER TABLE task_comments DROP CONSTRAINT IF EXISTS task_comments_project_id_fkey;;
ALTER TABLE task_comments ADD CONSTRAINT task_comments_project_id_fkey
   FOREIGN KEY (project_id) REFERENCES projects (id) MATCH SIMPLE
   ON UPDATE CASCADE ON DELETE CASCADE;;

-- Add status failure text
SELECT add_drop_column('challenges', 'status_message', 'text NULL');;

# --- !Downs
SELECT add_drop_column('task_comments', 'challenge_id', '', false);;
SELECT add_drop_column('task_comments', 'project_id', '', false);;
ALTER TABLE task_comments DROP CONSTRAINT IF EXISTS task_comments_challenge_id_fkey;;
ALTER TABLE task_comments DROP CONSTRAINT IF EXISTS task_comments_project_id_fkey;;
SELECT add_drop_column('challenges', 'status_message', '', false);;
