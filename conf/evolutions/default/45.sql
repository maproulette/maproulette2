# --- MapRoulette Scheme

# --- !Ups
-- Update all challenges to STATUS_FINISHED (5) that have at least 1 task and
-- have no tasks in CREATED (0) or SKIPPED (3) statuses
--
-- Update all challenges to STATUS_READY (3) that were set to STATUS_FINISHED
-- but still have at least one task in CREATED (0) or SKIPPED (3) status
UPDATE challenges c SET status=5 WHERE
          0 < (SELECT COUNT(*) FROM tasks where tasks.parent_id=c.id) AND
          0 = (SELECT COUNT(*) AS total FROM tasks
          WHERE tasks.parent_id=c.id AND status IN (0, 3));;

UPDATE challenges c SET status=3 WHERE
          c.status = 5 AND
          0 < (SELECT COUNT(*) AS total FROM tasks
          WHERE tasks.parent_id=c.id AND status IN (0, 3));;

# --- !Downs
