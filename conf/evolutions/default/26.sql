# --- MapRoulette Scheme

# --- !Ups
-- New table for virtual challenges
CREATE TABLE IF NOT EXISTS user_metrics
(
  user_id integer NOT NULL,
  score integer NOT NULL,
  total_fixed integer,
  total_false_positive integer,
  total_already_fixed integer,
  total_too_hard integer,
  total_skipped integer
);;

CREATE TABLE IF NOT EXISTS user_metrics_history
(
  user_id integer NOT NULL,
  score integer NOT NULL,
  total_fixed integer,
  total_false_positive integer,
  total_already_fixed integer,
  total_too_hard integer,
  total_skipped integer,
  snapshot_date timestamp without time zone DEFAULT NOW()
);;

SELECT create_index_if_not_exists('user_metrics', 'user_id', '(user_id)');;
SELECT create_index_if_not_exists('user_metrics_history', 'user_id', '(user_id)');;
SELECT create_index_if_not_exists('user_metrics_history', 'user_id_snapshot_date', '(user_id, snapshot_date)');;

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
 GROUP BY sa.osm_user_id, users.id;;


# --- !Downs
--DROP TABLE IF EXISTS user_metrics;;
--DROP TABLE IF EXISTS user_metrics_history;;
