package org.maproulette

import javax.inject.{Inject, Singleton}

import org.maproulette.actions.Actions
import play.api.Application
import play.api.libs.oauth.ConsumerKey

import scala.concurrent.duration.Duration

case class OSMOAuth(userDetailsURL:String, requestTokenURL:String, accessTokenURL:String,
                    authorizationURL:String, consumerKey:ConsumerKey)

case class OSMQLProvider(providerURL:String, requestTimeout:Duration)

/**
  * @author cuthbertm
  */
@Singleton
class Config @Inject() (implicit val application:Application) {
  private val config = application.configuration

  lazy val logoURL = this.config.getString(Config.KEY_LOGO) match {
    case Some(logo) => logo
    case None => "/assets/images/logo.png"// default to the Map Roulette Icon
  }

  lazy val superKey : Option[String] = this.config.getString(Config.KEY_SUPER_KEY)

  lazy val superAccounts : List[String] = this.config.getString(Config.KEY_SUPER_ACCOUNTS) match {
    case Some(accs) => accs.split(",").toList
    case None => List.empty
  }

  lazy val ignoreSessionTimeout : Boolean = this.sessionTimeout == -1

  lazy val isDebugMode : Boolean =
    this.config.getBoolean(Config.KEY_DEBUG).getOrElse(false)

  lazy val actionLevel : Int =
    this.config.getInt(Config.KEY_ACTION_LEVEL).getOrElse(Actions.ACTION_LEVEL_2)

  lazy val numberOfChallenges : Int =
    this.config.getInt(Config.KEY_NUM_OF_CHALLENGES).getOrElse(Config.DEFAULT_NUM_OF_CHALLENGES)

  lazy val numberOfActivities : Int =
    this.config.getInt(Config.KEY_RECENT_ACTIVITY).getOrElse(Config.DEFAULT_RECENT_ACTIVITY)

  lazy val getOSMOauth : OSMOAuth = {
    val osmServer = this.config.getString(Config.KEY_OSM_SERVER).get
    OSMOAuth(
      osmServer + this.config.getString(Config.KEY_OSM_USER_DETAILS_URL).get,
      osmServer + this.config.getString(Config.KEY_OSM_REQUEST_TOKEN_URL).get,
      osmServer + this.config.getString(Config.KEY_OSM_ACCESS_TOKEN_URL).get,
      osmServer + this.config.getString(Config.KEY_OSM_AUTHORIZATION_URL).get,
      ConsumerKey(this.config.getString(Config.KEY_OSM_CONSUMER_KEY).get,
        this.config.getString(Config.KEY_OSM_CONSUMER_SECRET).get)
    )
  }

  lazy val getOSMQLProvider : OSMQLProvider = OSMQLProvider(
    this.config.getString(Config.KEY_OSM_QL_PROVIDER).get,
    Duration(this.config.getInt(Config.KEY_OSM_QL_TIMEOUT).getOrElse(Config.DEFAULT_OSM_QL_TIMEOUT), "s")
  )

  lazy val getSemanticVersion : String =
    this.config.getString(Config.KEY_SEMANTIC_VERSION).getOrElse("N/A")

  lazy val sessionTimeout : Long = this.config.getLong(Config.KEY_SESSION_TIMEOUT).getOrElse(Config.DEFAULT_SESSION_TIMEOUT)

  lazy val taskReset : Int = this.config.getInt(Config.KEY_TASK_RESET).getOrElse(Config.DEFAULT_TASK_RESET)
}

object Config {
  val GROUP_MAPROULETTE = "maproulette"
  val KEY_LOGO = s"$GROUP_MAPROULETTE.logo"
  val KEY_SUPER_KEY = s"$GROUP_MAPROULETTE.super.key"
  val KEY_SUPER_ACCOUNTS = s"$GROUP_MAPROULETTE.super.accounts"
  val KEY_DEBUG = s"$GROUP_MAPROULETTE.debug"
  val KEY_ACTION_LEVEL = s"$GROUP_MAPROULETTE.action.level"
  val KEY_NUM_OF_CHALLENGES = s"$GROUP_MAPROULETTE.limits.challenges"
  val KEY_RECENT_ACTIVITY = s"$GROUP_MAPROULETTE.limits.activities"
  val KEY_SEMANTIC_VERSION = s"$GROUP_MAPROULETTE.version"
  val KEY_SESSION_TIMEOUT = s"$GROUP_MAPROULETTE.session.timeout"
  val KEY_TASK_RESET = s"$GROUP_MAPROULETTE.task.reset"

  val GROUP_OSM = "osm"
  val KEY_OSM_SERVER = s"$GROUP_OSM.server"
  val KEY_OSM_USER_DETAILS_URL = s"$GROUP_OSM.userDetails"
  val KEY_OSM_REQUEST_TOKEN_URL = s"$GROUP_OSM.requestTokenURL"
  val KEY_OSM_ACCESS_TOKEN_URL = s"$GROUP_OSM.accessTokenURL"
  val KEY_OSM_AUTHORIZATION_URL = s"$GROUP_OSM.authorizationURL"
  val KEY_OSM_CONSUMER_KEY = s"$GROUP_OSM.consumerKey"
  val KEY_OSM_CONSUMER_SECRET = s"$GROUP_OSM.consumerSecret"

  val KEY_OSM_QL_PROVIDER = s"$GROUP_OSM.ql.provider"
  val KEY_OSM_QL_TIMEOUT = s"$GROUP_OSM.ql.timeout"

  val DEFAULT_SESSION_TIMEOUT = 3600000L
  val DEFAULT_TASK_RESET= 7
  val DEFAULT_OSM_QL_TIMEOUT = 25
  val DEFAULT_NUM_OF_CHALLENGES = 3
  val DEFAULT_RECENT_ACTIVITY = 5
}
