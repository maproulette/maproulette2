/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import org.maproulette.data.ProjectType
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.framework.model.{Challenge, Project, User, VirtualProject}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseParameter, SubQueryFilter}
import org.maproulette.framework.repository.VirtualProjectRepository
import org.maproulette.permissions.Permission

/**
  * @author mcuthbert
  */
@Singleton
class VirtualProjectService @Inject() (
    repository: VirtualProjectRepository,
    projectService: ProjectService,
    challengeService: ChallengeService,
    permission: Permission
) extends ServiceMixin[Project] {

  /**
    * Not Supported, use ProjectService.query instead
    *
    * @param query The query to match against to retrieve the objects
    * @return The list of objects
    */
  override def query(query: Query): List[Project] =
    throw new Exception("Function not supported, use ProjectService.query instead")

  /**
    * Retrieves an object of that type
    *
    * @param id The identifier for the object
    * @return An optional object, None if not found
    */
  override def retrieve(id: Long): Option[Project] =
    this.projectService
      .query(
        Query.simple(
          List(BaseParameter(Project.FIELD_VIRTUAL, true), BaseParameter(Project.FIELD_ID, id))
        )
      )
      .headOption

  /**
    * Will list all the virtual challenge children of a project
    *
    * @param projectId The id of the project (Virtual)
    * @param user The user making the request
    * @return A list of challenges
    */
  def listVirtualChildren(projectId: Long, user: User): List[Challenge] = {
    this.challengeService.query(
      Query.simple(
        List(
          SubQueryFilter(
            Challenge.FIELD_ID,
            Query.simple(
              List(BaseParameter(VirtualProject.FIELD_PROJECT_ID, projectId)),
              "SELECT challenge_id FROM virtual_project_challenges"
            )
          )
        )
      )
    )
  }

  /**
    * Adds a challenge to a virtual project. You are required to have write access
    * to the project you are adding the challenge to
    *
    * @param projectId   The id of the virtual parent project
    * @param challengeId The id of the challenge that you are moving
    */
  def addChallenge(projectId: Long, challengeId: Long, user: User): Boolean = {
    this.permission.hasWriteAccess(ProjectType(), user)(projectId)
    this.projectService.retrieve(projectId) match {
      case Some(p) =>
        if (!p.isVirtual.getOrElse(false)) {
          throw new InvalidException(s"Project must be a virtual project to add a challenge.")
        }
      case None => throw new NotFoundException(s"No project found with id $projectId found.")
    }
    this.repository.addChallenge(projectId, challengeId)
  }

  /**
    * Removes a challenge from a virtual project. You are required to have write access
    * to the project you are removing the challenge from.
    *
    * @param projectId   The id of the virtual parent project
    * @param challengeId The id of the challenge that you are moving
    */
  def removeChallenge(projectId: Long, challengeId: Long, user: User): Boolean = {
    this.permission.hasWriteAccess(ProjectType(), user)(projectId)
    this.projectService.retrieve(projectId) match {
      case Some(p) =>
        if (!p.isVirtual.getOrElse(false)) {
          throw new InvalidException(s"Project must be a virtual project to remove a challenge.")
        }
      case None => throw new NotFoundException(s"No challenge with id $challengeId found.")
    }
    this.repository.removeChallenge(projectId, challengeId)
  }
}
