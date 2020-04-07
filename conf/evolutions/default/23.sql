# --- MapRoulette Scheme

# --- !Ups
-- Updates all challenges to a STATUS_FINISHED that have no incomplete tasks
UPDATE challenges c SET status=5 WHERE c.status=3 AND
          0=(SELECT COUNT(*) AS total FROM tasks
          WHERE tasks.parent_id=c.id AND status=0);;

# --- !Downs
