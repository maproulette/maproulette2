// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.dal.UserGroupDAL
import org.maproulette.session.{Group, SearchParameters, User}
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.data.ProjectType
import play.api.db.Database
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.ListBuffer

/**
  * Specific functions for virtual projects
  *
  * @author krotstan
  */
@Singleton
class VirtualProjectDAL @Inject()(override val db: Database,
                           childDAL: ChallengeDAL,
                           surveyDAL: SurveyDAL,
                           userGroupDAL: UserGroupDAL,
                           override val permission: Permission)
  extends ProjectDAL(db, childDAL, surveyDAL, userGroupDAL, permission) {

  /**
    * Adds a challenge to a virtual project. You are required to have write access
    * to the project you are adding the challenge to
    *
    * @param projectId   The id of the virtual parent project
    * @param challengeId The id of the challenge that you are moving
    * @param c           an implicit connection
    */
  def addChallenge(projectId: Long, challengeId: Long, user: User)(implicit c: Option[Connection] = None): Option[Project] = {
    this.permission.hasWriteAccess(ProjectType(), user)(projectId)
    this.retrieveById(projectId) match {
      case Some(p) => if (!p.isVirtual.getOrElse(false)) {
          throw new InvalidException(s"Project must be a virtual project to add a challenge.")
        }
      case None => throw new NotFoundException(s"No challenge with id $challengeId found.")
    }
    this.withMRTransaction { implicit c =>
      val query = s"""INSERT INTO virtual_project_challenges (project_id, challenge_id)
                      VALUES ($projectId, $challengeId)"""
      SQL(query).execute()
      None
    }
  }

  /**
    * Removes a challenge from a virtual project. You are required to have write access
    * to the project you are removing the challenge from.
    *
    * @param projectId   The id of the virtual parent project
    * @param challengeId The id of the challenge that you are moving
    * @param c           an implicit connection
    */
  def removeChallenge(projectId: Long, challengeId: Long, user: User)(implicit c: Option[Connection] = None): Option[Project] = {
    this.permission.hasWriteAccess(ProjectType(), user)(projectId)
    this.retrieveById(projectId) match {
      case Some(p) => if (!p.isVirtual.getOrElse(false)) {
          throw new InvalidException(s"Project must be a virtual project to remove a challenge.")
        }
      case None => throw new NotFoundException(s"No challenge with id $challengeId found.")
    }
    this.withMRTransaction { implicit c =>
      val query = s"""DELETE FROM virtual_project_challenges
                      WHERE project_id=$projectId AND challenge_id=$challengeId"""
      SQL(query).execute()
      None
    }
  }
}
