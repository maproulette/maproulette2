# --- MapRoulette Scheme

# --- !Ups
-- New table for bundles
CREATE TABLE IF NOT EXISTS completion_snapshots
(
  id SERIAL NOT NULL PRIMARY KEY,
  type_id integer NOT NULL,
  item_id integer NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  priority integer,
  available integer NOT NULL,
  fixed integer NOT NULL,
  false_positive integer NOT NULL,
  skipped integer NOT NULL,
  deleted integer NOT NULL,
  already_fixed integer NOT NULL,
  too_hard integer NOT NULL,
  answered integer NOT NULL,
  validated integer NOT NULL,
  disabled integer NOT NULL
);;

-- New table for bundles
CREATE TABLE IF NOT EXISTS review_snapshots
(
  id SERIAL NOT NULL PRIMARY KEY,
  type_id integer NOT NULL,
  item_id integer NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  requested integer NOT NULL,
  approved integer NOT NULL,
  rejected integer NOT NULL,
  assisted integer NOT NULL,
  disputed integer NOT NULL
);;

-- New table for task bundles
CREATE TABLE IF NOT EXISTS challenge_snapshots
(
  id SERIAL NOT NULL PRIMARY KEY,
  challenge_id integer NOT NULL,
  challenge_name text NOT NULL,
  challenge_status integer,
  created timestamp without time zone DEFAULT NOW(),
  completion_snapshot_id integer NOT NULL,
  low_completion_snapshot_id integer NOT NULL,
  medium_completion_snapshot_id integer NOT NULL,
  high_completion_snapshot_id integer NOT NULL,
  review_snapshot_id integer NOT NULL,
  manual Boolean DEFAULT TRUE,

  CONSTRAINT challenge_completion_snapshot_id FOREIGN KEY (completion_snapshot_id)
    REFERENCES completion_snapshots(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT challenge_low_completion_snapshot_id FOREIGN KEY (low_completion_snapshot_id)
    REFERENCES completion_snapshots(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT challenge_medium_completion_snapshot_id FOREIGN KEY (medium_completion_snapshot_id)
    REFERENCES completion_snapshots(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT challenge_high_completion_snapshot_id FOREIGN KEY (high_completion_snapshot_id)
    REFERENCES completion_snapshots(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT challenge_review_snapshot_id FOREIGN KEY (review_snapshot_id)
    REFERENCES review_snapshots(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('challenge_snapshots', 'cs_challenge_id', '(challenge_id)');;
SELECT create_index_if_not_exists('challenge_snapshots', 'cs_challenge_id_created', '(challenge_id, created)');;
SELECT create_index_if_not_exists('completion_snapshots', 'cs_item_id_type_id', '(item_id, type_id)');;
SELECT create_index_if_not_exists('completion_snapshots', 'cs_item_id_type_id_created', '(item_id, type_id, created)');;
SELECT create_index_if_not_exists('completion_snapshots', 'cs_priority', '(priority)');;

# --- !Downs
DROP TABLE IF EXISTS challenge_snapshots;;
DROP TABLE IF EXISTS completion_snapshots;;
DROP TABLE IF EXISTS review_snapshots;;
