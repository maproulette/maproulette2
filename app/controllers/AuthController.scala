// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package controllers

import com.google.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.controllers.ControllerHelper
import org.maproulette.exception._
import org.maproulette.models.dal.DALManager
import org.maproulette.session.{Group, SessionManager, User}
import org.webjars.play.WebJarsUtil
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsString, Json}
import play.api.mvc._
import play.shaded.oauth.oauth.signpost.exception.OAuthNotAuthorizedException

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * All the authentication actions go in this class
  *
  * @author cuthbertm
  */
class AuthController @Inject() (messagesApi: MessagesApi,
                                components: ControllerComponents,
                                override val webJarsUtil: WebJarsUtil,
                                sessionManager:SessionManager,
                                override val dalManager:DALManager,
                                val config:Config) extends AbstractController(components) with I18nSupport with ControllerHelper with StatusMessages {

  import scala.concurrent.ExecutionContext.Implicits.global

  /**
    * An action to call to authenticate a user using OAuth 1.0a against the OAuth OSM Provider
    *
    * @return Redirects back to the index page containing a valid session
    */
  def authenticate() : Action[AnyContent] = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncUIExceptionCatcher(User.guestUser, config, dalManager) { () =>
      val p = Promise[Result]
      val redirect = request.getQueryString("redirect").getOrElse("")
      request.getQueryString("oauth_verifier") match {
        case Some(verifier) =>
          sessionManager.retrieveUser(verifier) onComplete {
            case Success(user) =>
              // We received the authorized tokens in the OAuth object - store it before we proceed
              p success this.withOSMSession(user, Redirect(redirect, SEE_OTHER).withHeaders(("Cache-Control", "no-cache")))
            case Failure(e) => p failure e
          }
        case None =>
          sessionManager.retrieveRequestToken(proxyRedirect(routes.AuthController.authenticate()) + s"?redirect=${getRedirectURL(request, redirect)}") match {
            case Right(t) =>
              // We received the unauthorized tokens in the OAuth object - store it before we proceed
              p success Redirect(sessionManager.redirectUrl(t.token))
                .withHeaders(("Cache-Control", "no-cache"))
                .withSession(SessionManager.KEY_TOKEN -> t.token,
                  SessionManager.KEY_SECRET -> t.secret,
                  SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString
                )
            case Left(e) => p failure e
          }
      }
      p.future
    }
  }

  def signIn(redirect:String) : Action[AnyContent] = Action.async { implicit request =>
    MPExceptionUtil.internalAsyncUIExceptionCatcher(User.guestUser, config, dalManager) { () =>
      val p = Promise[Result]
      request.body.asFormUrlEncoded match {
        case Some(data) =>
          val username = data.getOrElse("signInUsername", ArrayBuffer("")).mkString
          val apiKey = data.getOrElse("signInAPIKey", ArrayBuffer("")).mkString
          this.sessionManager.retrieveUser(username, apiKey) match {
            case Some(user) => p success this.withOSMSession(user, Redirect(getRedirectURL(request, redirect)).withHeaders(("Cache-Control", "no-chache")))
            case None => p failure new OAuthNotAuthorizedException("Invalid username or apiKey provided")
          }
        case None => p failure new OAuthNotAuthorizedException("Invalid username or apiKey provided")
      }
      p.future
    }
  }

  /**
    * Signs out the user, creating essentially a blank new session and redirects user to the index page
    *
    * @return The index html page
    */
  def signOut() : Action[AnyContent] = Action { implicit request =>
    Redirect(routes.Application.index()).withNewSession
  }

  def deleteUser(userId:Long) : Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser = true
    sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(StatusMessage("OK", JsString(s"${dalManager.user.delete(userId, user)} User deleted by super user ${user.name} [${user.id}]."))))
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
  def generateAPIKey(userId:Long = -1) : Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val newAPIUser = if (user.isSuperUser && userId != -1) {
        dalManager.user.retrieveById(userId) match {
          case Some(u) => u
          case None => throw new NotFoundException(s"No user found with id [$userId], no API key could be generated.")
        }
      } else {
        user
      }
      dalManager.user.generateAPIKey(newAPIUser, user) match {
        case Some(updated) => updated.apiKey match {
          case Some(api) => Ok(api)
          case None => NoContent
        }
        case None => NoContent
      }
    }
  }

  /**
    * Super user action that will reset all the api keys.
    *
    * @return Simple Ok if succeeded.
    */
  def resetAllAPIKeys() : Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser = true
    sessionManager.authenticatedRequest { implicit user =>
      dalManager.user.list(-1).foreach { apiUser =>
        dalManager.user.generateAPIKey(apiUser, user)
      }
      Ok
    }
  }

  /**
    * Adds a user to the Admin group for a project
    *
    * @param projectId The id of the project to add the user too
    * @return NoContent
    */
  def addUserToProject(userId:Long, projectId:Long) : Action[AnyContent] = Action.async { implicit request =>
    implicit val requireSuperUser = true
    sessionManager.authenticatedRequest { implicit user =>
      dalManager.user.retrieveById(userId) match {
        case Some(addUser) =>
          if (addUser.groups.exists(_.projectId == projectId)) {
            throw new InvalidException(s"User ${addUser.name} is already part of project $projectId")
          }
          dalManager.user.addUserToProject(addUser.osmProfile.id, projectId, Group.TYPE_ADMIN, user)
          Ok(Json.toJson(StatusMessage("OK", JsString(s"User ${addUser.name} added to project $projectId"))))
        case None => throw new NotFoundException(s"Could not find user with ID $userId")
      }
    }
  }

  def withOSMSession(user:User, result:Result) : Result = {
    result.withSession(SessionManager.KEY_TOKEN -> user.osmProfile.requestToken.token,
      SessionManager.KEY_SECRET -> user.osmProfile.requestToken.secret,
      SessionManager.KEY_USER_ID -> user.id.toString,
      SessionManager.KEY_OSM_ID -> user.osmProfile.id.toString,
      SessionManager.KEY_USER_TICK -> DateTime.now().getMillis.toString
    )
  }

  def getRedirectURL(implicit request: Request[AnyContent], redirect: String): String = {
    val redirectURL = proxyRedirect(routes.Application.index())
    val referer = request.headers.get(REFERER)
    if (StringUtils.isEmpty(redirect) && referer.isDefined) {
      referer.get
    } else if (StringUtils.isNotEmpty(redirect)) {
      if (config.mr3DevMode) {
        // In dev mode, allow redirects to anywhere so frontend can be run separately
        redirect
      }
      else {
        s"${redirectURL.substring(0, redirectURL.length - 1)}$redirect"
      }
    } else {
      redirectURL
    }
  }

  private def proxyRedirect(call:Call)(implicit request: Request[AnyContent]): String = {
    config.proxyPort match {
      case Some(port) =>
        val applicationPort = System.getProperty("http.port")
        call.absoluteURL(config.isProxySSL)
          .replaceFirst(s":$applicationPort", s"${if (port == 80) { "" } else { s":$port" }}")
      case None => call.absoluteURL(config.isProxySSL)
    }
  }
}
