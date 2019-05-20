# --- MapRoulette Scheme

# --- !Ups
-- Add support for virtual projectSearch
ALTER TABLE projects ADD COLUMN is_virtual boolean DEFAULT false;

CREATE TABLE virtual_project_challenges
(
  id SERIAL NOT NULL PRIMARY KEY,
  project_id integer NOT NULL,
  challenge_id integer NOT NULL,
  created timestamp without time zone DEFAULT NOW(),

  CONSTRAINT virtual_project_id_fkey FOREIGN KEY (project_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON DELETE CASCADE,

  CONSTRAINT virtual_challenge_id_fkey FOREIGN KEY (challenge_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('virtual_project_challenges', 'vp_project_id', '(project_id)');;
SELECT create_index_if_not_exists('virtual_project_challenges', 'vp_challenge_id', '(challenge_id)');;

# --- !Downs
ALTER TABLE projects DROP COLUMN is_virtual;

DROP TABLE virtual_project_challenges;
