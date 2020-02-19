// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.Config
import org.maproulette.data.{ActionManager, QuestionAnswered, SurveyType, SnapshotManager}
import org.maproulette.exception.NotFoundException
import org.maproulette.models.dal._
import org.maproulette.models.{Answer, Challenge}
import org.maproulette.permissions.Permission
import org.maproulette.provider.ChallengeProvider
import org.maproulette.session.{SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._

/**
  * The survey controller handles all operations for the Survey objects.
  * This includes CRUD operations and searching/listing.
  * See ParentController for more details on parent object operations
  * See CRUDController for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class SurveyController @Inject() (
    override val childController: TaskController,
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val dal: SurveyDAL,
    dalManager: DALManager,
    override val tagDAL: TagDAL,
    challengeProvider: ChallengeProvider,
    wsClient: WSClient,
    permission: Permission,
    config: Config,
    components: ControllerComponents,
    override val bodyParsers: PlayBodyParsers,
    override val snapshotManager: SnapshotManager
) extends ChallengeController(
      childController,
      sessionManager,
      actionManager,
      dalManager.challenge,
      dalManager,
      tagDAL,
      challengeProvider,
      wsClient,
      permission,
      config,
      components,
      bodyParsers,
      snapshotManager
    ) {

  // The type of object that this controller deals with.
  override implicit val itemType = SurveyType()

  /**
    * Classes can override this function to inject values into the object before it is sent along
    * with the response
    *
    * @param obj the object being sent in the response
    * @return A Json representation of the object
    */
  override def inject(obj: Challenge)(implicit request: Request[Any]) = {
    val json = super.inject(obj)
    // if no answers provided with Challenge, then provide the default answers
    val answers = this.dalManager.survey.getAnswers(obj.id) match {
      case a if a.isEmpty => List(Challenge.defaultAnswerValid, Challenge.defaultAnswerInvalid)
      case a              => a
    }
    Utils.insertIntoJson(json, Challenge.KEY_ANSWER, Json.toJson(answers))
  }

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user: User): JsValue = {
    val jsonBody = super.updateCreateBody(body, user: User)
    //if answers are supplied in a simple json string array, then convert to the answer types
    val answerArray = (jsonBody \ "answers").as[List[String]].map(a => Answer(answer = a))
    Utils.insertIntoJson(jsonBody, "answers", answerArray, true)
  }

  /**
    * Answers a question for a survey
    *
    * @param challengeId The id of the survey
    * @param taskId      The id of the task being viewed
    * @param answerId    The id of the answer
    * @return
    */
  def answerSurveyQuestion(
      challengeId: Long,
      taskId: Long,
      answerId: Long,
      comment: String
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      // make sure that the survey and answer exists first
      this.dal.retrieveById(challengeId) match {
        case Some(challenge) =>
          val ans =
            if (answerId != Challenge.defaultAnswerValid.id && answerId != Challenge.defaultAnswerInvalid.id) {
              this.dalManager.survey.getAnswers(challengeId).find(_.id == answerId) match {
                case None =>
                  throw new NotFoundException(
                    s"Requested answer [$answerId] for survey does not exist."
                  )
                case Some(a) => a.answer
              }
            } else if (answerId == Challenge.defaultAnswerValid.id) {
              Challenge.defaultAnswerValid.answer
            } else {
              Challenge.defaultAnswerInvalid.answer
            }
          this.dal.answerQuestion(challenge, taskId, answerId, user)
          this.childController.customTaskStatus(taskId, QuestionAnswered(answerId), user, comment)
          NoContent
        case None =>
          throw new NotFoundException(
            s"Requested survey [$challengeId] to answer question from does not exist."
          )
      }
    }
  }
}
