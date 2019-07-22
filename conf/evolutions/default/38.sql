# --- MapRoulette Scheme

# --- !Ups
-- Remove unique constraint on name for tags and make it a unique constraint
-- for name/tag_type

DROP INDEX "idx_tags_name";;

CREATE UNIQUE INDEX "idx_tags_name_tag_type" ON "tags" (lower("name"), "tag_type");;


# -- !Downs
DROP INDEX "idx_tags_name_tag_type";; 

SELECT create_index_if_not_exists('tags', 'name', '(lower(name))', true);;
