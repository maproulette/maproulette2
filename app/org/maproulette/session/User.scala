// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.session

import java.util.Locale

import javax.crypto.{BadPaddingException, IllegalBlockSizeException}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.maproulette.Config
import org.maproulette.data.{ItemType, UserType}
import org.maproulette.models.BaseObject
import org.maproulette.utils.{Crypto, Utils}
import org.slf4j.LoggerFactory
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json._
import play.api.libs.oauth.RequestToken
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

import scala.xml.{Elem, XML}

/**
  * Classes for the User object and the OSM Profile
  *
  * @author cuthbertm
  */
/**
  * Basic Location case class defining longitude and latitude
  *
  * @param longitude longitude for location
  * @param latitude  latitude for location
  */
case class Location(latitude: Double, longitude: Double, name: Option[String] = None)

/**
  * Information specific to the OSM profile of the user. All users in the system are based on
  * OSM users.
  *
  * @param id           The osm id
  * @param displayName  The display name for the osm user
  * @param description  The description of the OSM user as per their OSM profile
  * @param avatarURL    The avatar URL to enabling displaying of their avatar
  * @param homeLocation Their home location
  * @param created      When their OSM account was created
  * @param requestToken The key and secret (access token) used for authorization
  */
case class OSMProfile(
    id: Long,
    displayName: String,
    description: String,
    avatarURL: String,
    homeLocation: Location,
    created: DateTime,
    requestToken: RequestToken
)

/**
  * A user search result containing a few public fields from user's OSM Profile.
  *
  * @param osmId       The osm id
  * @param displayName The display name for the osm user
  * @param avatarURL   The avatar URL to enabling displaying of their avatar
  */
case class UserSearchResult(osmId: Long, displayName: String, avatarURL: String)

/**
  * Information specific to a user managing a project. Includes the project id,
  * a few basic fields about the user, and their group types for the project.
  *
  * @param projectId   The project id
  * @param userId      The user's MapRoulette id
  * @param osmId       The user's osm id
  * @param displayName The display name for the osm user
  * @param avatarURL   The avatar URL to enabling displaying of their avatar
  * @param groupTypes  List of the user's group types for the project
  */
case class ProjectManager(
    projectId: Long,
    userId: Long,
    osmId: Long,
    displayName: String,
    avatarURL: String,
    groupTypes: List[Int] = List()
)

/**
  * Settings that are not defined by the OSM user profile, but specific to MapRoulette
  *
  * @param defaultEditor     The default editor that the user wants to use
  * @param defaultBasemap    The default basemap that the user wants to see, will be overridden by default basemap for the challenge if set
  * @param defaultBasemapId  The string id of the default basemap that the user wants to see
  * @param customBasemap     It default basemap is custom, then this is the url to the tile server
  * @param locale            The locale for the user, if not set will default to en
  * @param email             The user's email address
  * @param emailOptIn        If the user has opted in to receive emails
  * @param leaderboardOptOut If the user has opted out of the public leaderboard
  * @param needsReview       If the user's work should be reviewed
  * @param isReviewer        If this user can review others work
  * @param theme             The theme to display in MapRoulette. Optionally - 0=skin-black, 1=skin-black-light,
  *                          2=skin-blue, 3=skin-blue-light, 4=skin-green, 5=skin-green-light,
  *                          6=skin-purple, 7=skin-purple-light, 8=skin-red, 9=skin-red-light, 10=skin-yellow, 11=skin-yellow-light
  */
case class UserSettings(
    defaultEditor: Option[Int] = None,
    defaultBasemap: Option[Int] = None,
    defaultBasemapId: Option[String] = None,
    customBasemap: Option[String] = None,
    locale: Option[String] = None,
    email: Option[String] = None,
    emailOptIn: Option[Boolean] = None,
    leaderboardOptOut: Option[Boolean] = None,
    needsReview: Option[Int] = None,
    isReviewer: Option[Boolean] = None,
    theme: Option[Int] = None
) {
  def getTheme: String = theme match {
    case Some(t) =>
      t match {
        case User.THEME_BLACK        => "skin-black"
        case User.THEME_BLACK_LIGHT  => "skin-black-light"
        case User.THEME_BLUE         => "skin-blue"
        case User.THEME_BLUE_LIGHT   => "skin-blue-light"
        case User.THEME_GREEN        => "skin-green"
        case User.THEME_GREEN_LIGHT  => "skin-green-light"
        case User.THEME_PURPLE       => "skin-purple"
        case User.THEME_PURPLE_LIGHT => "skin-purple-light"
        case User.THEME_RED          => "skin-red"
        case User.THEME_RED_LIGHT    => "skin-red-light"
        case User.THEME_YELLOW       => "skin-yellow"
        case User.THEME_YELLOW_LIGHT => "skin-yellow-light"
        case _                       => "skin-blue"
      }
    case None => "skin-blue"
  }
}

/**
  * Information specific to the MapRoulette user.
  *
  * @param id         The id defined by the database
  * @param created    When their account was created
  * @param modified   When their account was last updated. If last updated was longer then a day,
  *                   will automatically update their OSM information
  * @param osmProfile The osm profile information
  * @param apiKey     The current api key to validate requests
  * @param guest      Whether this is a guest account or not.
  */
case class User(
    override val id: Long,
    override val created: DateTime,
    override val modified: DateTime,
    osmProfile: OSMProfile,
    groups: List[Group] = List(),
    apiKey: Option[String] = None,
    guest: Boolean = false,
    settings: UserSettings = UserSettings(),
    properties: Option[String] = None,
    score: Option[Int] = None
) extends BaseObject[Long] {
  // for users the display name is always retrieved from OSM
  override def name: String = osmProfile.displayName

  override val itemType: ItemType = UserType()

  def homeLocation: String = osmProfile.homeLocation.name match {
    case Some(name) => name
    case None       => "Unknown"
  }

  def formattedOSMCreatedDate: String =
    DateTimeFormat.forPattern("MMMM. yyyy").print(osmProfile.created)

  def formattedMPCreatedDate: String = DateTimeFormat.forPattern("MMMM. yyyy").print(created)

  /**
    * Checks to see if this user is part of the special super user group
    *
    * @return true if user is a super user
    */
  def isSuperUser: Boolean = groups.exists(_.groupType == Group.TYPE_SUPER_USER)

  def isAdmin: Boolean = groups.exists(_.groupType == Group.TYPE_ADMIN)

  def adminForProject(projectId: Long): Boolean =
    groups.exists(g => g.groupType == Group.TYPE_ADMIN && g.projectId == projectId)

  def getUserLocale: Locale = new Locale(this.settings.locale.getOrElse("en"))
}

/**
  * Static functions to easily create user objects
  */
object User {
  implicit val tokenWrites: Writes[RequestToken]            = Json.writes[RequestToken]
  implicit val tokenReads: Reads[RequestToken]              = Json.reads[RequestToken]
  implicit val settingsWrites: Writes[UserSettings]         = Json.writes[UserSettings]
  implicit val settingsReads: Reads[UserSettings]           = Json.reads[UserSettings]
  implicit val userGroupWrites: Writes[Group]               = Group.groupWrites
  implicit val userGroupReads: Reads[Group]                 = Group.groupReads
  implicit val locationWrites: Writes[Location]             = Json.writes[Location]
  implicit val locationReads: Reads[Location]               = Json.reads[Location]
  implicit val osmWrites: Writes[OSMProfile]                = Json.writes[OSMProfile]
  implicit val osmReads: Reads[OSMProfile]                  = Json.reads[OSMProfile]
  implicit val searchResultWrites: Writes[UserSearchResult] = Json.writes[UserSearchResult]
  implicit val projectManagerWrites: Writes[ProjectManager] = Json.writes[ProjectManager]

  implicit object UserFormat extends Format[User] {
    override def writes(o: User): JsValue = {
      implicit val taskWrites: Writes[User] = Json.writes[User]
      val original                          = Json.toJson(o)(Json.writes[User])
      val updated = o.properties match {
        case Some(p) => Utils.insertIntoJson(original, "properties", Json.parse(p), true)
        case None    => original
      }
      Utils.insertIntoJson(updated, "properties", Json.parse(o.properties.getOrElse("{}")), true)
    }

    override def reads(json: JsValue): JsResult[User] = {
      val modifiedJson: JsValue = (json \ "properties").toOption match {
        case Some(p) =>
          p match {
            case props: JsString => json
            case _ =>
              json.as[JsObject] ++ Json.obj("properties" -> p.toString())
          }
        case None => json
      }
      Json.fromJson[User](modifiedJson)(Json.reads[User])
    }
  }

  val DEFAULT_GUEST_USER_ID = -998
  val DEFAULT_SUPER_USER_ID = -999
  val DEFAULT_GROUP_ID      = -999

  val THEME_BLACK        = 0
  val THEME_BLACK_LIGHT  = 1
  val THEME_BLUE         = 2
  val THEME_BLUE_LIGHT   = 3
  val THEME_GREEN        = 4
  val THEME_GREEN_LIGHT  = 5
  val THEME_PURPLE       = 6
  val THEME_PURPLE_LIGHT = 7
  val THEME_RED          = 8
  val THEME_RED_LIGHT    = 9
  val THEME_YELLOW       = 10
  val THEME_YELLOW_LIGHT = 11
  val superGroup: Group  = Group(DEFAULT_GROUP_ID, "SUPERUSERS", 0, Group.TYPE_SUPER_USER)
  val settingsForm = Form(
    mapping(
      "defaultEditor"     -> optional(number),
      "defaultBasemap"    -> optional(number),
      "defaultBasemapId"  -> optional(text),
      "customBasemap"     -> optional(text),
      "locale"            -> optional(text),
      "email"             -> optional(text),
      "emailOptIn"        -> optional(boolean),
      "leaderboardOptOut" -> optional(boolean),
      "needsReview"       -> optional(number),
      "isReviewer"        -> optional(boolean),
      "theme"             -> optional(number)
    )(UserSettings.apply)(UserSettings.unapply)
  )

  val REVIEW_NOT_NEEDED = 0
  val REVIEW_NEEDED     = 1
  val REVIEW_MANDATORY  = 2

  /**
    * Generates a User object based on the json details and request token
    *
    * @param userXML      A XML string originally queried form the OSM details API
    * @param requestToken The access token used to retrieve the OSM details
    * @return A user object based on the XML details provided
    */
  def generate(userXML: String, requestToken: RequestToken, config: Config): User =
    generate(XML.loadString(userXML), requestToken, config)

  /**
    * Generate a User object based on the XML details and request token
    *
    * @param root         The XML details of the user based on OSM details API
    * @param requestToken The access token used to retrieve the OSM details
    * @return A user object based on the XML details provided
    */
  def generate(root: Elem, requestToken: RequestToken, config: Config): User = {
    val userXML           = (root \ "user").head
    val displayName       = userXML \@ "display_name"
    val osmAccountCreated = userXML \@ "account_created"
    val osmId             = userXML \@ "id"
    val description       = (userXML \ "description").head.text
    val avatarURL = (userXML \ "img").headOption match {
      case Some(img) => img \@ "href"
      case None      => "/assets/images/user_no_image.png"
    }
    val location = (userXML \ "home").headOption match {
      case Some(loc) => Location((loc \@ "lat").toDouble, (loc \@ "lon").toDouble)
      case None      => Location(47.608013, -122.335167)
    }
    // check whether this user is a super user
    val groups =
      if (config.superAccounts.contains(osmId) ||
          (config.superAccounts.size == 1 && config.superAccounts.head.equals("*"))) {
        List(superGroup)
      } else {
        List[Group]()
      }
    User(
      -1,
      new DateTime(),
      new DateTime(),
      OSMProfile(
        osmId.toLong,
        displayName,
        description,
        avatarURL,
        location,
        DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").parseDateTime(osmAccountCreated),
        requestToken
      ),
      groups,
      settings =
        UserSettings(theme = Some(THEME_BLUE), needsReview = Option(config.defaultNeedsReview))
    )
  }

  def superUser: User =
    User(
      DEFAULT_SUPER_USER_ID,
      DateTime.now(),
      DateTime.now(),
      OSMProfile(
        DEFAULT_SUPER_USER_ID,
        "SuperUser",
        "FULL ACCESS",
        "/assets/images/user_no_image.png",
        Location(47.608013, -122.335167),
        DateTime.now(),
        RequestToken("", "")
      ),
      List(superGroup.copy()),
      settings = UserSettings(theme = Some(THEME_BLACK))
    )

  def withDecryptedAPIKey(user: User)(implicit crypto: Crypto): User = {
    user.apiKey match {
      case Some(key) if key.nonEmpty =>
        try {
          val decryptedAPIKey = Some(s"${user.id}|${crypto.decrypt(key)}")
          new User(
            user.id,
            user.created,
            user.modified,
            user.osmProfile,
            user.groups,
            decryptedAPIKey,
            user.guest,
            user.settings,
            user.properties,
            user.score
          )
        } catch {
          case _: BadPaddingException | _: IllegalBlockSizeException =>
            LoggerFactory
              .getLogger(this.getClass)
              .debug("Invalid key found, could be that the application secret on server changed.")
            user
          case e: Throwable => throw e
        }
      case _ => user
    }
  }

  /**
    * Simple helper function that if the provided Option[User] is None, will return a guest
    * user, otherwise will simply return back the provided user
    *
    * @param user The user to check
    * @return Guest user if none, otherwise simply the provided user.
    */
  def userOrMocked(user: Option[User]): User = {
    user match {
      case Some(u) => u
      case None    => guestUser
    }
  }

  /**
    * Creates a guest user object with default information.
    */
  def guestUser: User =
    User(
      DEFAULT_GUEST_USER_ID,
      DateTime.now(),
      DateTime.now(),
      OSMProfile(
        DEFAULT_GUEST_USER_ID,
        "Guest",
        "Sign in using your OSM account for more access to MapRoulette features.",
        "/assets/images/user_no_image.png",
        Location(-33.918861, 18.423300),
        DateTime.now(),
        RequestToken("", "")
      ),
      List(),
      None,
      true,
      UserSettings(theme = Some(THEME_GREEN))
    )
}
