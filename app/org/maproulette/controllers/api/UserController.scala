// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.maproulette.exception.{InvalidException, NotFoundException, StatusMessage}
import org.maproulette.models.dal.ProjectDAL
import org.maproulette.models.{Challenge, Task}
import org.maproulette.session.dal.{UserDAL, UserGroupDAL}
import org.maproulette.session.{SessionManager, User, UserSettings}
import org.maproulette.utils.{Crypto, Utils}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author cuthbertm
  */
class UserController @Inject()(userDAL: UserDAL,
                               userGroupDAL: UserGroupDAL,
                               sessionManager: SessionManager,
                               projectDAL: ProjectDAL,
                               components: ControllerComponents,
                               bodyParsers: PlayBodyParsers,
                               crypto: Crypto) extends AbstractController(components) with DefaultWrites {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val userReadWrite = User.UserFormat
  implicit val challengeWrites = Challenge.writes.challengeWrites
  implicit val taskWrites = Task.TaskFormat
  implicit val userSearchResultWrites = User.searchResultWrites
  implicit val projectManagerWrites = User.projectManagerWrites

  def deleteUser(osmId: Long, anonymize: Boolean): Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser = true
    this.sessionManager.authenticatedRequest { implicit user =>
      if (anonymize) {
        this.userDAL.anonymizeUser(osmId, user)
      }
      // delete the user
      this.userDAL.deleteByOsmID(osmId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"User with osm ID $osmId deleted from the database${
        if (anonymize) {
          " and anonymized"
        } else {
          ""
        }
      }"))))
    }
  }

  def whoami(): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(user))
    }
  }

  def getUser(userId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      var result : Option[Result] = None

      user match {
        case Some(requestUser) =>
          // User can get itself
          if (userId == requestUser.id || userId == requestUser.osmProfile.id) {
            result = Option(Ok(Json.toJson(User.withDecryptedAPIKey(requestUser)(crypto))))
          }
          // Super User can get all users
          else if (requestUser.isSuperUser) {
            this.userDAL.retrieveByOSMID(userId, requestUser) match {
              case Some(u) => result = Option(Ok(Json.toJson(User.withDecryptedAPIKey(u)(crypto))))
              case None => this.userDAL.retrieveById(userId) match {
                case Some(u) => result = Option(Ok(Json.toJson(User.withDecryptedAPIKey(u)(crypto))))
                case None => throw new NotFoundException(s"No user found with id '$userId'")
              }
            }
          }
        case _ => None
      }

      result match {
        case Some(result) => result
        case _ =>
          // User is not logged in or Not a super user or is Not requesting itself
          // so we return a basic user object
          val target = this.userDAL.retrieveByOSMID(userId, User.superUser) match {
            case Some(u) => u
            case None => this.userDAL.retrieveById(userId) match {
              case Some(u) => u
              case None => throw new NotFoundException(s"No user found with id '$userId'")
            }
          }

          Ok(buildBasicUser(target))
      }
    }
  }

  def getUserByOSMUsername(username: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      var result : Option[Result] = None

      user match {
        case Some(u) =>
          if (u.name == username) {
            result = Option(Ok(Json.toJson(u)))
          }
          else if (u.isSuperUser) {
            this.userDAL.retrieveByOSMUsername(username, u) match {
              case Some(u) => result = Option(Ok(Json.toJson(u)))
              case None => throw new NotFoundException(s"No user found with OSM username '$username'")
            }
          }
        case _ => None
      }

      result match {
        case Some(r) => r
        case _ =>
          this.userDAL.retrieveByOSMUsername(username, User.superUser) match {
            case Some(u) => Ok(buildBasicUser(u))
            case None => throw new NotFoundException(s"No user found with OSM username '$username'")
          }
      }
    }
  }

  def searchUserByOSMUsername(username: String, limit: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (StringUtils.isEmpty(username)) {
        Ok(Json.toJson(List[JsValue]()))
      } else {
        Ok(Json.toJson(this.userDAL.searchByOSMUsername(username, limit)))
      }
    }
  }

  private def buildBasicUser(user: User): JsValue = {
    val avatar = user.osmProfile.avatarURL
    val displayName = user.osmProfile.displayName
    val leaderOptOut = user.settings.leaderboardOptOut.getOrElse(false)

    Json.obj("id" -> user.id,
                      "osmProfile" -> Json.obj("id" -> user.osmProfile.id,
                        "avatarURL" -> avatar, "displayName" -> displayName),
                      "name" -> user.name,
                      "created" -> user.created.toString,
                      "settings" -> Json.obj("leaderboardOptOut" -> leaderOptOut))
  }

  def updateUser(id: Long): Action[JsValue] = Action.async(bodyParsers.json) { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      implicit val settingsRead = User.settingsReads
      userDAL.managedUpdate(request.body.as[UserSettings], (request.body \ "properties").toOption, user)(id) match {
        case Some(u) => Ok(Json.toJson(u))
        case None => throw new NotFoundException(s"No user found to update with id '$id'")
      }
    }
  }

  /**
    * Action to refresh the user's OSM profile
    *
    * @return Ok Status with no content
    */
  def refreshProfile(osmUserId: Long): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedFutureRequest { implicit user =>
      val p = Promise[Result]
      this.userDAL.retrieveByOSMID(osmUserId, user) match {
        case Some(u) =>
          sessionManager.refreshProfile(u.osmProfile.requestToken, user) onComplete {
            case Success(result) => p success Ok
            case Failure(f) => p failure f
          }
        case None => p failure new NotFoundException(s"Failed to find any user with OSM User id [$osmUserId]")
      }
      p.future
    }
  }

  def getSavedChallenges(userId: Long, limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(this.userDAL.getSavedChallenges(userId, user, limit, offset)))
    }
  }

  def saveChallenge(userId: Long, challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.saveChallenge(userId, challengeId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Challenge $challengeId saved for user $userId"))))
    }
  }

  def unsaveChallenge(userId: Long, challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.unsaveChallenge(userId, challengeId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Challenge $challengeId unsaved from user $userId"))))
    }
  }

  def getSavedTasks(userId: Long, challengeIds: String, limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val ids = challengeIds.split(",").filter(_.nonEmpty) match {
        case v if v.nonEmpty => v.map(_.toLong).toSeq
        case _ => Seq.empty[Long]
      }
      Ok(Json.toJson(this.userDAL.getSavedTasks(userId, user, ids, limit, offset)))
    }
  }

  def saveTask(userId: Long, taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.saveTask(userId, taskId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Task $taskId saved for user $userId"))))
    }
  }

  def unsaveTask(userId: Long, taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      this.userDAL.unsaveTask(userId, taskId, user)
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Task $taskId unsaved from user $userId"))))
    }
  }

  /**
    * Add the user to the Admin group of a Project
    *
    * @param userId    The id of the User to add
    * @param projectId The project to add too
    * @param groupType The type of group 1 - Admin, 2 - Write, 3 - Read
    * @return Standard status message
    */
  def addUserToProject(userId: Long, projectId: Long, groupType: Int): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      this.addUser(userId, projectId, groupType, user)
      // clear group caches
      this.userGroupDAL.clearCache()
      Ok(Json.toJson(StatusMessage("OK", JsString(s"User with id [$userId] added to project $projectId"))))
    }
  }

  def addUsersToProject(projectId: Long, groupType: Int): Action[JsValue] = Action.async(bodyParsers.json) { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val jsBody = request.body
      if (!jsBody.isInstanceOf[JsArray]) {
        throw new InvalidException("Expecting JSON array of user id's for this request")
      }

      val idList = jsBody.as[JsArray].value
      idList.foreach(id => {
        this.addUser(id.as[Long], projectId, groupType, user)
      })
      // clear the group caches
      this.userGroupDAL.clearCache()
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Users with ids [${idList.mkString(",")} added to project $projectId"))))
    }
  }

  private def addUser(userId: Long, projectId: Long, groupType: Int, user: User): Unit = {
    val addUser = this.userDAL.retrieveById(userId) match {
      case Some(addUser) => addUser
      case None =>
        // check to see if the osm id was supplied instead
        this.userDAL.retrieveByOSMID(userId, user) match {
          case Some(u) => u
          case None => throw new NotFoundException(s"Could not find user with ID $userId")
        }
    }
    if (addUser.groups.exists(g => g.projectId == projectId && g.groupType == groupType)) {
      throw new InvalidException(s"User ${addUser.name} is already part of project $projectId")
    }
    // quick verification to make sure that the project exists
    this.projectDAL.retrieveById(projectId) match {
      case Some(_) => // just ignore
      case None => throw new NotFoundException(s"Could not find project with ID $projectId")
    }
    this.userDAL.addUserToProject(addUser.osmProfile.id, projectId, groupType, user)
  }

  /**
    * Sets the group type of the user in a project, first removing any prior
    * group types.
    *
    * @param userId    The id of the User to add
    * @param projectId The project to add too
    * @param groupType The type of group 1 - Admin, 2 - Write, 3 - Read
    * @return Standard status message
    */
  def setUserProjectGroup(userId: Long, projectId: Long, groupType: Int): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val addUser = this.userDAL.retrieveById(userId) match {
        case Some(addUser) => addUser
        case None =>
          // check to see if the osm id was supplied instead. Superuser promotion required.
          this.userDAL.retrieveByOSMID(userId, User.superUser) match {
            case Some(u) => u
            case None => throw new NotFoundException(s"Could not found user with ID $userId")
          }
      }
      this.userDAL.setUserProjectGroup(addUser.osmProfile.id, projectId, groupType, user)
      // clear group caches
      this.userGroupDAL.clearCache()
      Ok(Json.toJson(this.userDAL.getUsersManagingProject(projectId, None, user)))
    }
  }

  /**
    * Removes the user from the Admin group of the Project
    *
    * @param userId
    * @param projectId
    * @return
    */
  def removeUserFromProject(userId: Long, projectId: Long, groupType: Int): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      this.removeUser(userId, projectId, groupType, user)
      // clear group caches
      this.userGroupDAL.clearCache()
      Ok(Json.toJson(StatusMessage("OK", JsString(s"User with id $userId removed from project $projectId"))))
    }
  }

  private def removeUser(userId: Long, projectId: Long, groupType: Int, user: User): Unit = {
    val addUser = this.userDAL.retrieveById(userId) match {
      case Some(addUser) => addUser
      case None =>
        // check to see if the osm id was supplied instead. Superuser promotion required.
        this.userDAL.retrieveByOSMID(userId, User.superUser) match {
          case Some(u) => u
          case None => throw new NotFoundException(s"Could not find user with ID $userId")
        }
    }
    // just check to make sure that the project exists
    this.projectDAL.retrieveById(projectId) match {
      case Some(_) => // just ignore
      case None => throw new NotFoundException(s"Could not find project with ID $projectId")
    }
    this.userDAL.removeUserFromProject(addUser.osmProfile.id, projectId, groupType, user)
  }

  def removeUsersFromProject(projectId: Long, groupType: Int): Action[JsValue] = Action.async(bodyParsers.json) { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val jsBody = request.body
      if (!jsBody.isInstanceOf[JsArray]) {
        throw new InvalidException("Expecting JSON array of user id's for this request")
      }

      val idList = jsBody.as[JsArray].value
      idList.foreach(id => {
        this.removeUser(id.as[Long], projectId, groupType, user)
      })
      // clear the group caches
      this.userGroupDAL.clearCache()
      Ok(Json.toJson(StatusMessage("OK", JsString(s"Users with ids [${idList.mkString(",")} removed from project $projectId"))))
    }
  }

  /**
    * Generates a new API key for the user. A user can then use the API key to make API calls directly against
    * the server. Only the current API key for the user will work on any authenticated API calls, any previous
    * keys are immediately discarded once a new one is created.
    *
    * @return Will return NoContent if cannot create the key (which most likely means that no user was
    *         found, or will return the api key as plain text.
    */
  def generateAPIKey(userId: Long = -1): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val newAPIUser = if (user.isSuperUser && userId != -1) {
        this.userDAL.retrieveById(userId) match {
          case Some(u) => u
          case None => // look for the user under the OSM_ID
            this.userDAL.retrieveByOSMID(userId, user) match {
              case Some(u) => u
              case None => throw new NotFoundException(s"No user found with id [$userId], no API key could be generated.")
            }
        }
      } else {
        user
      }
      this.userDAL.generateAPIKey(newAPIUser, user) match {
        case Some(updated) => updated.apiKey match {
          case Some(api) => Ok(api)
          case None => NoContent
        }
        case None => NoContent
      }
    }
  }

  def getUsersManagingProject(projectId: Long, osmIds: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(this.userDAL.getUsersManagingProject(projectId, Utils.toLongList(osmIds), user)))
    }
  }

  def getMetricsForUser(userId: Long, monthDuration:Int = -1, reviewDuration:Int = -1): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.userDAL.getMetricsForUser(userId, User.userOrMocked(user), monthDuration, reviewDuration)))
    }
  }
}
