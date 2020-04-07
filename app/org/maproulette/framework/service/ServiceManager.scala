/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Provider, Singleton}
import org.maproulette.data._
import org.maproulette.exception.NotFoundException

/**
  * Class storing references to all the services available.
  *
  * @author mcuthbert
  */
@Singleton
class ServiceManager @Inject() (
    projectService: Provider[ProjectService],
    groupService: Provider[GroupService],
    userService: Provider[UserService],
    commentService: Provider[CommentService],
    challengeService: Provider[ChallengeService],
    userMetricService: Provider[UserMetricService],
    virtualProjectService: Provider[VirtualProjectService]
) {
  def comment: CommentService = commentService.get()

  def userMetrics: UserMetricService = userMetricService.get()

  def virtualProject: VirtualProjectService = virtualProjectService.get()

  def getService(itemType: ItemType): ServiceMixin[_] = itemType match {
    case ProjectType()   => this.project
    case GroupType()     => this.group
    case UserType()      => this.user
    case ChallengeType() => this.challenge
    case _               => throw new NotFoundException(s"Service not found for type $itemType")
  }

  def project: ProjectService = projectService.get()

  def group: GroupService = groupService.get()

  def user: UserService = userService.get()

  def challenge: ChallengeService = challengeService.get()
}
