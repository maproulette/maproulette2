// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm._
import org.maproulette.data.{ChallengeType, ItemType, TaskType}
import org.maproulette.exception.InvalidException
import org.maproulette.models.{BaseObject, Tag}
import org.maproulette.session.User

/**
  * @author cuthbertm
  */
trait TagDALMixin[T <: BaseObject[Long]] {
  this: BaseDAL[Long, T] =>

  // This is basically the table name, but removing the 's' for the column foreign key
  private lazy val name = tableName.substring(0, tableName.length - 1)

  def tagDAL: TagDAL

  /**
    * Deletes tags from a item. This will not delete any items or tags, it will simply sever the
    * connection between the item and tag.
    *
    * @param id   The item id that the user is removing the tags from
    * @param tags The tags that are being removed from the item
    * @param user The user executing the item
    */
  def deleteItemTags(id: Long, tags: List[Long], user: User)(implicit c: Option[Connection] = None): Unit = {
    if (tags.nonEmpty) {
      this.permission.hasAdminAccess(getItemTypeBasedOnTableName, user)(id)
      this.withMRTransaction { implicit c =>
        SQL"""DELETE FROM tags_on_${this.tableName} WHERE ${this.name}_id = {$id} AND tag_id IN ($tags)""".execute()
      }
    }
  }

  private def getItemTypeBasedOnTableName: ItemType = {
    this.tableName match {
      case "challenges" => ChallengeType()
      case "tasks" => TaskType()
    }
  }

  /**
    * Pretty much the same as {@link this#deleteItemTags} but removes the tags from the items based
    * on the name of the tag instead of the id. This is most likely to be used more often.
    *
    * @param id   The id of the item that is having the tags remove from it
    * @param tags The tags to be removed from the item
    * @param user The user executing the item
    */
  def deleteItemStringTags(id: Long, tags: List[String], user: User)(implicit c: Option[Connection] = None): Unit = {
    if (tags.nonEmpty) {
      this.permission.hasAdminAccess(getItemTypeBasedOnTableName, user)(id)
      val lowerTags = tags.map(_.toLowerCase)
      this.withMRTransaction { implicit c =>
        val query =
          s"""DELETE FROM tags_on_${this.tableName} tt USING tags t
                            WHERE tt.tag_id = t.id AND tt.${this.name}_id = $id AND
                            t.name IN ({tags})"""
        SQL(query).on('tags -> lowerTags).execute()
      }
    }
  }

  /**
    * Links tags to a specific item. If the tags in the provided list do not exist then it will
    * create the new tags.
    *
    * @param id   The item id to update with
    * @param tags The tags to be applied to the item
    * @param user The user executing the item
    */
  def updateItemTagNames(id: Long, tags: List[String], user: User)(implicit c: Option[Connection] = None): Unit = {
    val tagIds = tags.filter(_.nonEmpty).flatMap { tag => {
      this.tagDAL.retrieveByName(tag) match {
        case Some(t) => Some(t.id)
        case None => Some(this.tagDAL.insert(Tag(-1, tag), user).id)
      }
    }
    }
    this.updateItemTags(id, tagIds, user)
  }

  /**
    * Updates the tags on the item. This maps the tag objects to the task objects in the database
    * through the use of a mapping table
    *
    * @param id           The id of the item to add the tags too
    * @param tags         A list of tags to add to the task
    * @param user         The user executing the task
    * @param completeList If complete list is true, then it will treat the tag list as if it is the
    *                     authoritative list, and any tags not in that list should be removed
    */
  def updateItemTags(id: Long, tags: List[Long], user: User, completeList: Boolean = false)(implicit c: Option[Connection] = None): Unit = {
    this.retrieveById(id) match {
      case Some(item) =>
        this.withMRTransaction { implicit c =>
          if (tags.nonEmpty) {
            if (completeList) {
              SQL"""DELETE FROM tags_on_#${this.tableName} WHERE #${name}_id = $id""".executeUpdate()
            }

            val indexedValues = tags.zipWithIndex
            val rows = indexedValues.map { case (_, i) =>
              s"({itemid_$i}, {tagid_$i})"
            }.mkString(",")
            val parameters = indexedValues.flatMap { case (value, i) =>
              Seq(
                NamedParameter(s"itemid_$i", ToParameterValue.apply[Long].apply(id)),
                NamedParameter(s"tagid_$i", ToParameterValue.apply[Long].apply(value))
              )
            }

            SQL(s"INSERT INTO tags_on_${this.tableName} (${name}_id, tag_id) VALUES " + rows + " ON CONFLICT DO NOTHING")
              .on(parameters: _*)
              .execute()
          }
          else if (completeList) {
            SQL"""DELETE FROM tags_on_#${this.tableName} WHERE #${name}_id = $id""".executeUpdate()
          }
        }
      case None =>
        throw new InvalidException(s"""Could not add tags [${tags.mkString(",")}]. Item [$id] Not Found.""")
    }
  }

  /**
    * Get a list of items based purely on the tags that are associated with the items
    *
    * @param tags   The list of tags to match
    * @param limit  The number of items to return
    * @param offset For paging, where 0 is the first page
    * @return A list of tags that have the tags
    */
  def getItemsBasedOnTags(tags: List[String], limit: Int, offset: Int)(implicit c: Option[Connection] = None): List[T] = {
    val lowerTags = tags.map(_.toLowerCase)
    this.withMRConnection { implicit c =>
      val sqlLimit = if (limit == -1) "ALL" else limit + ""
      val prefix = if (this.tableName == "tasks") {
        "t"
      } else {
        "c"
      }

      val query =
        s"""SELECT ${this.retrieveColumns} FROM ${this.tableName} $prefix
                      ${
          if (this.tableName == "tasks") {
            s"INNER JOIN challenges c ON c.id = ${this.tableName}.parent_id"
          } else {
            ""
          }
        }
                      INNER JOIN projects p ON p.id = $prefix.parent_id
                      INNER JOIN tags_on_${this.tableName} tt ON $prefix.id = tt.${this.name}_id
                      INNER JOIN tags tg ON tg.id = tt.tag_id
                      WHERE c.enabled = TRUE AND p.enabled = TRUE AND tg.name IN ({tags})
                      LIMIT $sqlLimit OFFSET {offset}"""
      SQL(query).on('tags -> ToParameterValue.apply[List[String]].apply(lowerTags), 'offset -> offset).as(this.parser.*)
    }
  }
}
