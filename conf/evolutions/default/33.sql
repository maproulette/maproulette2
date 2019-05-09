# --- MapRoulette Scheme

# --- !Ups
-- Add unique constraint on task_review.task_id

ALTER TABLE "task_review" ADD CONSTRAINT task_review_task_id UNIQUE (task_id);

# --- !Downs
ALTER TABLE "task_review" DROP CONSTRAINT task_review_task_id
