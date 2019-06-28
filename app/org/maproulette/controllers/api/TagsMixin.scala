// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import org.apache.commons.lang3.StringUtils
import org.maproulette.controllers.CRUDController
import org.maproulette.data._
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.models.dal.{TagDAL, TagDALMixin}
import org.maproulette.models.{BaseObject, Tag}
import org.maproulette.session.User
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}

/**
  * @author cuthbertm
  */
trait TagsMixin[T <: BaseObject[Long]] {

  this: CRUDController[T] =>

  def tagDAL: TagDAL

  def dalWithTags: TagDALMixin[T]

  /**
    * Gets tasks based on tags, this is regardless of the project or challenge parents.
    *
    * @param tags   A comma separated list of tags to match against
    * @param limit  The number of tasks to return
    * @param offset The paging offset, incrementing will take you to the next set in the list
    * @return The html Result containing a json array of the found tasks
    */
  def getItemsBasedOnTags(tags: String, limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        Ok(Json.toJson(this.dalWithTags.getItemsBasedOnTags(tags.split(",").toList, limit, offset)))
      }
    }
  }

  /**
    * Deletes tags from a given item.
    * Must be authenticated to perform operation
    *
    * @param id   The id of the task
    * @param tags A comma separated list of tags to delete
    * @return
    */
  def deleteTagsFromItem(id: Long, tags: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (StringUtils.isEmpty(tags)) {
        Utils.badRequest("A comma separated list of tags need to be provided via the query string. Example: ?tags=tag1,tag2")
      } else {
        MPExceptionUtil.internalExceptionCatcher { () =>
          val tagList = tags.split(",").toList
          if (tagList.nonEmpty) {
            this.dalWithTags.deleteItemStringTags(id, tagList, user)
            this.actionManager.setAction(Some(user), this.itemType.convertToItem(id), TagRemoved(), tags)
          }
          NoContent
        }
      }
    }
  }

  /**
    * Add tags to a given item.
    * Must be authenticated to perform operation
    *
    * @param id   The id of the item
    * @param tags A comma separated list of tags to add
    * @param user the user executing the request
    */
  def addTagstoItem(id:Long, tags:List[Tag], user:User): Unit = {
    val tagIds = this.tagDAL.updateTagList(tags, user).map(_.id)

    if (tagIds.nonEmpty) {
      // now we have the ids for the supplied tags, then lets map them to the item created
      this.dalWithTags.updateItemTags(id, tagIds, user)
      this.actionManager.setAction(Some(user), this.itemType.convertToItem(id), TagAdded(), tagIds.mkString(","))
    }
  }

  /**
    * In this function the task will extract any tags that are supplied with the create json, it will
    * then attempt to create or update the associated tags. The tags can be supplied in 3 different
    * formats:
    * 1. comma separated list of tag names
    * 2. array of full json object structure containing id (optional), name and description of tag
    * 3. comma separated list of tag ids
    *
    * @param body          The Json body of data
    * @param createdObject The Task that was created by the create function
    * @param user          the user executing the request
    */
  def extractTags(body: JsValue, createdObject: T, user: User, completeList: Boolean = false): Unit = {
    val tags: List[Tag] = body \ "tags" match {
      case tags: JsDefined =>
        // this case is for a comma separated list, either of ints or strings
        tags.as[String].split(",").toList.map(tag => {
          try {
            Tag(tag.toLong, "")
          } catch {
            case e: NumberFormatException =>
              // this is the case where a name is supplied, so we will either search for a tag with
              // the same name or create a new tag with the current name
              this.tagDAL.retrieveByName(tag) match {
                case Some(t) => t.asInstanceOf[Tag]
                case None => Tag(-1, tag)
              }
          }
        })
      case tags: JsUndefined =>
        (body \ "fulltags").asOpt[List[JsValue]] match {
          case Some(tagList) =>
            tagList.map(value => {
              val identifier = (value \ "id").asOpt[Long].getOrElse(-1L)
              val name = (value \ "name").asOpt[String].getOrElse("")
              val description = (value \ "description").asOpt[String]
              Tag(identifier, name, description)
            })
          case None => List.empty
        }
      case _ => List.empty
    }
    val tagIds = this.tagDAL.updateTagList(tags, user).map(_.id)

    if (tagIds.nonEmpty || completeList) {
      // now we have the ids for the supplied tags, then lets map them to the item created
      this.dalWithTags.updateItemTags(createdObject.id, tagIds, user, completeList)
      this.actionManager.setAction(Some(user), this.itemType.convertToItem(createdObject.id), TagAdded(), tagIds.mkString(","))
    }
  }

  /**
    * Gets the tags for either the challenge or the task depending on what controller is using the mixin
    *
    * @param id The id of the object you are looking for
    * @return A list of tags associated with the item
    */
  def getTags(id: Long): List[Tag] = {
    this.itemType match {
      case ChallengeType() => this.tagDAL.listByChallenge(id)
      case SurveyType() => this.tagDAL.listByChallenge(id)
      case TaskType() => this.tagDAL.listByTask(id)
      case _ => List.empty
    }
  }
}
