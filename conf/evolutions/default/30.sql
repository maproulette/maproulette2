# --- !Ups
-- Add email address to users table
ALTER TABLE "users" ADD COLUMN email character varying;;

-- New table for notifications
CREATE TABLE IF NOT EXISTS user_notifications
(
  id SERIAL NOT NULL PRIMARY KEY,
  user_id integer NOT NULL,
  notification_type integer NOT NULL,
  created timestamp without time zone DEFAULT NOW(),
  modified timestamp without time zone DEFAULT NOW(),
  description character varying,
  from_username character varying,
  is_read boolean DEFAULT FALSE,
  email_status integer NOT NULL,
  task_id integer,
  challenge_id integer,
  project_id integer,
  target_id integer,
  extra character varying
);;

SELECT create_index_if_not_exists('user_notifications', 'user_id', '(user_id)');;
SELECT create_index_if_not_exists('user_notifications', 'email_status', '(email_status)');;

-- New table for notification subscriptions
CREATE TABLE IF NOT EXISTS user_notification_subscriptions
(
  id SERIAL NOT NULL PRIMARY KEY,
  user_id integer NOT NULL,
  system integer NOT NULL DEFAULT 1,
  mention integer NOT NULL DEFAULT 1,
  review_approved integer NOT NULL DEFAULT 1,
  review_rejected integer NOT NULL DEFAULT 1,
  review_again integer NOT NULL DEFAULT 1
);;

SELECT create_index_if_not_exists('user_notification_subscriptions', 'user_id', '(user_id)', true);;

# --- !Downs
DROP TABLE IF EXISTS user_notification_subscriptions;;
DROP TABLE IF EXISTS user_notifications;;
ALTER TABLE "users" DROP COLUMN email;;
