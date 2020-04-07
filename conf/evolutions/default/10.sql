# --- MapRoulette Scheme

# --- !Ups
-- Add default Challenge for default Survey Answers
INSERT INTO challenges (id, name, parent_id, challenge_type) VALUES (-1, 'Default Dummy Survey', 0, 2);;
-- Add default Valid (-1) and InValid (-2) answers for any Challenge
INSERT INTO answers (id, survey_id, answer) VALUES
  (-1, -1, 'Valid'),
  (-2, -1, 'Invalid');;

# --- !Downs
DELETE FROM challenges WHERE id = -1;;
DELETE FROM answers WHERE id IN (-1, -2);;
