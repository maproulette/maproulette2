# --- MapRoulette Scheme

# --- !Ups
SELECT add_drop_column('challenges', 'challenge_type', '', false);;
DROP TABLE IF EXISTS survey_answers;;
DROP TABLE IF EXISTS answers;;

# --- !Downs
SELECT add_drop_column('challenges', 'challenge_type', 'INTEGER NOT NULL DEFAULT 1');;

-- All the answers for a specific survey
CREATE TABLE IF NOT EXISTS answers
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  survey_id integer NOT NULL,
  answer character varying NOT NULL,
  CONSTRAINT answers_survey_id_fkey FOREIGN KEY (survey_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
    DEFERRABLE INITIALLY DEFERRED
);;

DROP TRIGGER IF EXISTS update_answers_modified ON answers;;
CREATE TRIGGER update_answers_modified BEFORE UPDATE ON answers
  FOR EACH ROW EXECUTE PROCEDURE update_modified();;

SELECT create_index_if_not_exists('answers', 'survey_id', '(survey_id)');;

-- The answers for a survey from a user
CREATE TABLE IF NOT EXISTS survey_answers
(
  id SERIAL NOT NULL PRIMARY KEY,
  created timestamp without time zone DEFAULT NOW(),
  osm_user_id integer NOT NULL,
  project_id integer NOT NULL,
  survey_id integer NOT NULL,
  task_id integer NOT NULL,
  answer_id integer NOT NULL,
  CONSTRAINT survey_answers_project_id_fkey FOREIGN KEY (project_id)
    REFERENCES projects(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT survey_answers_survey_id_fkey FOREIGN KEY (survey_id)
    REFERENCES challenges(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT survey_answers_task_id_fkey FOREIGN KEY (task_id)
    REFERENCES tasks(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT survey_answers_answer_id_fkey FOREIGN KEY (answer_id)
    REFERENCES answers(id) MATCH SIMPLE
    ON UPDATE CASCADE ON DELETE CASCADE
);;

SELECT create_index_if_not_exists('survey_answers', 'survey_id', '(survey_id)');;
