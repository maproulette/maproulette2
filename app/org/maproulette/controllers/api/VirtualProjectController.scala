// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils

import org.maproulette.data.{ActionManager, ProjectType, TaskViewed}
import org.maproulette.models.dal.{ProjectDAL, TaskDAL, VirtualProjectDAL}
import org.maproulette.models.{Challenge, ClusteredPoint, Project}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc._

/**
  * The virtual project controller handles all operations specific to Virtual
  * Project objects. It extendes ProjectController.
  *
  * See ProjectController for more details on all project object operations
  *
  * @author krotstan
  */
class VirtualProjectController @Inject()(override val childController: ChallengeController,
                                  override val sessionManager: SessionManager,
                                  override val actionManager: ActionManager,
                                  override val dal: ProjectDAL,
                                  virtualProjectDAL: VirtualProjectDAL,
                                  components: ControllerComponents,
                                  taskDAL: TaskDAL,
                                  override val bodyParsers: PlayBodyParsers)
  extends ProjectController(childController, sessionManager, actionManager, dal, components, taskDAL, bodyParsers) {

  /**
    * Adds a challenge to a virtual project. This requires Write access on the project
    *
    * @param projectId The virtual project to add the challenge to
    * @param challengeId  The challenge that you are adding
    * @return Ok with no message
    */
  def addChallenge(projectId: Long, challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      virtualProjectDAL.addChallenge(projectId, challengeId, user)
      Ok
    }
  }

  /**
    * Removes a challenge from a virtual project. This requires Write access on the project
    *
    * @param projectId The virtual project to remove the challenge from
    * @param challengeId  The challenge that you are removing
    * @return Ok with no message
    */
  def removeChallenge(projectId: Long, challengeId: Long): Action[AnyContent] = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      virtualProjectDAL.removeChallenge(projectId, challengeId, user)
      Ok
    }
  }
}
