// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.session.dal

import java.sql.Connection
import java.util.UUID

import anorm.SqlParser._
import anorm._
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Point}
import com.vividsolutions.jts.io.{WKTReader, WKTWriter}
import javax.inject.{Inject, Singleton}
import java.time.{LocalDate, Period}
import java.time.format.DateTimeFormatter
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.data.UserType
import org.maproulette.exception.NotFoundException
import org.maproulette.models.dal.{BaseDAL, ChallengeDAL, ProjectDAL, TaskDAL}
import org.maproulette.models.{Challenge, Project, Task}
import org.maproulette.permissions.Permission
import org.maproulette.session._
import org.maproulette.utils.{Crypto, Utils}
import play.api.db.Database
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
class UserDAL @Inject()(override val db: Database,
                        userGroupDAL: UserGroupDAL,
                        projectDAL: ProjectDAL,
                        challengeDAL: ChallengeDAL,
                        taskDAL: TaskDAL,
                        config: Config,
                        crypto: Crypto,
                        override val permission: Permission) extends BaseDAL[Long, User] {

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
      get[Option[String]]("users.default_basemap_id") ~
      get[Option[String]]("users.custom_basemap_url") ~
      get[Option[String]]("users.email") ~
      get[Option[Boolean]]("users.email_opt_in") ~
      get[Option[Boolean]]("users.leaderboard_opt_out") ~
      get[Option[Int]]("users.needs_review") ~
      get[Option[Boolean]]("users.is_reviewer") ~
      get[Option[String]]("users.locale") ~
      get[Option[Int]]("users.theme") ~
      get[Option[String]]("properties") ~
      get[Option[Int]]("score") map {
      case id ~ osmId ~ created ~ modified ~ osmCreated ~ displayName ~ description ~ avatarURL ~
        homeLocation ~ apiKey ~ oauthToken ~ oauthSecret ~ defaultEditor ~ defaultBasemap ~ defaultBasemapId ~
        customBasemap ~ email ~ emailOptIn ~ leaderboardOptOut ~ needsReview ~ isReviewer ~ locale ~ theme ~
        properties ~ score =>
        val locationWKT = homeLocation match {
          case Some(wkt) => new WKTReader().read(wkt).asInstanceOf[Point]
          case None => new GeometryFactory().createPoint(new Coordinate(0, 0))
        }

        val setNeedsReview = needsReview match {
          case Some(nr) => needsReview
          case None => Option(config.defaultNeedsReview)
        }

        new User(id, created, modified,
          OSMProfile(osmId, displayName, description.getOrElse(""), avatarURL.getOrElse(""),
            Location(locationWKT.getX, locationWKT.getY), osmCreated, RequestToken(oauthToken, oauthSecret)),
          userGroupDAL.getUserGroups(osmId, User.superUser
          ),
          apiKey, false,
          UserSettings(defaultEditor, defaultBasemap, defaultBasemapId, customBasemap, locale, email, emailOptIn, leaderboardOptOut, setNeedsReview, isReviewer, theme),
          properties,
          score
        )
    }
  }

  // The anorm row parser to convert user records to search results
  val searchResultParser: RowParser[UserSearchResult] = {
    get[Long]("users.osm_id") ~
      get[String]("users.name") ~
      get[Option[String]]("users.avatar_url") map {
      case osmId ~ displayName ~ avatarURL =>
        UserSearchResult(osmId, displayName, avatarURL.getOrElse(""))
    }
  }
  // The anorm row parser to convert user records to project manager
  val projectManagerParser: RowParser[ProjectManager] = {
    get[Long]("project_id") ~
      get[Long]("users.osm_id") ~
      get[String]("users.name") ~
      get[Option[String]]("users.avatar_url") ~
      get[List[Int]]("group_types") map {
      case projectId ~ osmId ~ displayName ~ avatarURL ~ groupTypes =>
        ProjectManager(projectId, osmId, displayName, avatarURL.getOrElse(""), groupTypes)
    }
  }

  /**
    * Find the user based on the user's osm ID. If found on cache, will return cached object
    * instead of hitting the database
    *
    * @param id   The user's osm ID
    * @param user The user making the request
    * @return The matched user, None if User not found
    */
  def retrieveByOSMID(implicit id: Long, user: User): Option[User] = this.cacheManager.withOptionCaching { () =>
    this.db.withConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns}, score FROM users
                      LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                      WHERE osm_id = {id}"""
      SQL(query).on('id -> id).as(this.parser.*).headOption match {
        case Some(u) =>
          this.permission.hasObjectReadAccess(u, user)
          Some(u)
        case None => None
      }
    }
  }

  /**
    * Find the User based on the id.
    *
    * @param id The id of the object to be retrieved
    * @return The object, None if not found
    */
  override def retrieveById(implicit id: Long, c: Option[Connection] = None): Option[User] = {
    this.cacheManager.withCaching { () =>
      this.withMRConnection { implicit c =>
        val query = s"""SELECT $retrieveColumns, score FROM users
                        LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                        WHERE id = {id}"""
        SQL(query).on('id -> id).as(this.parser.singleOpt)
      }
    }
  }

  /**
    * Find the User based on an API key, the API key is unique in the database.
    *
    * @param apiKey The APIKey to match against
    * @param id     The id of the user
    * @return The matched user, None if User not found
    */
  def retrieveByAPIKey(apiKey: String, user: User)(implicit id: Long): Option[User] = this.cacheManager.withOptionCaching { () =>
    this.db.withConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns}, score FROM users
                      LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                      WHERE (id = {id} OR osm_id = {id}) AND api_key = {apiKey}"""
      SQL(query).on('id -> id, 'apiKey -> apiKey).as(this.parser.*).headOption match {
        case Some(u) =>
          this.permission.hasObjectReadAccess(u, user)
          Some(u)
        case None => None
      }
    }
  }

  def retrieveByUsernameAndAPIKey(username: String, apiKey: String): Option[User] = this.cacheManager.withOptionCaching { () =>
    this.db.withConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns}, score FROM users
                      LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                      WHERE name = {name} AND api_key = {apiKey}"""
      SQL(query).on('name -> username, 'apiKey -> apiKey).as(this.parser.*).headOption
    }
  }

  /**
    * Helper function to allow users to be retrieve by just the OSM username, for security we only
    * allow super users access to this function
    *
    * @param username The username that is being searched for
    * @param user     The user making the request
    * @return An optional user object, if none then not found
    */
  def retrieveByOSMUsername(username: String, user: User): Option[User] = this.cacheManager.withOptionCaching { () =>
    // only only this kind of request if the user is a super user
    if (user.isSuperUser) {
      this.db.withConnection { implicit c =>
        val query =
          s"""SELECT ${this.retrieveColumns}, score FROM users
                        LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                        WHERE LOWER(name) = LOWER({name})"""
        SQL(query).on('name -> username).as(this.parser.*).headOption
      }
    } else {
      throw new IllegalAccessException("Only Superuser allowed to look up users by just OSM username")
    }
  }

  /**
    * Allow users to search for other users by OSM username.
    *
    * @param username The username or username fragment to search for.
    * @param limit    The maximum number of results to retrieve.
    * @return A (possibly empty) list of UserSearchResult objects.
    */
  def searchByOSMUsername(username: String, limit: Int = Config.DEFAULT_LIST_SIZE): List[UserSearchResult] = {
    this.db.withConnection { implicit c =>
      val query =
        s"""SELECT osm_id, name, avatar_url
            FROM users
            WHERE name ILIKE '${username}%'
            ORDER BY (users.name ILIKE '${username}') DESC, users.name
            LIMIT ${limit}"""
      SQL(query).on('name -> username).as(this.searchResultParser.*)
    }
  }

  /**
    * Match the user based on the token, secret and id for the user.
    *
    * @param id           The id of the user
    * @param requestToken The request token containing the access token and secret
    * @return The matched user, None if User not found
    */
  def matchByRequestTokenAndId(requestToken: RequestToken, user: User)(implicit id: Long): Option[User] = {
    val requestedUser = this.cacheManager.withCaching { () =>
      this.db.withConnection { implicit c =>
        val query =
          s"""SELECT ${this.retrieveColumns}, score FROM users
                        LEFT JOIN user_metrics ON users.id = user_metrics.user_id
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
  def matchByRequestToken(requestToken: RequestToken, user: User): Option[User] = {
    this.db.withConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns}, score FROM users
                      LEFT JOIN user_metrics ON users.id = user_metrics.user_id
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
  override def insert(item: User, user: User)(implicit c: Option[Connection] = None): User = this.cacheManager.withOptionCaching { () =>
    this.permission.hasObjectAdminAccess(item, user)
    this.withMRTransaction { implicit c =>
      val ewkt = new WKTWriter().write(
        new GeometryFactory().createPoint(
          new Coordinate(item.osmProfile.homeLocation.latitude, item.osmProfile.homeLocation.longitude)
        )
      )
      val newAPIKey = crypto.encrypt(this.generateAPIKey)

      val query =
        s"""WITH upsert AS (UPDATE users SET osm_id = {osmID}, osm_created = {osmCreated},
                              name = {name}, description = {description}, avatar_url = {avatarURL},
                              oauth_token = {token}, oauth_secret = {secret},  home_location = ST_GeomFromEWKT({wkt})
                            WHERE id = {id} OR osm_id = {osmID} RETURNING ${this.retrieveColumns})
            INSERT INTO users (api_key, osm_id, osm_created, name, description,
                               avatar_url, oauth_token, oauth_secret, home_location)
            SELECT {apiKey}, {osmID}, {osmCreated}, {name}, {description}, {avatarURL}, {token}, {secret}, ST_GeomFromEWKT({wkt})
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
        'id -> item.id
      ).executeUpdate()
    }
    // just in case expire the osm ID
    userGroupDAL.clearUserCache(item.osmProfile.id)

    // We do this separately from the transaction because if we don't the user_group mappings
    // wont be accessible just yet.
    val retUser = this.db.withConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns}, score FROM users
                      LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                      WHERE osm_id = {id}"""
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
    * Add a user to a group
    *
    * @param osmID The OSM ID of the user to add to the project
    * @param group The group that user is being added too
    * @param user  The user that is adding the user to the project
    */
  def addUserToGroup(osmID: Long, group: Group, user: User)(implicit c: Option[Connection] = None): Unit = {
    this.permission.hasSuperAccess(user)
    userGroupDAL.clearUserCache(osmID)
    this.withMRTransaction { implicit c =>
      SQL"""INSERT INTO user_groups (osm_user_id, group_id) VALUES ($osmID, ${group.id})""".executeUpdate()
    }
  }

  /**
    * Retrieves a list of objects from the supplied list of ids. Will check for any objects currently
    * in the cache and those that aren't will be retrieved from the database
    *
    * @param limit The limit on the number of objects returned. This is not entirely useful as a limit
    *              could be set simply by how many ids you supplied in the list, but possibly useful
    *              for paging
    * @param offset For paging, ie. the page number starting at 0
    * @param ids The list of ids to be retrieved
    * @return A list of objects, empty list if none found
    */
  override def retrieveListById(limit: Int = -1, offset: Int = 0)(implicit ids:List[Long], c: Option[Connection] = None): List[User] = {
    if (ids.isEmpty) {
      List.empty
    } else {
      this.cacheManager.withIDListCaching { implicit uncachedIDs =>
        this.withMRConnection { implicit c =>
          val query =
            s"""SELECT ${this.retrieveColumns}, score FROM ${this.tableName}
                          LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                          WHERE id IN ({inString})
                          LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
          SQL(query).on('inString -> ToParameterValue.apply[List[Long]](s = keyToSQL, p = keyToStatement).apply(uncachedIDs),
            'offset -> offset).as(this.parser.*)
        }
      }
    }
  }

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
    * @param c          an optional connection, if not provided a new connection from the pool will be retrieved
    * @return An optional user, if user with supplied ID not found, then will return empty optional
    */
  def managedUpdate(settings: UserSettings, properties: Option[JsValue], user: User)(implicit id: Long, c: Option[Connection] = None): Option[User] = {
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
    * @param id    The id of the user to update
    * @return The user that was updated, None if no user was found with the id
    */
  override def update(value: JsValue, user: User)(implicit id: Long, c: Option[Connection] = None): Option[User] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasObjectAdminAccess(cachedItem, user)
      this.withMRTransaction { implicit c =>
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
        val defaultBasemapId = (value \ "settings" \ "defaultBasemapId").asOpt[String].getOrElse(cachedItem.settings.defaultBasemapId.getOrElse(""))
        val customBasemap = (value \ "settings" \ "customBasemap").asOpt[String].getOrElse(cachedItem.settings.customBasemap.getOrElse(""))
        val locale = (value \ "settings" \ "locale").asOpt[String].getOrElse(cachedItem.settings.locale.getOrElse("en"))
        val email = (value \ "settings" \ "email").asOpt[String].getOrElse(cachedItem.settings.email.getOrElse(""))
        val emailOptIn = (value \ "settings" \ "emailOptIn").asOpt[Boolean].getOrElse(cachedItem.settings.emailOptIn.getOrElse(false))
        val leaderboardOptOut = (value \ "settings" \ "leaderboardOptOut").asOpt[Boolean].getOrElse(cachedItem.settings.leaderboardOptOut.getOrElse(false))
        var needsReview = (value \ "settings" \ "needsReview").asOpt[Int].getOrElse(cachedItem.settings.needsReview.getOrElse(config.defaultNeedsReview))
        val isReviewer = (value \ "settings" \ "isReviewer").asOpt[Boolean].getOrElse(cachedItem.settings.isReviewer.getOrElse(false))
        val theme = (value \ "settings" \ "theme").asOpt[Int].getOrElse(cachedItem.settings.theme.getOrElse(-1))
        val properties = (value \ "properties").asOpt[String].getOrElse(cachedItem.properties.getOrElse("{}"))

        // If this user always requires a review, then they are not allowed to change it (except super users)
        if (user.settings.needsReview.getOrElse(0) == User.REVIEW_MANDATORY) {
          if (!user.isSuperUser) {
            needsReview = User.REVIEW_MANDATORY
          }
        }

        this.updateGroups(value, user)
        this.userGroupDAL.clearUserCache(cachedItem.osmProfile.id)

        val query =
          s"""UPDATE users SET name = {name}, description = {description},
                                          avatar_url = {avatarURL}, oauth_token = {token}, oauth_secret = {secret},
                                          home_location = ST_SetSRID(ST_GeomFromEWKT({wkt}),4326), default_editor = {defaultEditor},
                                          default_basemap = {defaultBasemap}, default_basemap_id = {defaultBasemapId}, custom_basemap_url = {customBasemap},
                                          locale = {locale}, email = {email}, email_opt_in = {emailOptIn}, leaderboard_opt_out = {leaderboardOptOut},
                                          needs_review = {needsReview}, is_reviewer = {isReviewer}, theme = {theme}, properties = {properties}
                        WHERE id = {id} RETURNING ${this.retrieveColumns},
                        (SELECT score FROM user_metrics um WHERE um.user_id = ${user.id}) as score"""
        SQL(query).on(
          'name -> displayName,
          'description -> description,
          'avatarURL -> avatarURL,
          'token -> token,
          'secret -> secret,
          'wkt -> s"SRID=4326;$ewkt",
          'id -> id,
          'defaultEditor -> defaultEditor,
          'defaultBasemap -> defaultBasemap,
          'defaultBasemapId -> defaultBasemapId,
          'customBasemap -> customBasemap,
          'locale -> locale,
          'email -> email,
          'emailOptIn -> emailOptIn,
          'leaderboardOptOut -> leaderboardOptOut,
          'needsReview -> needsReview,
          'isReviewer -> isReviewer,
          'theme -> theme,
          'properties -> properties
        ).as(this.parser.*).headOption
      }
    }
  }

  def updateGroups(value: JsValue, user: User)(implicit id: Long, c: Option[Connection] = None): Unit = {
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
          BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
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
  override def delete(id: Long, user: User, immediate: Boolean = false)(implicit c: Option[Connection] = None): User = {
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
    * @param user  The user deleting the user
    * @return
    */
  def deleteByOsmID(osmId: Long, user: User)(implicit c: Option[Connection] = None): Int = {
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
    * Anonymizes all user data in the database
    *
    * @param osmId The OSM id of the user you wish to anonymize
    * @param user  The user requesting action, can only be a super user
    * @param c     An implicit connection
    */
  def anonymizeUser(osmId: Long, user: User)(implicit c: Option[Connection] = None): Unit = {
    this.permission.hasSuperAccess(user)
    this.withMRTransaction { implicit c =>
      // anonymize all status actions set
      SQL"""UPDATE status_actions SET osm_user_id = -1 WHERE osm_user_id = $osmId""".executeUpdate()
      // set all comments made to "COMMENT_DELETED"
      SQL"""UPDATE task_comments SET comment = '*COMMENT DELETED*', osm_id = -1 WHERE osm_id = $osmId""".executeUpdate()
      // anonymize all survey_answers answered
      SQL"""UPDATE survey_answers SET osm_user_id = -1 WHERE osm_user_id = $osmId""".executeUpdate()
    }
  }

  /**
    * Removes a user from a project
    *
    * @param osmID     The OSM ID of the user
    * @param projectId The id of the project to remove the user from
    * @param groupType The type of group to remove -1 - all, 1 - Admin, 2 - Write, 3 - Read
    * @param user      The user making the request
    * @param c
    */
  def removeUserFromProject(osmID: Long, projectId: Long, groupType: Int, user: User)(implicit c: Option[Connection] = None): Unit = {
    this.permission.hasProjectAccess(this.projectDAL.retrieveById(projectId), user)
    implicit val osmKey = osmID
    implicit val requestingUser = User.superUser

    userGroupDAL.clearUserCache(osmID)
    this.cacheManager.withUpdatingCache(Long => retrieveByOSMID) { cachedUser =>
      this.withMRTransaction { implicit c =>
        if (groupType == -1) {
          SQL"""DELETE FROM user_groups
                WHERE osm_user_id = $osmKey AND group_id IN
                  (SELECT id FROM groups WHERE project_id = $projectId)
            """.executeUpdate()
        } else {
          SQL"""DELETE FROM user_groups
                WHERE osm_user_id = $osmKey AND group_id =
                  (SELECT id FROM groups WHERE group_type = $groupType AND project_id = $projectId)
            """.executeUpdate()
        }
      }
      Some(cachedUser.copy(groups = userGroupDAL.getUserGroups(osmID, User.superUser)))
    }.get
  }

  /**
    * Sets the group type of a user in a project, first deleting any prior group types,
    * in a single transaction.
    *
    * @param osmID     The OSM ID of the user
    * @param projectId The id of the project to remove the user from
    * @param groupType The type of group to set 1 - Admin, 2 - Write, 3 - Read
    * @param user      The user making the request
    * @param c
    */
  def setUserProjectGroup(osmID: Long, projectId: Long, groupType: Int, user: User)(implicit c: Option[Connection] = None): Unit = {
    this.permission.hasProjectAccess(this.projectDAL.retrieveById(projectId), user)
    implicit val osmKey = osmID
    implicit val requestingUser = User.superUser
    userGroupDAL.clearUserCache(osmID)
    this.verifyProjectGroups(projectId)
    this.cacheManager.withUpdatingCache(Long => retrieveByOSMID) { cachedUser =>
      this.withMRTransaction { implicit c =>
        // Remove all groups types for project from user and then add desired group type
        SQL"""DELETE FROM user_groups
              WHERE osm_user_id = $osmKey AND group_id IN (SELECT id FROM groups WHERE project_id = $projectId)
           """.executeUpdate()
        SQL"""INSERT INTO user_groups (osm_user_id, group_id)
              SELECT $osmID, id FROM groups
              WHERE group_type = $groupType AND project_id = $projectId
           """.executeUpdate()
      }
      Some(cachedUser.copy(groups = userGroupDAL.getUserGroups(osmID, User.superUser)))
    }.get
  }

  /**
    * This function will quickly verify that the project groups have been created correctly and if not,
    * then it will create them
    *
    * @param projectId The id of the project you are checking
    */
  private def verifyProjectGroups(projectId: Long): Unit = {
    this.projectDAL.clearCache(projectId)

    this.projectDAL.retrieveById(projectId) match {
      case Some(p) =>
        val groups = p.groups
        // must contain at least 1 admin group, 1 write group and 1 read group
        if (groups.count(_.groupType == Group.TYPE_ADMIN) < 1) {
          userGroupDAL.createGroup(projectId, Group.TYPE_ADMIN, User.superUser)
        }
        if (groups.count(_.groupType == Group.TYPE_WRITE_ACCESS) < 1) {
          userGroupDAL.createGroup(projectId, Group.TYPE_WRITE_ACCESS, User.superUser)
        }
        if (groups.count(_.groupType == Group.TYPE_READ_ONLY) < 1) {
          userGroupDAL.createGroup(projectId, Group.TYPE_READ_ONLY, User.superUser)
        }
      case None => throw new NotFoundException(s"No project found with id $projectId")
    }
  }

  /**
    * Retrieve list of all users possessing a group type for the project.
    *
    * @param projectId   The project
    * @param osmIdFilter : A filter for manager OSM ids
    * @param user        The user making the request
    * @return A list of ProjectManager objects.
    */
  def getUsersManagingProject(projectId: Long, osmIdFilter: Option[List[Long]] = None, user: User): List[ProjectManager] = {
    this.permission.hasProjectAccess(this.projectDAL.retrieveById(projectId), user, Group.TYPE_READ_ONLY)

    this.db.withConnection { implicit c =>
      SQL"""SELECT ${projectId} AS project_id, u.*, array_agg(g.group_type) AS group_types
            FROM users u, groups g, user_groups ug
            WHERE ug.group_id = g.id AND ug.osm_user_id = u.osm_id AND g.project_id = ${projectId}
            #${getLongListFilter(osmIdFilter, "u.osm_id")}
            GROUP BY u.id
      """.as(this.projectManagerParser.*)
    }
  }

  /**
    * Removes a user from a group
    *
    * @param osmID The OSM ID of the user
    * @param group The group that you are removing from the user
    * @param user  The user executing the request
    * @param c     An implicit connection if applicable
    */
  def removeUserFromGroup(osmID: Long, group: Group, user: User)(implicit c: Option[Connection] = None): Unit = {
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
  def generateAPIKey(apiKeyUser: User, user: User)(implicit c: Option[Connection] = None): Option[User] = {
    this.permission.hasAdminAccess(UserType(), user)(apiKeyUser.id)

    implicit val id = apiKeyUser.id
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.withMRTransaction { implicit c =>
        val newAPIKey = crypto.encrypt(this.generateAPIKey)
        val query =
          s"""UPDATE users SET api_key = {apiKey} WHERE id = {id}
                        RETURNING ${this.retrieveColumns},
                        (SELECT score FROM user_metrics um WHERE um.user_id = {id}) as score"""
        SQL(query).on('apiKey -> newAPIKey, 'id -> apiKeyUser.id).as(this.parser.*).headOption
      }
    }
  }

  private def generateAPIKey: String = UUID.randomUUID().toString

  /**
    * Initializes the home project for the user. If the project already exists, then we are
    * good.
    *
    * @param user The user to initialize the home project
    */
  def initializeHomeProject(user: User): User = {
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
    * Adds a user to a project
    *
    * @param osmID     The OSM ID of the user to add to the project
    * @param projectId The project that user is being added too
    * @param groupType The type of group to add 1 - Admin, 2 - Write, 3 - Read
    * @param user      The user that is adding the user to the project
    */
  def addUserToProject(osmID: Long, projectId: Long, groupType: Int, user: User)(implicit c: Option[Connection] = None): User = {
    this.permission.hasProjectAccess(this.projectDAL.retrieveById(projectId), user)
    implicit val osmKey = osmID
    implicit val requestingUser = user
    // expire the user group cache
    userGroupDAL.clearUserCache(osmID)
    this.verifyProjectGroups(projectId)
    this.cacheManager.withUpdatingCache(Long => retrieveByOSMID) { cachedUser =>
      this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO user_groups (osm_user_id, group_id)
            SELECT $osmID, id FROM groups
            WHERE group_type = $groupType AND project_id = $projectId
         """.executeUpdate()
      }
      Some(cachedUser.copy(groups = userGroupDAL.getUserGroups(osmID, User.superUser)))
    }.get
  }

  /**
    * Gets the last X saved challenges for a user
    *
    * @param userId The id of the user you are requesting the saved challenges for
    * @param user   The user making the request
    * @param limit  limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param c      The existing connection if any
    * @return a List of challenges
    */
  def getSavedChallenges(userId: Long, user: User, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0)
                        (implicit c: Option[Connection] = None): List[Challenge] = {
    this.permission.hasReadAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      val query =
        s"""
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
    * @param userId      The id of the user
    * @param challengeId the id of the challenge
    * @param user        the user executing the request
    * @param c           The existing connection if any
    */
  def saveChallenge(userId: Long, challengeId: Long, user: User)(implicit c: Option[Connection] = None): Unit = {
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
    * @param userId      The id of the user that has previously saved the challenge
    * @param challengeId The id of the challenge to remove from the user profile
    * @param user        The user executing the unsave function
    * @param c           The existing connection if any
    */
  def unsaveChallenge(userId: Long, challengeId: Long, user: User)(implicit c: Option[Connection] = None): Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      SQL(s"""DELETE FROM saved_challenges WHERE user_id = $userId AND challenge_id = $challengeId""").execute()
    }
  }

  /**
    * Gets the last X saved tasks for a user
    *
    * @param userId       The id of the user you are requesting the saved challenges for
    * @param user         The user making the request
    * @param challengeIds A sequence of challengeId to limit the response to a specific set of challenges
    * @param limit        limits the number of children to be returned
    * @param offset       For paging, ie. the page number starting at 0
    * @param c            The existing connection if any
    * @return a List of challenges
    */
  def getSavedTasks(userId: Long, user: User, challengeIds: Seq[Long] = Seq.empty, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0)
                   (implicit c: Option[Connection] = None): List[Task] = {
    this.permission.hasReadAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      val query =
        s"""
           |SELECT ${taskDAL.retrieveColumnsWithReview} FROM tasks
           |LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
           |WHERE tasks.id IN (
           |  SELECT task_id FROM saved_tasks
           |  WHERE user_id = $userId
           |  ${
          if (challengeIds.nonEmpty) {
            s"AND challenge_id IN (${challengeIds.mkString(",")})"
          } else {
            ""
          }
        }
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
    * @param user   the user executing the request
    * @param c      The existing connection if any
    */
  def saveTask(userId: Long, taskId: Long, user: User)(implicit c: Option[Connection] = None): Unit = {
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
    * @param userId  The id of the user that has previously saved the challenge
    * @param task_id The id of the task to remove from the user profile
    * @param user    The user executing the unsave function
    * @param c       The existing connection if any
    */
  def unsaveTask(userId: Long, task_id: Long, user: User)(implicit c: Option[Connection] = None): Unit = {
    this.permission.hasWriteAccess(UserType(), user)(userId)
    withMRConnection { implicit c =>
      SQL(s"""DELETE FROM saved_tasks WHERE user_id = $userId AND task_id = $task_id""").execute()
    }
  }

  /**
    * Updates the user's score in the user_metrics table.
    *
    * @param taskStatus The new status of the task to credit the user for.
    * @param taskReviewStatus The review status of the task to credit the user for.
    * @param isReviewRevision Whether this is the first review or is occurring after
    *                         a revision (due to a rejected status)
    * @param asReviewer Whether the user is the reviewer (true) or the mapper (false)
    * @param user       The user who should get the credit
    */
  def updateUserScore(taskStatus: Option[Int], taskReviewStatus: Option[Int], isReviewRevision: Boolean = false,
                      asReviewer: Boolean = false, userId: Long)(implicit c: Connection = null) = {
    // We need to invalidate the user in the cache.
    implicit val id = userId
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.withMRTransaction { implicit c =>
        var statusBump = ""

        val pointsToAward = taskStatus match {
          case Some(Task.STATUS_FIXED) => {
            statusBump = ", total_fixed=(total_fixed + 1)"
            config.taskScoreFixed
          }
          case Some(Task.STATUS_FALSE_POSITIVE) => {
            statusBump = ", total_false_positive=(total_false_positive + 1)"
            config.taskScoreFalsePositive
          }
          case Some(Task.STATUS_ALREADY_FIXED) => {
            statusBump = ", total_already_fixed=(total_already_fixed + 1)"
            config.taskScoreAlreadyFixed
          }
          case Some(Task.STATUS_TOO_HARD) => {
            statusBump = ", total_too_hard=(total_too_hard + 1)"
            config.taskScoreTooHard
          }
          case Some(Task.STATUS_SKIPPED) => {
            statusBump = ", total_skipped=(total_skipped + 1)"
            config.taskScoreSkipped
          }
          case None => 0
          case default => 0
        }

        val scoreBump = "score=(score + " + pointsToAward + ")"

        taskReviewStatus match {
          case Some(Task.REVIEW_STATUS_REJECTED) => {
            statusBump = ", total_rejected=(total_rejected + 1)"
            if (!isReviewRevision) {
              statusBump += ", initial_rejected=(initial_rejected + 1)"
            }
          }
          case Some(Task.REVIEW_STATUS_APPROVED) => {
            statusBump = ", total_approved=(total_approved + 1)"
            if (!isReviewRevision) {
              statusBump += ", initial_approved=(initial_approved + 1)"
            }
          }
          case Some(Task.REVIEW_STATUS_ASSISTED) => {
            statusBump = ", total_assisted=(total_assisted + 1)"
            if (!isReviewRevision) {
              statusBump += ", initial_assisted=(initial_assisted + 1)"
            }
          }
          case Some(Task.REVIEW_STATUS_DISPUTED) => {
            if (asReviewer) {
              statusBump = ", total_disputed_as_reviewer=(total_disputed_as_reviewer + 1)"
            }
            else {
              statusBump = ", total_disputed_as_mapper=(total_disputed_as_mapper + 1)"

              // Let's rollback mapper's rejected score
              statusBump += ", total_rejected=(total_rejected - 1)"
            }
          }
          case default => None
        }

        // We need to make sure the user is in the database first.
        SQL(s"""INSERT INTO user_metrics (user_id, score, total_fixed, total_false_positive,
                total_already_fixed, total_too_hard, total_skipped)
                VALUES (${userId}, 0, 0, 0, 0, 0, 0)
                ON CONFLICT (user_id) DO NOTHING""").executeUpdate()

        val updateScoreQuery =
          s"""UPDATE user_metrics SET ${scoreBump} ${statusBump} WHERE user_id = ${userId} """
        SQL(updateScoreQuery).executeUpdate()

        val query =
          s"""SELECT ${this.retrieveColumns}, score FROM users
                        LEFT JOIN user_metrics ON users.id = user_metrics.user_id
                        WHERE id = ${userId}"""
        SQL(query).as(this.parser.*).headOption
      }
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
    this.projectDAL.retrieveByName(homeName) match {
      case Some(project) => project
      case None => throw new NotFoundException("You should never get this exception, Home project should always exist for user.")
    }
  }

  /**
    * Gets metrics for a user
    *
    * @param userId       The id of the user you are requesting the saved challenges for
    * @param user         The user making the request
    * @param taskMonthDuration
    * @param reviewMonthDuration
    * @param c            The existing connection if any
    */
  def getMetricsForUser(userId: Long, user: User, taskMonthDuration: Int, reviewMonthDuration: Int)(
    implicit c: Option[Connection] = None): Map[String,Map[String,Int]] = {

    val targetUser = retrieveById(userId)
    targetUser match {
      case Some(u) =>
        if (u.settings.leaderboardOptOut.getOrElse(false) && !user.isSuperUser && userId != user.id) {
          throw new IllegalAccessException(s"User metrics are not public for this user.")
        }
      case _ =>
        throw new NotFoundException(s"Could not find user with id: $userId")
    }

    this.withMRConnection { implicit c =>
      // Fetch task metrics
      val timeClause = taskMonthDuration match {
          case -1 => "1=1"
          case default =>
            val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val startMonth = LocalDate.now.minus(Period.ofMonths(taskMonthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            s"""sa1.created::DATE BETWEEN '$startMonth' AND '$today'"""
        }

      val taskCountsParser: RowParser[Map[String,Int]] = {
          get[Int]("total") ~
          get[Int]("total_fixed") ~
          get[Int]("total_false_positive") ~
          get[Int]("total_already_fixed") ~
          get[Int]("total_too_hard") ~
          get[Int]("total_skipped") map {
          case total ~ fixed ~ falsePositive ~ alreadyFixed ~ tooHard ~ skipped => {
            Map("total" -> total, "fixed" -> fixed, "falsePositive" -> falsePositive,
                "alreadyFixed" -> alreadyFixed, "tooHard" -> tooHard, "skipped" -> skipped)
          }
        }
      }

      val taskCountsQuery =
       s"""SELECT COUNT(tasks.id) AS total,
             COALESCE(SUM(CASE WHEN sa1.status = ${Task.STATUS_FIXED} then 1 else 0 end), 0) total_fixed,
             COALESCE(SUM(CASE WHEN sa1.status = ${Task.STATUS_FALSE_POSITIVE} then 1 else 0 end), 0) total_false_positive,
             COALESCE(SUM(CASE WHEN sa1.status = ${Task.STATUS_ALREADY_FIXED} then 1 else 0 end), 0) total_already_fixed,
             COALESCE(SUM(CASE WHEN sa1.status = ${Task.STATUS_TOO_HARD} then 1 else 0 end), 0) total_too_hard,
             COALESCE(SUM(CASE WHEN sa1.status = ${Task.STATUS_SKIPPED} then 1 else 0 end), 0) total_skipped
           FROM tasks
           INNER JOIN status_actions sa1 ON sa1.task_id = tasks.id AND sa1.status = tasks.status
           INNER JOIN users ON users.osm_id = sa1.osm_user_id AND users.id=${userId}
           WHERE $timeClause;"""

       val taskCounts = SQL(taskCountsQuery).as(taskCountsParser.single)

       // Now fetch Review Metrics
       val reviewTimeClause = reviewMonthDuration match {
           case -1 => "1=1"
           case default =>
             val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
             val startMonth = LocalDate.now.minus(Period.ofMonths(reviewMonthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
             s"""reviewed_at::DATE BETWEEN '$startMonth' AND '$today'"""
         }

       val reviewCountsParser: RowParser[Map[String,Int]] = {
           get[Int]("total") ~
           get[Int]("approvedCount") ~
           get[Int]("rejectedCount") ~
           get[Int]("assistedCount") ~
           get[Int]("disputedCount") ~
           get[Int]("requestedCount") map {
           case total ~ approvedCount ~ rejectedCount ~ assistedCount ~ disputedCount ~ requestedCount => {
             Map("total" -> total, "approved" -> approvedCount, "rejected" -> rejectedCount,
                 "assisted" -> assistedCount, "disputed" -> disputedCount, "requested" -> requestedCount)
           }
         }
       }

       val reviewCountsQuery =
         s"""
            |SELECT count(*) as total,
            |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_APPROVED} then 1 else 0 end), 0) approvedCount,
            |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_REJECTED} then 1 else 0 end), 0) rejectedCount,
            |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_ASSISTED} then 1 else 0 end), 0) assistedCount,
            |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_DISPUTED} then 1 else 0 end), 0) disputedCount,
            |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_REQUESTED} then 1 else 0 end), 0) requestedCount
            |FROM task_review
            |WHERE task_review.review_requested_by = $userId AND ${reviewTimeClause}
        """.stripMargin

        val reviewCounts = SQL(reviewCountsQuery).as(reviewCountsParser.single)

       Map("tasks" -> taskCounts, "reviewTasks" -> reviewCounts)
    }
  }
}
