// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import javax.inject.{Inject, Singleton}
import org.maproulette.data._
import org.maproulette.session.dal.{UserDAL, UserGroupDAL}

/**
  * Factory that contains references to all the DAL's in the system
  *
  * @author cuthbertm
  */
@Singleton
class DALManager @Inject() (
    tagDAL: TagDAL,
    taskDAL: TaskDAL,
    challengeDAL: ChallengeDAL,
    virtualChallengeDAL: VirtualChallengeDAL,
    surveyDAL: SurveyDAL,
    projectDAL: ProjectDAL,
    userDAL: UserDAL,
    userGroupDAL: UserGroupDAL,
    notificationDAL: NotificationDAL,
    actionManager: ActionManager,
    dataManager: DataManager,
    commentDAL: CommentDAL,
    taskBundleDAL: TaskBundleDAL,
    taskReviewDAL: TaskReviewDAL,
    taskClusterDAL: TaskClusterDAL,
    statusActionManager: StatusActionManager
) {
  def tag: TagDAL = tagDAL

  def task: TaskDAL = taskDAL

  def challenge: ChallengeDAL = challengeDAL

  def virtualChallenge: VirtualChallengeDAL = virtualChallengeDAL

  def survey: SurveyDAL = surveyDAL

  def project: ProjectDAL = projectDAL

  def user: UserDAL = userDAL

  def userGroup: UserGroupDAL = userGroupDAL

  def notification: NotificationDAL = notificationDAL

  def action: ActionManager = actionManager

  def data: DataManager = dataManager

  def statusAction: StatusActionManager = statusActionManager

  def comment: CommentDAL = commentDAL

  def taskBundle: TaskBundleDAL = taskBundleDAL

  def taskReview: TaskReviewDAL = taskReviewDAL

  def taskCluster: TaskClusterDAL = taskClusterDAL

  def getManager(itemType: ItemType): BaseDAL[Long, _] = {
    itemType match {
      case ProjectType()          => projectDAL
      case ChallengeType()        => challengeDAL
      case VirtualChallengeType() => virtualChallengeDAL
      case SurveyType()           => surveyDAL
      case TaskType()             => taskDAL
      case UserType()             => userDAL
      case TagType()              => tagDAL
    }
  }
}
