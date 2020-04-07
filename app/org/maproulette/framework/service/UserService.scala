/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import java.util.UUID

import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.locationtech.jts.geom.{Coordinate, GeometryFactory}
import org.locationtech.jts.io.WKTWriter
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.data.UserType
import org.maproulette.exception.NotFoundException
import org.maproulette.framework.model._
import org.maproulette.framework.psql._
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.repository.{UserRepository, UserSavedObjectsRepository}
import org.maproulette.models.Task
import org.maproulette.models.dal.TaskDAL
import org.maproulette.permissions.Permission
import org.maproulette.utils.{Crypto, Utils, Writers}
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.oauth.RequestToken

/**
  * @author mcuthbert
  */
@Singleton
class UserService @Inject() (
    repository: UserRepository,
    savedObjectsRepository: UserSavedObjectsRepository,
    groupService: GroupService,
    projectService: ProjectService,
    taskDAL: TaskDAL,
    config: Config,
    permission: Permission,
    crypto: Crypto
) extends ServiceMixin[User]
    with Writers {
  // The cache manager for the users
  val cacheManager = new CacheManager[Long, User](config, Config.CACHE_ID_USERS)

  /**
    * Find the User based on an API key, the API key is unique in the database.
    *
    * @param apiKey The APIKey to match against
    * @param id     The id of the user
    * @return The matched user, None if User not found
    */
  def retrieveByAPIKey(id: Long, apiKey: String, user: User): Option[User] =
    this.cacheManager.withOptionCaching { () =>
      val idFilterGroup = FilterGroup(
        OR(),
        BaseParameter(User.FIELD_ID, id),
        BaseParameter(User.FIELD_OSM_ID, id)
      )
      val apiFilterGroup = FilterGroup(AND(), BaseParameter(User.FIELD_API_KEY, apiKey))
      val query          = Query(Filter(AND(), idFilterGroup, apiFilterGroup))

      this.repository.query(query).headOption match {
        case Some(u) =>
          this.permission.hasObjectReadAccess(u, user)
          Some(u)
        case None => None
      }
    }

  def retrieveByUsernameAndAPIKey(username: String, apiKey: String): Option[User] =
    this.cacheManager.withOptionCaching { () =>
      this.repository
        .query(
          Query.simple(
            List(
              BaseParameter(User.FIELD_NAME, username),
              BaseParameter(User.FIELD_API_KEY, apiKey)
            )
          )
        )
        .headOption
    }

  /**
    * Helper function to allow users to be retrieve by just the OSM username, for security we only
    * allow super users access to this function
    *
    * @param username The username that is being searched for
    * @param user     The user making the request
    * @return An optional user object, if none then not found
    */
  def retrieveByOSMUsername(username: String, user: User): Option[User] =
    this.cacheManager.withOptionCaching { () =>
      // only execute this kind of request if the user is a super user
      if (user.isSuperUser) {
        this
          .query(
            Query
              .simple(
                List(BaseParameter(User.FIELD_NAME, username, Operator.ILIKE))
              ),
            user
          )
          .headOption
      } else {
        throw new IllegalAccessException(
          "Only Superuser allowed to look up users by just OSM username"
        )
      }
    }

  def query(query: Query, user: User): List[User] = this.repository.query(query)

  /**
    * Allow users to search for other users by OSM username.
    *
    * @param username The username or username fragment to search for.
    * @param paging    The maximum number of results to retrieve.
    * @return A (possibly empty) list of UserSearchResult objects.
    */
  def searchByOSMUsername(
      username: String,
      paging: Paging = Paging()
  ): List[User] = {
    // ordering previously was done like "ORDER BY (users.name ILIKE '${username}') DESC, users.name"
    // which is somewhat strange and not currently supported by the Query system, so leaving out and
    // seeing what will be the result of that.
    this.repository
      .query(
        Query.simple(
          List(
            BaseParameter(User.FIELD_NAME, SQLUtils.search(username), Operator.ILIKE)
          ),
          paging = paging,
          order = Order(List(User.FIELD_NAME))
        )
      )
  }

  /**
    * Match the user based on the token, secret and id for the user.
    *
    * @param id           The id of the user
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestToken(id: Long, requestToken: RequestToken, user: User): Option[User] = {
    val requestedUser = this.cacheManager.withCaching { () =>
      this
        .query(
          Query.simple(
            List(
              FilterParameter.conditional(User.FIELD_ID, id, includeOnlyIfTrue = id > 0),
              BaseParameter(User.FIELD_OAUTH_TOKEN, requestToken.token),
              BaseParameter(User.FIELD_OAUTH_SECRET, requestToken.secret)
            )
          ),
          user
        )
        .headOption
    }(id = id)
    requestedUser match {
      case Some(u) =>
        // double check that the token and secret still match, in case it came from the cache
        if (StringUtils.equals(u.osmProfile.requestToken.token, requestToken.token) &&
            StringUtils.equals(u.osmProfile.requestToken.secret, requestToken.secret)) {
          this.permission.hasObjectReadAccess(u, user)
          Some(u)
        } else {
          None
        }
      case None => None
    }
  }

  /**
    * "Upsert" function that will insert a new user into the database, if the user already exists in
    * the database it will simply update the user with new information. A user is considered to exist
    * in the database if the id or osm_id is found in the users table. During an insert any groups
    * for the user will be ignored, to update groups or add/remove groups, the specific functions
    * for that must be used. Or the update method which has a specific mechanism for updating
    * groups
    *
    * @param user The user to update
    * @return None if failed to update or create.
    */
  def create(item: User, user: User): User = {
    this.cacheManager.withOptionCaching { () =>
      this.permission.hasObjectAdminAccess(item, user)
      val newAPIKey = crypto.encrypt(this.generateAPIKey)
      val ewkt = new WKTWriter().write(
        new GeometryFactory().createPoint(
          new Coordinate(
            item.osmProfile.homeLocation.latitude,
            item.osmProfile.homeLocation.longitude
          )
        )
      )
      this.repository.upsert(item, newAPIKey, ewkt)

      // just in case expire the osm ID
      this.groupService.clearCache(osmId = item.osmProfile.id)

      // We do this separately from the transaction because if we don't the user_group mappings
      // wont be accessible just yet.
      val retUser = this.retrieveByOSMId(item.osmProfile.id).head

      // now update the groups by adding any new groups, from the supplied user
      val newGroups = item.groups.filter(g => !retUser.groups.exists(_.id == g.id))
      newGroups.foreach(g => this.groupService.addUserToGroup(item.osmProfile.id, g, User.superUser)
      )
      Some(retUser.copy(groups = newGroups))
    }.get
  }

  /**
    * Generates a new API key for the user
    *
    * @param apiKeyUser The user that is requesting that their key be updated.
    * @return An optional variable that will contain the updated user if successful
    */
  def generateAPIKey(apiKeyUser: User, user: User): Option[User] = {
    this.permission.hasAdminAccess(UserType(), user)(apiKeyUser.id)
    this.cacheManager
      .withUpdatingCache(this.retrieve) { implicit cachedItem =>
        val newAPIKey = crypto.encrypt(this.generateAPIKey)
        Some(this.repository.updateAPIKey(apiKeyUser.id, newAPIKey))
      }(id = apiKeyUser.id)
  }

  private def generateAPIKey: String = UUID.randomUUID().toString

  /**
    * Retrieves an object of that type
    *
    * @param id The identifier for the object
    * @return An optional object, None if not found
    */
  override def retrieve(id: Long): Option[User] = this.retrieveListById(List(id)).headOption

  /**
    * Retrieves a list of objects from the supplied list of ids. Will check for any objects currently
    * in the cache and those that aren't will be retrieved from the database
    *
    * @param ids The list of ids to be retrieved
    * @param paging paging object to handle paging in response
    * @return A list of objects, empty list if none found
    */
  def retrieveListById(ids: List[Long], paging: Paging = Paging()): List[User] = {
    if (ids.isEmpty) {
      List.empty
    } else {
      this.cacheManager.withIDListCaching { implicit uncachedIDs =>
        this.query(
          Query.simple(
            List(BaseParameter(User.FIELD_ID, uncachedIDs, Operator.IN)),
            paging = paging
          ),
          User.superUser
        )
      }(ids = ids)
    }
  }

  /**
    * Retrieves all the objects based on the search criteria
    *
    * @param query The query to match against to retrieve the objects
    * @return The list of objects
    */
  override def query(query: Query): List[User] = ???

  /**
    * This is a specialized update that is accessed via the API that allows users to update only the
    * fields that are available for update. This is so that there is no accidental update of OSM username
    * or anything retrieved from the OSM API.
    *
    * The function will simply recreate a JSON object with only the allowed fields. Any APIKey's must
    * be updated separately
    *
    * @param settings   The user settings that have been pulled from the request object
    * @param properties Any extra properties that a client wishes to store alongside the user object
    * @param user       The user making the update request
    * @param id         The id of the user being updated
    * @return An optional user, if user with supplied ID not found, then will return empty optional
    */
  def managedUpdate(
      id: Long,
      settings: UserSettings,
      properties: Option[JsValue],
      user: User
  ): Option[User] = {
    val updateBody = Utils.insertIntoJson(Json.parse("{}"), "settings", Json.toJson(settings))
    this.update(id, properties match {
      case Some(p) => Utils.insertIntoJson(updateBody, "properties", JsString(p.toString()))
      case None    => updateBody
    }, user)
  }

  /**
    * Only certain values are allowed to be updated for the user. Namely apiKey, displayName,
    * description, avatarURL, token, secret and theme.
    *
    * @param value The json object containing the fields to update
    * @param id    The id of the user to update
    * @return The user that was updated, None if no user was found with the id
    */
  def update(id: Long, value: JsValue, user: User): Option[User] = {
    this.cacheManager.withUpdatingCache(this.retrieve) { implicit cachedItem =>
      this.permission.hasObjectAdminAccess(cachedItem, user)
      val displayName = (value \ "osmProfile" \ "displayName")
        .asOpt[String]
        .getOrElse(cachedItem.osmProfile.displayName)
      val description = (value \ "osmProfile" \ "description")
        .asOpt[String]
        .getOrElse(cachedItem.osmProfile.description)
      val avatarURL = (value \ "osmProfile" \ "avatarURL")
        .asOpt[String]
        .getOrElse(cachedItem.osmProfile.avatarURL)
      val token = (value \ "osmProfile" \ "token")
        .asOpt[String]
        .getOrElse(cachedItem.osmProfile.requestToken.token)
      val secret = (value \ "osmProfile" \ "secret")
        .asOpt[String]
        .getOrElse(cachedItem.osmProfile.requestToken.secret)
      // todo: allow to insert in WKT, WKB or latitude/longitude
      val latitude = (value \ "osmProfile" \ "homeLocation" \ "latitude")
        .asOpt[Double]
        .getOrElse(cachedItem.osmProfile.homeLocation.latitude)
      val longitude = (value \ "osmProfile" \ "homeLocation" \ "longitude")
        .asOpt[Double]
        .getOrElse(cachedItem.osmProfile.homeLocation.longitude)
      val ewkt = new WKTWriter()
        .write(new GeometryFactory().createPoint(new Coordinate(latitude, longitude)))
      val defaultEditor = (value \ "settings" \ "defaultEditor")
        .asOpt[Int]
        .getOrElse(cachedItem.settings.defaultEditor.getOrElse(-1))
      val defaultBasemap = (value \ "settings" \ "defaultBasemap")
        .asOpt[Int]
        .getOrElse(cachedItem.settings.defaultBasemap.getOrElse(-1))
      val defaultBasemapId = (value \ "settings" \ "defaultBasemapId")
        .asOpt[String]
        .getOrElse(cachedItem.settings.defaultBasemapId.getOrElse(""))
      val customBasemap = (value \ "settings" \ "customBasemap")
        .asOpt[String]
        .getOrElse(cachedItem.settings.customBasemap.getOrElse(""))
      val locale = (value \ "settings" \ "locale")
        .asOpt[String]
        .getOrElse(cachedItem.settings.locale.getOrElse("en"))
      val email = (value \ "settings" \ "email")
        .asOpt[String]
        .getOrElse(cachedItem.settings.email.getOrElse(""))
      val emailOptIn = (value \ "settings" \ "emailOptIn")
        .asOpt[Boolean]
        .getOrElse(cachedItem.settings.emailOptIn.getOrElse(false))
      val leaderboardOptOut = (value \ "settings" \ "leaderboardOptOut")
        .asOpt[Boolean]
        .getOrElse(cachedItem.settings.leaderboardOptOut.getOrElse(false))
      var needsReview = (value \ "settings" \ "needsReview")
        .asOpt[Int]
        .getOrElse(cachedItem.settings.needsReview.getOrElse(config.defaultNeedsReview))
      val isReviewer = (value \ "settings" \ "isReviewer")
        .asOpt[Boolean]
        .getOrElse(cachedItem.settings.isReviewer.getOrElse(false))
      val theme = (value \ "settings" \ "theme")
        .asOpt[Int]
        .getOrElse(cachedItem.settings.theme.getOrElse(-1))
      val properties =
        (value \ "properties").asOpt[String].getOrElse(cachedItem.properties.getOrElse("{}"))

      // If this user always requires a review, then they are not allowed to change it (except super users)
      if (user.settings.needsReview.getOrElse(0) == User.REVIEW_MANDATORY) {
        if (!user.isSuperUser) {
          needsReview = User.REVIEW_MANDATORY
        }
      }

      this.groupService.clearCache(osmId = cachedItem.osmProfile.id)
      Some(
        this.repository.update(
          User(
            id,
            DateTime.now(),
            DateTime.now(),
            OSMProfile(
              -1,
              displayName,
              description,
              avatarURL,
              null,
              DateTime.now(),
              RequestToken(token, secret)
            ),
            settings = UserSettings(
              Some(defaultEditor),
              Some(defaultBasemap),
              Some(defaultBasemapId),
              Some(customBasemap),
              Some(locale),
              Some(email),
              Some(emailOptIn),
              Some(leaderboardOptOut),
              Some(needsReview),
              Some(isReviewer),
              Some(theme)
            ),
            properties = Some(properties)
          ),
          ewkt
        )
      )

    }(id = id)
  }

  /**
    * Deletes a user from the database based on a specific user id
    *
    * @param id The user to delete
    * @return The rows that were deleted
    */
  def delete(id: Long, user: User): Boolean = {
    this.permission.hasSuperAccess(user)
    retrieve(id) match {
      case Some(u) => this.groupService.clearCache(osmId = u.osmProfile.id)
      case None    => //no user, so can just ignore
    }
    this.repository.delete(id)
  }

  /**
    * Delete a user based on their OSM ID
    *
    * @param osmId The OSM ID for the user
    * @param user  The user deleting the user
    * @return
    */
  def deleteByOsmID(osmId: Long, user: User): Boolean = {
    this.permission.hasSuperAccess(user)
    // expire the user group cache
    this.groupService.clearCache(osmId = osmId)
    this.cacheManager.withCacheIDDeletion { () =>
      this.repository.deleteByOSMID(osmId)
    }(ids = List(osmId))
  }

  /**
    * Anonymizes all user data in the database
    *
    * @param osmId The OSM id of the user you wish to anonymize
    * @param user  The user requesting action, can only be a super user
    */
  def anonymizeUser(osmId: Long, user: User): Unit = {
    this.permission.hasSuperAccess(user)
    this.repository.anonymizeUser(osmId)
  }

  /**
    * Removes a user from a project
    *
    * @param osmId     The OSM ID of the user
    * @param projectId The id of the project to remove the user from
    * @param groupType The type of group to remove -1 - all, 1 - Admin, 2 - Write, 3 - Read
    * @param user      The user making the request
    */
  def removeUserFromProject(osmId: Long, projectId: Long, groupType: Int, user: User): Unit = {
    this.permission.hasProjectAccess(this.projectService.retrieve(projectId), user)
    this.groupService.clearCache(osmId = osmId)
    this.cacheManager
      .withUpdatingCache(this.retrieveByOSMId) { cachedUser =>
        this.groupService.removeUserFromProjectGroups(osmId, projectId, groupType, User.superUser)
        Some(
          cachedUser.copy(groups = this.groupService.retrieveUserGroups(osmId, User.superUser))
        )
      }(id = osmId)
      .get
  }

  /**
    * Find the user based on the user's osm ID. If found on cache, will return cached object
    * instead of hitting the database
    *
    * @param id   The user's osm ID
    * @return The matched user, None if User not found
    */
  def retrieveByOSMId(id: Long): Option[User] =
    this.cacheManager.withOptionCaching { () =>
      this
        .query(Query.simple(List(BaseParameter(User.FIELD_OSM_ID, id))), User.superUser)
        .headOption
    }

  /**
    * Initializes the home project for the user. If the project already exists, then we are
    * good.
    *
    * @param user The user to initialize the home project
    */
  def initializeHomeProject(user: User): User = {
    val homeName = s"Home_${user.osmProfile.id}"
    val homeProjectId = this.projectService.retrieveByName(homeName) match {
      case Some(project) => project.id
      case None =>
        this.projectService
          .create(
            Project(
              id = -1,
              owner = user.osmProfile.id,
              name = homeName,
              created = DateTime.now(),
              modified = DateTime.now(),
              description = Some(s"Home project for user ${user.name}"),
              displayName = Some(s"${user.osmProfile.displayName}'s Project")
            ),
            user
          )
          .id
    }
    // make sure the user is an admin of this project
    if (!user.groups.exists(g => g.projectId == homeProjectId)) {
      this.addUserToProject(user.osmProfile.id, homeProjectId, Group.TYPE_ADMIN, User.superUser)
    } else {
      user
    }
  }

  /**
    * Adds a user to a project
    *
    * @param osmId     The OSM ID of the user to add to the project
    * @param projectId The project that user is being added too
    * @param groupType The type of group to add 1 - Admin, 2 - Write, 3 - Read
    * @param user      The user that is adding the user to the project
    */
  def addUserToProject(
      osmId: Long,
      projectId: Long,
      groupType: Int,
      user: User,
      clear: Boolean = false
  ): User = {
    this.permission.hasProjectAccess(this.projectService.retrieve(projectId), user)
    // expire the user group cache
    this.groupService.clearCache(osmId = osmId)
    this.verifyProjectGroups(projectId)
    this.cacheManager
      .withUpdatingCache(this.retrieveByOSMId) { cachedUser =>
        if (clear) {
          this.groupService.removeUserFromProjectGroups(osmId, projectId, -1, User.superUser)
        }
        this.groupService.addUserToProject(osmId, groupType, projectId, User.superUser)
        Some(cachedUser.copy(groups = this.groupService.retrieveUserGroups(osmId, User.superUser)))
      }(id = osmId)
      .get
  }

  /**
    * This function will quickly verify that the project groups have been created correctly and if not,
    * then it will create them
    *
    * @param projectId The id of the project you are checking
    */
  def verifyProjectGroups(projectId: Long): Unit = {
    this.projectService.clearCache(projectId)
    this.projectService.retrieve(projectId) match {
      case Some(p) =>
        val groups = p.groups
        // must contain at least 1 admin group, 1 write group and 1 read group
        if (groups.count(_.groupType == Group.TYPE_ADMIN) < 1) {
          this.groupService.create(projectId, Group.TYPE_ADMIN, User.superUser)
        }
        if (groups.count(_.groupType == Group.TYPE_WRITE_ACCESS) < 1) {
          this.groupService.create(projectId, Group.TYPE_WRITE_ACCESS, User.superUser)
        }
        if (groups.count(_.groupType == Group.TYPE_READ_ONLY) < 1) {
          this.groupService.create(projectId, Group.TYPE_READ_ONLY, User.superUser)
        }
      case None => throw new NotFoundException(s"No project found with id $projectId")
    }
  }

  /**
    * Retrieves the user's home project
    *
    * @param user The user to search for the home project
    * @return
    */
  def getHomeProject(user: User): Project = {
    val homeName = s"Home_${user.osmProfile.id}"
    this.projectService.retrieveByName(homeName) match {
      case Some(project) => project
      case None =>
        throw new NotFoundException(
          "You should never get this exception, Home project should always exist for user."
        )
    }
  }

  /**
    * Gets the last X saved challenges for a user
    *
    * @param userId The id of the user you are requesting the saved challenges for
    * @param user   The user making the request
    * @param paging paging object to handle paging in response
    * @return a List of challenges
    */
  def getSavedChallenges(
      userId: Long,
      user: User,
      paging: Paging = Paging()
  ): List[Challenge] = {
    this.permission.hasReadAccess(UserType(), user)(userId)
    this.savedObjectsRepository.getSavedChallenges(userId)
  }

  /**
    * Saves a challenge to a users profile
    *
    * @param userId The id of the user to save the challenge too
    * @param challengeId The id of the challenge to save
    * @param user The user making the actual request
    */
  def saveChallenge(userId: Long, challengeId: Long, user: User): Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    this.savedObjectsRepository.saveChallenge(userId, challengeId)
  }

  /**
    * "Unsave" a challenge from a users profile
    *
    * @param userId The id of the user to unsave the challenge from
    * @param challengeId The id of the challenge to unsave
    * @param user The user making the actual request
    */
  def unsaveChallenge(userId: Long, challengeId: Long, user: User): Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    this.savedObjectsRepository.unsaveChallenge(userId, challengeId)
  }

  /**
    * Retrieve all the tasks that have been saved
    *
    * @param userId The id of the user to get all the tasks for
    * @param user The user making the actual request
    * @param challengeIds A list of challenge ids that we want to filter on
    * @param paging paging object to handle paging in response
    * @return A list of Tasks that have been saved to the users profile
    */
  def getSavedTasks(
      userId: Long,
      user: User,
      challengeIds: Seq[Long] = Seq.empty,
      paging: Paging = Paging()
  ): List[Task] = {
    this.permission.hasReadAccess(UserType(), user)(userId)
    this.savedObjectsRepository.getSavedTasks(userId, challengeIds, paging)
  }

  /**
    * Saves the task for the user, will validate that the task actually exists first based on the
    * provided id
    *
    * @param userId The id of the user
    * @param taskId the id of the task
    * @param user   the user executing the request
    */
  def saveTask(userId: Long, taskId: Long, user: User): Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    this.taskDAL.retrieveById(taskId) match {
      case Some(task) => this.savedObjectsRepository.saveTask(userId, task.id, task.parent)
      case None       => throw new NotFoundException(s"No task found with ID $taskId")
    }
  }

  /**
    * Unsaves a task from the users profile
    *
    * @param userId  The id of the user that has previously saved the challenge
    * @param taskId The id of the task to remove from the user profile
    * @param user    The user executing the unsave function
    */
  def unsaveTask(userId: Long, taskId: Long, user: User): Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    this.savedObjectsRepository.unsaveTask(userId, taskId)
  }

  /**
    * Retrieve list of all users possessing a group type for the project.
    *
    * @param projectId   The project
    * @param osmIdFilter : A filter for manager OSM ids
    * @param user        The user making the request
    * @return A list of ProjectManager objects.
    */
  def getUsersManagingProject(
      projectId: Long,
      osmIdFilter: Option[List[Long]] = None,
      user: User
  ): List[ProjectManager] = {
    this.permission
      .hasProjectAccess(this.projectService.retrieve(projectId), user, Group.TYPE_READ_ONLY)
    this.repository.getUsersManagingProject(projectId, osmIdFilter)
  }

  /**
    * Clears the users cache
    *
    * @param id If id is supplied will only remove the project with that id
    */
  def clearCache(id: Long = -1): Unit = {
    if (id > -1) {
      this.cacheManager.cache.remove(id)
    } else {
      this.cacheManager.clearCaches
    }
  }
}
