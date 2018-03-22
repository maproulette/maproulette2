# --- MapRoulette Scheme

# --- !Ups
-- Add trigger function that will automatically update project and challenge ID's in the task_comments table
CREATE OR REPLACE FUNCTION on_task_comment_insert() RETURNS TRIGGER AS $$
BEGIN
  IF new.challenge_id IS NULL OR new.challenge_id = -1 THEN
    new.challenge_id := (SELECT parent_id FROM tasks WHERE id = new.task_id);;
  END IF;;
  IF new.project_id IS NULL OR new.project_id = -1 THEN
    new.project_id := (SELECT parent_id FROM challenges WHERE id = new.challenge_id);;
  END IF;;
  RETURN new;;
END
$$
LANGUAGE plpgsql VOLATILE;;

DROP TRIGGER IF EXISTS on_task_comment_insert ON task_comments;;
CREATE TRIGGER on_task_comment_insert BEFORE INSERT ON task_comments
  FOR EACH ROW EXECUTE PROCEDURE on_task_comment_insert();;

# --- !Downs
