# --- MapRoulette Scheme

# --- !Ups
-- Add needs_review, is_reviewer to user table
ALTER TABLE "user_metrics" ADD COLUMN initial_rejected integer DEFAULT 0;;
ALTER TABLE "user_metrics" ADD COLUMN initial_approved integer DEFAULT 0;;
ALTER TABLE "user_metrics" ADD COLUMN initial_assisted integer DEFAULT 0;;
ALTER TABLE "user_metrics" ADD COLUMN total_rejected integer DEFAULT 0;;
ALTER TABLE "user_metrics" ADD COLUMN total_approved integer DEFAULT 0;;
ALTER TABLE "user_metrics" ADD COLUMN total_assisted integer DEFAULT 0;;

ALTER TABLE "user_metrics_history" ADD COLUMN initial_rejected integer DEFAULT 0;;
ALTER TABLE "user_metrics_history" ADD COLUMN initial_approved integer DEFAULT 0;;
ALTER TABLE "user_metrics_history" ADD COLUMN initial_assisted integer DEFAULT 0;;
ALTER TABLE "user_metrics_history" ADD COLUMN total_rejected integer DEFAULT 0;;
ALTER TABLE "user_metrics_history" ADD COLUMN total_approved integer DEFAULT 0;;
ALTER TABLE "user_metrics_history" ADD COLUMN total_assisted integer DEFAULT 0;;

ALTER TABLE "user_metrics" ADD CONSTRAINT user_metric_primary_key PRIMARY KEY (user_id);;

-- This fixes bug where new users since the original creation of user_metrics where not having
-- their score tallied.
INSERT INTO user_metrics
(user_id, score, total_fixed, total_false_positive, total_already_fixed, total_too_hard, total_skipped)
SELECT users.id,
         SUM(CASE sa.status
             WHEN 1 THEN 5
             WHEN 2 THEN 3
             WHEN 5 THEN 3
             WHEN 6 THEN 1
             WHEN 3 THEN 0
             ELSE 0
         END) AS score,
         SUM(CASE WHEN sa.status = 1 then 1 else 0 end) total_fixed,
         SUM(CASE WHEN sa.status = 2 then 1 else 0 end) total_false_positive,
         SUM(CASE WHEN sa.status = 5 then 1 else 0 end) total_already_fixed,
         SUM(CASE WHEN sa.status = 6 then 1 else 0 end) total_too_hard,
         SUM(CASE WHEN sa.status = 3 then 1 else 0 end) total_skipped
 FROM status_actions sa, users
 WHERE users.osm_id = sa.osm_user_id AND sa.old_status <> sa.status
    AND users.id NOT IN (select user_id from user_metrics)
 GROUP BY sa.osm_user_id, users.id;;

# --- !Downs
ALTER TABLE "user_metrics" DROP COLUMN initial_rejected;;
ALTER TABLE "user_metrics" DROP COLUMN initial_approved;;
ALTER TABLE "user_metrics" DROP COLUMN initial_assisted;;
ALTER TABLE "user_metrics" DROP COLUMN total_rejected;;
ALTER TABLE "user_metrics" DROP COLUMN total_approved;;
ALTER TABLE "user_metrics" DROP COLUMN total_assisted;;

ALTER TABLE "user_metrics_history" DROP COLUMN initial_rejected;;
ALTER TABLE "user_metrics_history" DROP COLUMN initial_approved;;
ALTER TABLE "user_metrics_history" DROP COLUMN initial_assisted;;
ALTER TABLE "user_metrics_history" DROP COLUMN total_rejected;;
ALTER TABLE "user_metrics_history" DROP COLUMN total_approved;;
ALTER TABLE "user_metrics_history" DROP COLUMN total_assisted;;

ALTER TABLE "user_metrics" DROP CONSTRAINT user_metric_primary_key
