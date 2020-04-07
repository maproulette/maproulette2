# --- MapRoulette Scheme

# --- !Ups
-- New table for virtual challenges
CREATE TABLE IF NOT EXISTS virtual_challenges
(
  id SERIAL NOT NULL PRIMARY KEY,
  owner_id integer NOT NULL,
  name character varying NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  description character varying NULL,
  search_parameters character varying NOT NULL,
  expiry timestamp with time zone DEFAULT NOW() + INTERVAL '1 day'
);;

SELECT create_index_if_not_exists('virtual_challenges', 'owner_id', '(owner_id)');;

CREATE TABLE IF NOT EXISTS virtual_challenge_tasks
(
  id SERIAL NOT NULL PRIMARY KEY,
  task_id integer NOT NULL,
  virtual_challenge_id integer NOT NULL,
  CONSTRAINT virtual_challenges_tasks_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT virtual_challenges_tasks_virtual_challenge_id_fkey FOREIGN KEY (virtual_challenge_id)
    REFERENCES virtual_challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('virtual_challenge_tasks', 'virtual_challenge_id', '(virtual_challenge_id)');;

# --- !Downs
DROP TABLE virtual_challenge_tasks;;
DROP TABLE virtual_challenges;;
