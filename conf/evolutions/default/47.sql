# --- !Ups
-- Add subscription option for challenge completion notifications
ALTER TABLE IF EXISTS user_notification_subscriptions ADD COLUMN challenge_completed integer NOT NULL DEFAULT 1;;

# --- !Downs
ALTER TABLE IF EXISTS user_notification_subscriptions DROP COLUMN challenge_completed;;
