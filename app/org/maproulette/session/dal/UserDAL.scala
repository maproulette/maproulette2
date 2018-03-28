// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.session.dal

import java.sql.Connection
import javax.inject.Inject
import javax.inject.Singleton

import anorm._
import anorm.SqlParser._
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Point}
import com.vividsolutions.jts.io.{WKTReader, WKTWriter}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.actions.UserType
import org.maproulette.models.dal.{BaseDAL, ChallengeDAL, ProjectDAL, TaskDAL}
import org.maproulette.session._
import play.api.db.Database
import org.maproulette.cache.CacheManager
import org.maproulette.exception.NotFoundException
import org.maproulette.models.{Challenge, Project, Task}
import org.maproulette.permissions.Permission
import org.maproulette.utils.Utils
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.oauth.RequestToken

/**
  * The data access layer for the user object. This is considered a special object in the system,
  * as it does not use the baseObject for the user class and does not rely on the BaseDAL like all
  * the other objects. This is somewhat related to how the id's for the User are generated and used.
  *
  * TODO: This object should be locked down more than it currently is. Currently although you cannot
  * write to any of the objects without super user access, you can list all the users, which is
  * definitely not desirable, so will need to block any listing access unless you are a super user.
  *
  * @author cuthbertm
  */
@Singleton
class UserDAL @Inject() (override val db:Database,
                         userGroupDAL: UserGroupDAL,
                         projectDAL:ProjectDAL,
                         challengeDAL: ChallengeDAL,
                         taskDAL: TaskDAL,
                         config:Config,
                         override val permission:Permission) extends BaseDAL[Long, User] {

  import org.maproulette.utils.AnormExtension._

  // The cache manager for the users
  override val cacheManager = new CacheManager[Long, User]
  override val tableName = "users"
  override val retrieveColumns: String = "*, ST_AsText(users.home_location) AS home"

  // The anorm row parser to convert user records from the database to user objects
  val parser: RowParser[User] = {
    get[Long]("users.id") ~
      get[Long]("users.osm_id") ~
      get[DateTime]("users.created") ~
      get[DateTime]("users.modified") ~
      get[DateTime]("users.osm_created") ~
      get[String]("users.name") ~
      get[Option[String]]("users.description") ~
      get[Option[String]]("users.avatar_url") ~
      get[Option[String]]("home") ~
      get[Option[String]]("users.api_key") ~
      get[String]("users.oauth_token") ~
      get[String]("users.oauth_secret") ~
      get[Option[Int]]("users.default_editor") ~
      get[Option[Int]]("users.default_basemap") ~
      get[Option[String]]("users.custom_basemap_url") ~
      get[Option[Boolean]]("users.email_opt_in") ~
      get[Option[String]]("users.locale") ~
      get[Option[Int]]("users.theme") ~
      get[Option[String]]("properties") map {
      case id ~ osmId ~ created ~ modified ~ osmCreated ~ displayName ~ description ~ avatarURL ~
        homeLocation ~ apiKey ~ oauthToken ~ oauthSecret ~ defaultEditor ~ defaultBasemap ~
        customBasemap ~ emailOptIn ~ locale ~ theme ~ properties =>
        val locationWKT = homeLocation match {
          case Some(wkt) => new WKTReader().read(wkt).asInstanceOf[Point]
          case None => new GeometryFactory().createPoint(new Coordinate(0, 0))
        }
        // If the modified date is too old, then lets update this user information from OSM
        new User(id, created, modified,
          OSMProfile(osmId, displayName, description.getOrElse(""), avatarURL.getOrElse(""),
            Location(locationWKT.getX, locationWKT.getY), osmCreated, RequestToken(oauthToken, oauthSecret)),
            userGroupDAL.getUserGroups(osmId, User.superUser
          ),
          apiKey, false,
          UserSettings(defaultEditor, defaultBasemap, customBasemap, locale, emailOptIn, theme),
          properties
        )
    }
  }

  /**
    * Find the user based on the user's osm ID. If found on cache, will return cached object
    * instead of hitting the database
    *
    * @param id The user's osm ID
    * @param user The user making the request
    * @return The matched user, None if User not found
    */
  def retrieveByOSMID(implicit id: Long, user:User): Option[User] = this.cacheManager.withOptionCaching { () =>
    this.db.withConnection { implicit c =>
      val query = s"""SELECT ${this.retrieveColumns} FROM users WHERE osm_id = {id}"""
      SQL(query).on('id -> id).as(this.parser.*).headOption match {
        case Some(u) =>
          this.permission.hasObjectReadAccess(u, user)
          Some(u)
        case None => None
      }
    }
  }

  /**
    * Find the User based on an API key, the API key is unique in the database.
    *
    * @param apiKey The APIKey to match against
    * @param id The id of the user
    * @return The matched user, None if User not found
    */
  def retrieveByAPIKey(apiKey:String, user:User)(implicit id:Long) : Option[User] = this.cacheManager.withOptionCaching { () =>
    this.db.withConnection { implicit c =>
      val query = s"""SELECT ${this.retrieveColumns} FROM users WHERE (id = {id} OR osm_id = {id}) AND api_key = {apiKey}"""
      SQL(query).on('id -> id, 'apiKey -> apiKey).as(this.parser.*).headOption match {
        case Some(u) =>
          this.permission.hasObjectReadAccess(u, user)
          Some(u)
        case None => None
      }
    }
  }

  def retrieveByUsernameAndAPIKey(username:String, apiKey:String) : Option[User] = this.cacheManager.withOptionCaching { () =>
    this.db.withConnection { implicit c =>
      val query = s"""SELECT ${this.retrieveColumns} FROM users WHERE name = {name} AND api_key = {apiKey}"""
      SQL(query).on('name -> username, 'apiKey -> apiKey).as(this.parser.*).headOption
    }
  }

  /**
    * Helper function to allow users to be retrieve by just the OSM username, for security we only
    * allow super users access to this function
    *
    * @param username The username that is being searched for
    * @param user The user making the request
    * @return An optional user object, if none then not found
    */
  def retrieveByOSMUsername(username:String, user:User) : Option[User] = this.cacheManager.withOptionCaching { () =>
    // only only this kind of request if the user is a super user
    if (user.isSuperUser) {
      this.db.withConnection { implicit c =>
        val query = s"""SELECT ${this.retrieveColumns} FROM users WHERE name = {name}"""
        SQL(query).on('name -> username).as(this.parser.*).headOption
      }
    } else {
      throw new IllegalAccessException("Only Superuser allowed to look up users by just OSM username")
    }
  }

  /**
    * Match the user based on the token, secret and id for the user.
    *
    * @param id The id of the user
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestTokenAndId(requestToken: RequestToken, user:User)(implicit id:Long): Option[User] = {
    val requestedUser = this.cacheManager.withCaching { () =>
      this.db.withConnection { implicit c =>
        val query = s"""SELECT ${this.retrieveColumns} FROM users
                        WHERE id = {id} AND oauth_token = {token} AND oauth_secret = {secret}"""
        SQL(query).on('id -> id, 'token -> requestToken.token, 'secret -> requestToken.secret).as(this.parser.*).headOption
      }
    }
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
    * Match the user based on the token and secret for the user.
    *
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestToken(requestToken: RequestToken, user:User): Option[User] = {
    this.db.withConnection { implicit c =>
      val query = s"""SELECT ${this.retrieveColumns} FROM users
                      WHERE oauth_token = {token} AND oauth_secret = {secret}"""
      SQL(query).on('token -> requestToken.token, 'secret -> requestToken.secret).as(this.parser.*).headOption
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
  override def insert(item:User, user: User)(implicit c:Option[Connection]=None): User = this.cacheManager.withOptionCaching { () =>
    this.permission.hasObjectAdminAccess(item, user)
    this.withMRTransaction { implicit c =>
      val ewkt = new WKTWriter().write(
        new GeometryFactory().createPoint(
          new Coordinate(item.osmProfile.homeLocation.latitude, item.osmProfile.homeLocation.longitude)
        )
      )
      val newAPIKey = User.generateAPIKey(item.osmProfile.id)

      val query = s"""WITH upsert AS (UPDATE users SET osm_id = {osmID}, osm_created = {osmCreated},
                              name = {name}, description = {description}, avatar_url = {avatarURL},
                              oauth_token = {token}, oauth_secret = {secret},  home_location = ST_GeomFromEWKT({wkt}),
                              properties = {properties}
                            WHERE id = {id} OR osm_id = {osmID} RETURNING ${this.retrieveColumns})
            INSERT INTO users (api_key, osm_id, osm_created, name, description,
                               avatar_url, oauth_token, oauth_secret, home_location, properties)
            SELECT {apiKey}, {osmID}, {osmCreated}, {name}, {description}, {avatarURL}, {token}, {secret}, ST_GeomFromEWKT({wkt}), {properties}
            WHERE NOT EXISTS (SELECT * FROM upsert)"""
      SQL(query).on(
        'apiKey -> newAPIKey,
        'osmID -> item.osmProfile.id,
        'osmCreated -> item.osmProfile.created,
        'name -> item.osmProfile.displayName,
        'description -> item.osmProfile.description,
        'avatarURL -> item.osmProfile.avatarURL,
        'token -> item.osmProfile.requestToken.token,
        'secret -> item.osmProfile.requestToken.secret,
        'wkt -> s"SRID=4326;$ewkt",
        'id -> item.id,
        'properties -> item.properties
      ).executeUpdate()
    }
    // just in case expire the osm ID
    userGroupDAL.clearUserCache(item.osmProfile.id)

    // We do this separately from the transaction because if we don't the user_group mappings
    // wont be accessible just yet.
    val retUser = this.db.withConnection { implicit c =>
      val query = s"""SELECT ${this.retrieveColumns} FROM users WHERE osm_id = {id}"""
      SQL(query).on('id -> item.osmProfile.id).as(this.parser.*).head
    }

    // now update the groups by adding any new groups, from the supplied user
    val nuGroups = this.db.withTransaction { implicit c =>
      val newGroups = item.groups.filter(g => !retUser.groups.exists(_.id == g.id))
      newGroups.foreach(g => this.addUserToGroup(item.osmProfile.id, g, User.superUser))
      retUser.groups ++ newGroups
    }
    Some(retUser.copy(groups = nuGroups))
  }.get

  /**
    * This is a specialized update that is accessed via the API that allows users to update only the
    * fields that are available for update. This is so that there is no accidental update of OSM username
    * or anything retrieved from the OSM API.
    *
    * The function will simply recreate a JSON object with only the allowed fields. Any APIKey's must
    * be updated separately
    *
    * @param settings The user settings that have been pulled from the request object
    * @param properties Any extra properties that a client wishes to store alongside the user object
    * @param user The user making the update request
    * @param id The id of the user being updated
    * @param c an optional connection, if not provided a new connection from the pool will be retrieved
    * @return An optional user, if user with supplied ID not found, then will return empty optional
    */
  def managedUpdate(settings:UserSettings, properties:Option[JsValue], user:User)(implicit id:Long, c:Option[Connection]=None) : Option[User] = {
    implicit val settingsWrite = User.settingsWrites
    val updateBody = Utils.insertIntoJson(Json.parse("{}"), "settings", Json.toJson(settings))
    this.update(properties match {
      case Some(p) => Utils.insertIntoJson(updateBody, "properties", JsString(p.toString()))
      case None => updateBody
    }, user)
  }

  /**
    * Only certain values are allowed to be updated for the user. Namely apiKey, displayName,
    * description, avatarURL, token, secret and theme.
    *
    * @param value The json object containing the fields to update
    * @param id The id of the user to update
    * @return The user that was updated, None if no user was found with the id
    */
  override def update(value:JsValue, user:User)(implicit id:Long, c:Option[Connection]=None): Option[User] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasObjectAdminAccess(cachedItem, user)
      this.withMRTransaction { implicit c =>
        val apiKey = (value \ "apiKey").asOpt[String].getOrElse(cachedItem.apiKey.getOrElse("")) match {
          case "" => User.generateAPIKey(id)
          case v => v
        }
        val displayName = (value \ "osmProfile" \ "displayName").asOpt[String].getOrElse(cachedItem.osmProfile.displayName)
        val description = (value \ "osmProfile" \ "description").asOpt[String].getOrElse(cachedItem.osmProfile.description)
        val avatarURL = (value \ "osmProfile" \ "avatarURL").asOpt[String].getOrElse(cachedItem.osmProfile.avatarURL)
        val token = (value \ "osmProfile" \ "token").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.token)
        val secret = (value \ "osmProfile" \ "secret").asOpt[String].getOrElse(cachedItem.osmProfile.requestToken.secret)
        // todo: allow to insert in WKT, WKB or latitude/longitude
        val latitude = (value \ "osmProfile" \ "homeLocation" \ "latitude").asOpt[Double].getOrElse(cachedItem.osmProfile.homeLocation.latitude)
        val longitude = (value \ "osmProfile" \ "homeLocation" \ "longitude").asOpt[Double].getOrElse(cachedItem.osmProfile.homeLocation.longitude)
        val ewkt = new WKTWriter().write(new GeometryFactory().createPoint(new Coordinate(latitude, longitude)))
        val defaultEditor = (value \ "settings" \ "defaultEditor").asOpt[Int].getOrElse(cachedItem.settings.defaultEditor.getOrElse(-1))
        val defaultBasemap = (value \ "settings" \ "defaultBasemap").asOpt[Int].getOrElse(cachedItem.settings.defaultBasemap.getOrElse(-1))
        val customBasemap = (value \ "settings" \ "customBasemap").asOpt[String].getOrElse(cachedItem.settings.customBasemap.getOrElse(""))
        val locale = (value \ "settings" \ "locale").asOpt[String].getOrElse(cachedItem.settings.locale.getOrElse("en"))
        val emailOptIn = (value \ "settings" \ "emailOptIn").asOpt[Boolean].getOrElse(cachedItem.settings.emailOptIn.getOrElse(false))
        val theme = (value \ "settings" \ "theme").asOpt[Int].getOrElse(cachedItem.settings.theme.getOrElse(-1))
        val properties = (value \ "properties").asOpt[String].getOrElse(cachedItem.properties.getOrElse("{}"))

        this.updateGroups(value, user)
        this.userGroupDAL.clearUserCache(cachedItem.osmProfile.id)

        val query = s"""UPDATE users SET api_key = {apiKey}, name = {name}, description = {description},
                                          avatar_url = {avatarURL}, oauth_token = {token}, oauth_secret = {secret},
                                          home_location = ST_SetSRID(ST_GeomFromEWKT({wkt}),4326), default_editor = {defaultEditor},
                                          default_basemap = {defaultBasemap}, custom_basemap_url = {customBasemap},
                                          locale = {locale}, email_opt_in = {emailOptIn}, theme = {theme}, properties = {properties}
                        WHERE id = {id} RETURNING ${this.retrieveColumns}"""
        SQL(query).on(
          'apiKey -> apiKey,
          'name -> displayName,
          'description -> description,
          'avatarURL -> avatarURL,
          'token -> token,
          'secret -> secret,
          'wkt -> s"SRID=4326;$ewkt",
          'id -> id,
          'defaultEditor -> defaultEditor,
          'defaultBasemap -> defaultBasemap,
          'customBasemap -> customBasemap,
          'locale -> locale,
          'emailOptIn -> emailOptIn,
          'theme -> theme,
          'properties -> properties
        ).as(this.parser.*).headOption
      }
    }
  }

  def updateGroups(value:JsValue, user:User)(implicit id:Long, c:Option[Connection]=None): Unit = {
    this.permission.hasAdminAccess(UserType(), user)
    this.withMRTransaction { implicit c =>
      // list of groups to delete
      (value \ "groups" \ "delete").asOpt[List[Long]] match {
        case Some(values) =>
          values.foreach(this.userGroupDAL.clearCache(-1, _))
          SQL"""DELETE FROM user_groups WHERE group_id IN ($values)""".execute()
        case None => //ignore
      }
      (value \ "groups" \ "add").asOpt[List[Long]] match {
        case Some(values) =>
          values.foreach(this.userGroupDAL.clearCache(-1, _))
          val sqlQuery = s"""INSERT INTO user_groups (user_id, group_id) VALUES ($id, {groupId})"""
          val parameters = values.map(groupId => {
            Seq[NamedParameter]("groupId" -> groupId)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail:_*).execute()
        case None => //ignore
      }
    }
  }

  /**
    * Deletes a user from the database based on a specific user id
    *
    * @param id The user to delete
    * @return The rows that were deleted
    */
  override def delete(id: Long, user:User, immediate:Boolean=false)(implicit c:Option[Connection]=None) : User = {
    this.permission.hasSuperAccess(user)
    retrieveById(id) match {
      case Some(u) => userGroupDAL.clearUserCache(u.osmProfile.id)
      case None => //no user, so can just ignore
    }
    super.delete(id, user)
  }

  /**
    * Delete a user based on their OSM ID
    *
    * @param osmId The OSM ID for the user
    * @param user The user deleting the user
    * @return
    */
  def deleteByOsmID(osmId:Long, user:User)(implicit c:Option[Connection]=None) : Int = {
    this.permission.hasSuperAccess(user)
    implicit val ids = List(osmId)
    // expire the user group cache
    userGroupDAL.clearUserCache(osmId)
    this.cacheManager.withCacheIDDeletion { () =>
      this.withMRTransaction { implicit c =>
        SQL"""DELETE FROM users WHERE osm_id = $osmId""".executeUpdate()
      }
    }
  }

  /**
    * Adds a user to a project
    *
    * @param osmID The OSM ID of the user to add to the project
    * @param projectId The project that user is being added too
    * @param groupType The type of group to add 1 - Admin, 2 - Write, 3 - Read
    * @param user The user that is adding the user to the project
    */
  def addUserToProject(osmID:Long, projectId:Long, groupType:Int, user:User)(implicit c:Option[Connection]=None) : User = {
    this.permission.hasProjectAccess(this.projectDAL.retrieveById(projectId), user)
    implicit val osmKey = osmID
    implicit val superUser = user
    // expire the user group cache
    userGroupDAL.clearUserCache(osmID)
    this.cacheManager.withUpdatingCache(Long => retrieveByOSMID) { cachedUser =>
      this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO user_groups (osm_user_id, group_id)
            SELECT $osmID, id FROM groups
            WHERE group_type = $groupType AND project_id = $projectId
         """.executeUpdate()
      }
      Some(cachedUser.copy(groups = userGroupDAL.getUserGroups(osmID, superUser)))
    }.get
  }

  /**
    * Removes a user from a project
    *
    * @param osmID The OSM ID of the user
    * @param projectId The id of the project to remove the user from
    * @param groupType The type of group to add 1 - Admin, 2 - Write, 3 - Read
    * @param user The user making the request
    * @param c
    */
  def removeUserFromProject(osmID:Long, projectId:Long, groupType:Int, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasProjectAccess(this.projectDAL.retrieveById(projectId), user)
    implicit val osmKey = osmID
    implicit val superUser = user
    userGroupDAL.clearUserCache(osmID)
    this.cacheManager.withUpdatingCache(Long => retrieveByOSMID) { cachedUser =>
      this.withMRTransaction { implicit c =>
        SQL"""DELETE FROM user_groups
              WHERE group_id =
                (SELECT id FROM groups WHERE group_type = $groupType AND project_id = $projectId)
           """.executeUpdate()
      }
      Some(cachedUser.copy(groups = userGroupDAL.getUserGroups(osmID, superUser)))
    }.get
  }

  /**
    * Add a user to a group
    *
    * @param osmID The OSM ID of the user to add to the project
    * @param group The group that user is being added too
    * @param user The user that is adding the user to the project
    */
  def addUserToGroup(osmID:Long, group:Group, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasSuperAccess(user)
    userGroupDAL.clearUserCache(osmID)
    this.withMRTransaction { implicit c =>
      SQL"""INSERT INTO user_groups (osm_user_id, group_id) VALUES ($osmID, ${group.id})""".executeUpdate()
    }
  }

  /**
    * Removes a user from a group
    *
    * @param osmID The OSM ID of the user
    * @param group The group that you are removing from the user
    * @param user The user executing the request
    * @param c An implicit connection if applicable
    */
  def removeUserFromGroup(osmID:Long, group:Group, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasSuperAccess(user)
    userGroupDAL.clearUserCache(osmID)
    this.withMRTransaction { implicit c =>
      SQL"""DELETE FROM user_groups WHERE osm_user_id = $osmID AND group_id = ${group.id}""".executeUpdate()
    }
  }

  /**
    * Generates a new API key for the user
    *
    * @param apiKeyUser The user that is requesting that their key be updated.
    * @return An optional variable that will contain the updated user if successful
    */
  def generateAPIKey(apiKeyUser:User, user:User) : Option[User] = {
    this.permission.hasAdminAccess(UserType(), user)(apiKeyUser.id)
    this.update(Json.parse(s"""{"apiKey":"${User.generateAPIKey(apiKeyUser.osmProfile.id)}"}"""), User.superUser)(apiKeyUser.id)
  }

  /**
    * Initializes the home project for the user. If the project already exists, then we are
    * good.
    *
    * @param user The user to initialize the home project
    */
  def initializeHomeProject(user:User) : User = {
    val homeName = s"Home_${user.osmProfile.id}"
    val homeProjectId = this.projectDAL.retrieveByName(homeName) match {
      case Some(project) => project.id
      case None =>
        this.projectDAL.insert(Project(id = -1,
          owner = user.osmProfile.id,
          name = homeName,
          created = DateTime.now(),
          modified = DateTime.now(),
          description = Some(s"Home project for user ${user.name}"),
          enabled = false,
          displayName = Some(s"${user.osmProfile.displayName}'s Project")
        ), user).id
    }
    // make sure the user is an admin of this project
    if (!user.groups.exists(g => g.projectId == homeProjectId)) {
      this.addUserToProject(user.osmProfile.id, homeProjectId, Group.TYPE_ADMIN, User.superUser)
    } else {
      user
    }
  }

  /**
    * Gets the last X saved challenges for a user
    *
    * @param userId The id of the user you are requesting the saved challenges for
    * @param user The user making the request
    * @param limit limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param c The existing connection if any
    * @return a List of challenges
    */
  def getSavedChallenges(userId:Long, user:User, limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0)
                        (implicit c:Option[Connection]=None) : List[Challenge] = {
    this.permission.hasReadAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      val query = s"""
         |SELECT ${challengeDAL.retrieveColumns} FROM challenges
         |WHERE id IN (
         |  SELECT challenge_id FROM saved_challenges
         |  WHERE user_id = $userId
         |  ORDER BY created
         |  LIMIT ${sqlLimit(limit)} OFFSET $offset
         |)
       """.stripMargin
      SQL(query).as(challengeDAL.parser.*)
    }
  }

  /**
    * Saves the challenge for the user
    *
    * @param userId The id of the user
    * @param challengeId the id of the challenge
    * @param user the user executing the request
    * @param c The existing connection if any
    */
  def saveChallenge(userId:Long, challengeId:Long, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      SQL(
        s"""INSERT INTO saved_challenges (user_id, challenge_id)
           |VALUES ($userId, $challengeId) ON
           |CONFLICT(user_id, challenge_id) DO NOTHING""".stripMargin
      ).executeInsert()
    }
  }

  /**
    * Unsaves a challenge from the users profile
    *
    * @param userId The id of the user that has previously saved the challenge
    * @param challengeId The id of the challenge to remove from the user profile
    * @param user The user executing the unsave function
    * @param c The existing connection if any
    */
  def unsaveChallenge(userId:Long, challengeId:Long, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      SQL(s"""DELETE FROM saved_challenges WHERE user_id = $userId AND challenge_id = $challengeId""").execute()
    }
  }

  /**
    * Gets the last X saved tasks for a user
    *
    * @param userId The id of the user you are requesting the saved challenges for
    * @param user The user making the request
    * @param challengeIds A sequence of challengeId to limit the response to a specific set of challenges
    * @param limit limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param c The existing connection if any
    * @return a List of challenges
    */
  def getSavedTasks(userId:Long, user:User, challengeIds:Seq[Long] = Seq.empty, limit:Int=Config.DEFAULT_LIST_SIZE, offset:Int=0)
                   (implicit c:Option[Connection]=None) : List[Task] = {
    this.permission.hasReadAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      val query = s"""
                     |SELECT ${taskDAL.retrieveColumns} FROM tasks
                     |WHERE id IN (
                     |  SELECT task_id FROM saved_tasks
                     |  WHERE user_id = $userId
                     |  ${if (challengeIds.nonEmpty) {s"AND challenge_id IN (${challengeIds.mkString(",")})"} else {""} }
                     |  ORDER BY created
                     |  LIMIT ${sqlLimit(limit)} OFFSET $offset
                     |)
       """.stripMargin
      SQL(query).as(taskDAL.parser.*)
    }
  }

  /**
    * Saves the task for the user, will validate that the task actually exists first based on the
    * provided id
    *
    * @param userId The id of the user
    * @param taskId the id of the task
    * @param user the user executing the request
    * @param c The existing connection if any
    */
  def saveTask(userId:Long, taskId:Long, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    withMRTransaction { implicit c =>
      this.taskDAL.retrieveById(taskId) match {
        case Some(task) =>
          SQL(
            s"""INSERT INTO saved_tasks (user_id, task_id, challenge_id)
               |VALUES ($userId, ${task.id}, ${task.parent})
               |ON CONFLICT(user_id, task_id) DO NOTHING""".stripMargin
          ).executeInsert()
        case None => throw new NotFoundException(s"No task found with ID $taskId")
      }
    }
  }

  /**
    * Unsaves a task from the users profile
    *
    * @param userId The id of the user that has previously saved the challenge
    * @param task_id The id of the task to remove from the user profile
    * @param user The user executing the unsave function
    * @param c The existing connection if any
    */
  def unsaveTask(userId:Long, task_id:Long, user:User)(implicit c:Option[Connection]=None) : Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      SQL(s"""DELETE FROM saved_tasks WHERE user_id = $userId AND task_id = $task_id""").execute()
    }
  }

  /**
    * Retrieves the user's home project
    *
    * @param user The user to search for the home project
    * @return
    */
  def getHomeProject(user:User) : Project = {
    val homeName = s"Home_${user.osmProfile.id}"
    this.projectDAL.retrieveByName(homeName) match {
      case Some(project) => project
      case None => throw new NotFoundException("You should never get this exception, Home project should always exist for user.")
    }
  }
}
