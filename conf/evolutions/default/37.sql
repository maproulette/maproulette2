# --- MapRoulette Scheme

# --- !Ups
-- Add tag type for tags

ALTER TABLE "tags" ADD COLUMN tag_type character varying DEFAULT 'challenges';;

SELECT create_index_if_not_exists('tags', 'tags_tag_type', '(tag_type)');;
SELECT create_index_if_not_exists('tags', 'tags_tag_type_name', '(tag_type, name)');;

UPDATE tags set tag_type = 'challenges';

# --- !Downs
ALTER TABLE "tags" DROP COLUMN tag_type;;
