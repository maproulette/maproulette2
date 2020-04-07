/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.controller

import javax.inject.Inject
import org.maproulette.data.ActionManager
import org.maproulette.framework.service.VirtualProjectService
import org.maproulette.session.SessionManager
import play.api.mvc._

/**
  * The virtual project controller handles all operations specific to Virtual
  * Project objects. It extendes ProjectController.
  *
  * See ProjectController for more details on all project object operations
  *
  * @author krotstan
  */
class VirtualProjectController @Inject() (
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val bodyParsers: PlayBodyParsers,
    virtualProjectService: VirtualProjectService,
    components: ControllerComponents
) extends AbstractController(components)
    with MapRouletteController {

  /**
    * Adds a challenge to a virtual project. This requires Write access on the project
    *
    * @param projectId The virtual project to add the challenge to
    * @param challengeId  The challenge that you are adding
    * @return Ok with no message
    */
  def addChallenge(projectId: Long, challengeId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      sessionManager.authenticatedRequest { implicit user =>
        this.virtualProjectService.addChallenge(projectId, challengeId, user)
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
  def removeChallenge(projectId: Long, challengeId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      sessionManager.authenticatedRequest { implicit user =>
        this.virtualProjectService.removeChallenge(projectId, challengeId, user)
        Ok
      }
  }
}
