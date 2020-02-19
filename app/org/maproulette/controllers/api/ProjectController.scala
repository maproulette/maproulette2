// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.maproulette.controllers.ParentController
import org.maproulette.data.{ActionManager, ProjectType, TaskViewed}
import org.maproulette.models.dal.{CommentDAL, ProjectDAL, TaskDAL}
import org.maproulette.models.{Challenge, ClusteredPoint, Project}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc._

/**
  * The project controller handles all operations for the Project objects.
  * This includes CRUD operations and searching/listing.
  * See ParentController for more details on parent object operations
  * See CRUDController for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class ProjectController @Inject() (
    override val childController: ChallengeController,
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val dal: ProjectDAL,
    components: ControllerComponents,
    taskDAL: TaskDAL,
    commentDAL: CommentDAL,
    override val bodyParsers: PlayBodyParsers
) extends AbstractController(components)
    with ParentController[Project, Challenge] {

  // json reads for automatically reading Projects from a posted json body
  override implicit val tReads: Reads[Project] = Project.projectReads
  // json writes for automatically writing Projects to a json body response
  override implicit val tWrites: Writes[Project] = Project.projectWrites
  // json writes for automatically writing Challenges to a json body response
  override protected val cWrites: Writes[Challenge] = Challenge.writes.challengeWrites
  // json reads for automatically reading Challenges from a posted json body
  override protected val cReads: Reads[Challenge] = Challenge.reads.challengeReads
  // The type of object that this controller deals with.
  override implicit val itemType = ProjectType()

  implicit val writes = ClusteredPoint.clusteredPointWrites

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user: User): JsValue = {
    var jsonBody = super.updateCreateBody(body, user)
    jsonBody = Utils.insertIntoJson(jsonBody, "groups", Array.emptyShortArray)(arrayWrites[Short])
    jsonBody = Utils.insertIntoJson(jsonBody, "owner", user.osmProfile.id, true)(LongWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "deleted", false)(BooleanWrites)
    jsonBody = Utils.insertIntoJson(jsonBody, "featured", false)(BooleanWrites)
    Utils.insertIntoJson(jsonBody, "enabled", true)(BooleanWrites)
  }

  /**
    * We override the base function and force -1 as the parent, as projects do not have parents.
    */
  // scalastyle:on
  override def readByName(id: Long, name: String): Action[AnyContent] = super.readByName(-1, name)

  /**
    * Does a basic search on the name of an object
    *
    * @param search      The search string that we are looking for
    * @param limit       limit the number of returned items
    * @param offset      For paging, if limit is 10, total 100, then offset 1 will return items 11 - 20
    * @param onlyEnabled only enabled objects if true
    * @return A list of the requested items in JSON format
    */
  override def find(
      search: String,
      parentId: Long,
      limit: Int,
      offset: Int,
      onlyEnabled: Boolean
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(
        Json.toJson(
          this.dal
            .find(search, limit, offset, onlyEnabled, "display_name", "DESC")(parentId)
            .map(this.inject)
        )
      )
    }
  }

  /**
    * Retrieves the list of projects managed
    *
    * @param projectIds Comma separated list of project ids to fetch
    * @return json list of managed projects
    */
  def fetch(projectIds: String): Action[AnyContent] = Action.async { implicit response =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(this.dal.fetch(Utils.toLongList(projectIds))))
    }
  }

  /**
    * Retrieves the list of projects managed
    *
    * @param limit        Limit of how many tasks should be returned
    * @param offset       offset for pagination
    * @param onlyEnabled  Only list the enabled projects
    * @param onlyOwned    Only list the projects owned by this user
    * @param searchString basic search string to find specific projects
    * @param sort         An optional column to sort by.
    * @return json list of managed projects
    */
  def listManagedProjects(
      limit: Int,
      offset: Int,
      onlyEnabled: Boolean,
      onlyOwned: Boolean,
      searchString: String,
      sort: String = "display_name"
  ): Action[AnyContent] = Action.async { implicit response =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(
        Json.toJson(
          this.dal
            .listManagedProjects(user, limit, offset, onlyEnabled, onlyOwned, searchString, sort)
        )
      )
    }
  }

  /**
    * Gets the featured projects
    *
    * @param onlyEnabled Only include enabled projects
    * @param limit       The number of challenges to get
    * @param offset      The offset
    * @return A json array with the featured projects
    */
  def getFeaturedProjects(onlyEnabled: Boolean, limit: Int, offset: Int): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        Ok(Json.toJson(this.dal.getFeaturedProjects(onlyEnabled, limit, offset)))
      }
    }

  /**
    * Gets a random task that is an descendant of the project.
    *
    * @param limit       Limit of how many tasks should be returned
    * @param proximityId Id of task that you wish to find the next task based on the proximity of that task
    * @return A list of Tasks that match the supplied filters
    */
  def getRandomTasks(projectId: Long, limit: Int, proximityId: Long): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        SearchParameters.withSearch { params =>
          params.copy(projectIds = Some(List(projectId)))
          val result = this.taskDAL.getRandomTasks(
            User.userOrMocked(user),
            params,
            limit,
            None,
            Utils.negativeToOption(proximityId)
          )
          result.foreach(task =>
            this.actionManager
              .setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), "")
          )
          Ok(Json.toJson(result))
        }
      }
    }

  def getSearchedClusteredPoints(searchCookie: String): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        SearchParameters.withSearch { params =>
          Ok(Json.toJson(this.dal.getSearchedClusteredPoints(params)))
        }
      }
  }

  def getClusteredPoints(projectId: Long, challengeIds: String): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        val pid = if (projectId < 0) {
          None
        } else {
          Some(projectId)
        }
        val cids = if (StringUtils.isEmpty(challengeIds)) {
          List.empty
        } else {
          Utils.split(challengeIds).map(_.toLong)
        }
        Ok(Json.toJson(this.dal.getClusteredPoints(pid, cids)))
      }
  }

  /**
    * Retrieve all the comments for a specific project
    *
    * @param projectId The id of the challenge
    * @return A list of comments that exist for a specific challenge
    */
  def retrieveComments(projectId: Long, limit: Int, page: Int): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        Ok(
          Json.toJson(
            this.commentDAL.retrieveComments(List(projectId), List.empty, List.empty, limit, page)
          )
        )
      }
  }
}
