# --- MapRoulette Scheme

# --- !Ups
-- New table for bundles
CREATE TABLE IF NOT EXISTS bundles
(
  id SERIAL NOT NULL PRIMARY KEY,
  owner_id integer NOT NULL,
  name character varying NULL,
  description character varying NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  CONSTRAINT bundles_owner_id FOREIGN KEY (owner_id)
    REFERENCES users(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

-- New table for task bundles
CREATE TABLE IF NOT EXISTS task_bundles
(
  id SERIAL NOT NULL PRIMARY KEY,
  task_id integer NOT NULL,
  bundle_id integer NOT NULL,
  CONSTRAINT task_bundles_task_id FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT task_bundles_bundle_id FOREIGN KEY (bundle_id)
    REFERENCES bundles(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

ALTER TABLE tasks ADD COLUMN bundle_id integer NULL;;
ALTER TABLE tasks ADD COLUMN is_bundle_primary boolean;;

SELECT create_index_if_not_exists('task_bundles', 'bundle_id', '(bundle_id)');;

# --- !Downs
ALTER TABLE tasks DROP COLUMN bundle_id;;
ALTER TABLE tasks DROP COLUMN is_bundle_primary;;

DROP TABLE IF EXISTS task_bundles;;
DROP TABLE IF EXISTS bundles;;
