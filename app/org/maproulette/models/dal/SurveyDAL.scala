// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Provider, Singleton}
import org.maproulette.Config
import org.maproulette.exception.InvalidException
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.User
import play.api.db.Database
import play.api.libs.json.JsValue

/**
  * @author cuthbertm
  */
@Singleton
class SurveyDAL @Inject() (
    override val db: Database,
    taskDAL: TaskDAL,
    override val tagDAL: TagDAL,
    projectDAL: Provider[ProjectDAL],
    notificationDAL: Provider[NotificationDAL],
    override val permission: Permission,
    config: Config
) extends ChallengeDAL(db, taskDAL, tagDAL, projectDAL, notificationDAL, permission, config) {

  private val answerParser: RowParser[Answer] = {
    get[Long]("answers.id") ~
      get[String]("answers.answer") map {
      case id ~ answer => new Answer(id, answer)
    }
  }

  /**
    * Retrieves the answers for the survey given the survey id
    *
    * @param surveyId The id for the survey
    * @return List of answers for the survey
    */
  def getAnswers(surveyId: Long)(implicit c: Option[Connection] = None): List[Answer] = {
    val answers = this.withMRConnection { implicit c =>
      SQL"""SELECT * FROM answers WHERE survey_id = $surveyId""".as(this.answerParser.*)
    }
    // if no answers are found, then use the default answers
    answers match {
      case a if a.isEmpty => List(Challenge.defaultAnswerValid, Challenge.defaultAnswerInvalid)
      case a              => a
    }
  }

  /**
    * Answers a question by inserting a record in the survey_answers table. This will allow users
    * to answer the question for the same task multiple times. This way you will get an idea of the
    * answers based on multiple users feedback
    *
    * @param survey   The survey that is being evaluated
    * @param taskId   The id of the task that is viewed when answering the question
    * @param answerId The id for the selected answer
    * @param user     The user answering the question, if none will default to a guest user on the database
    * @return
    */
  def answerQuestion(survey: Challenge, taskId: Long, answerId: Long, user: User)(
      implicit c: Option[Connection] = None
  ): Option[Long] = {
    this.withMRTransaction { implicit c =>
      SQL"""INSERT INTO survey_answers (osm_user_id, project_id, survey_id, task_id, answer_id)
            VALUES (${user.osmProfile.id}, ${survey.general.parent}, ${survey.id}, $taskId, $answerId)"""
        .executeInsert()
    }
  }

  /**
    * Inserts answers for a new Challenge into the database, it will use the insert from the Challenge
    * super class and then insert all the answers into the answer table
    *
    * @param challenge The challenge associated with the answer
    * @param answers   The list of answers for the survey
    * @param user      The user executing the request
    * @return The object that was inserted into the database. This will include the newly created id
    */
  def insertAnswers(challenge: Challenge, answers: List[String], user: User)(
      implicit c: Option[Connection] = None
  ): Unit = {
    if (answers.size < 2) {
      throw new InvalidException("At least 2 answers required for creating a survey")
    }
    this.permission.hasObjectWriteAccess(challenge, user)
    this.withMRTransaction { implicit c =>
      // insert the answers into the table
      val sqlQuery =
        s"""INSERT INTO answers (survey_id, answer) VALUES (${challenge.id}, {answer})"""
      val parameters = answers.map(answer => {
        Seq[NamedParameter]("answer" -> answer)
      })
      BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
    }
  }

  /**
    * Updates a Survey. It will use the update from the Challenge super class, then delete all
    * delete/update/add new answers to the answers table
    *
    * @param updates The updates in json format
    * @param id      The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(
      updates: JsValue,
      user: User
  )(implicit id: Long, c: Option[Connection] = None): Option[Challenge] = {
    this.withMRTransaction { implicit c =>
      val updatedChallenge     = super.update(updates, user)
      implicit val answerReads = Challenge.answerReads
      // list of answers to delete
      (updates \ "answers" \ "delete").asOpt[List[Long]] match {
        case Some(values) => SQL"""DELETE FROM answers WHERE id IN ($values)""".execute()
        case None         => //ignore
      }
      (updates \ "answers" \ "update").asOpt[List[Answer]] match {
        case Some(values) =>
          val sqlQuery = """UPDATE answers SET answer = {answer} WHERE id = {id}"""
          val parameters = values.map(answer => {
            Seq[NamedParameter]("answer" -> answer.answer, "id" -> answer.id)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
        case None => //ignore
      }
      (updates \ "answers" \ "add").asOpt[List[Answer]] match {
        case Some(values) =>
          val sqlQuery = s"""INSERT INTO answers (survey_id, answer) VALUES ($id, {answer})"""
          val parameters = values.map(answer => {
            Seq[NamedParameter]("answer" -> answer.answer)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
        case None => //ignore
      }
      updatedChallenge
    }
  }
}
