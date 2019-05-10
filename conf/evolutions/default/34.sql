# --- MapRoulette Scheme

# --- !Ups
UPDATE status_actions
SET project_id = challenges.parent_id
FROM challenges
WHERE   challenge_id = challenges.id AND
        challenge_id IN (select distinct challenge_id
                    from status_actions
                    INNER JOIN challenges ON challenges.id = status_actions.challenge_id
                    WHERE challenges.parent_id != status_actions.project_id);
