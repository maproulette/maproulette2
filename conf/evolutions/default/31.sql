# --- MapRoulette Scheme

# --- !Ups
-- Change users.needs_review to be an integer type so we can have more
-- variability with this setting.

ALTER TABLE users ALTER COLUMN needs_review SET DEFAULT null;;

ALTER TABLE users ALTER COLUMN needs_review TYPE INTEGER USING
    CASE
      WHEN needs_review = true then 1
      WHEN needs_review = false then 0
      ELSE NULL
    END;;


# --- !Downs
ALTER TABLE users ALTER COLUMN needs_review TYPE BOOLEAN USING
    CASE
      WHEN needs_review = 1 then true
      WHEN needs_review = 2 then true
      ELSE false
    END;;

ALTER TABLE users ALTER COLUMN needs_review SET DEFAULT false;;
