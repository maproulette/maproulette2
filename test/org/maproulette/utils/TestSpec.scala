/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.utils

import com.google.inject.util.Providers
import org.joda.time.DateTime
import org.maproulette.data.{ActionManager, DataManager, StatusActionManager}
import org.maproulette.framework.model._
import org.maproulette.framework.service._
import org.maproulette.models._
import org.maproulette.models.dal._
import org.maproulette.permissions.Permission
import org.maproulette.session._
import org.mockito.ArgumentMatchers.{eq => eqM, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.db.Databases
import play.api.libs.oauth.RequestToken

/**
  * @author mcuthbert
  */
trait TestSpec extends PlaySpec with MockitoSugar {

  val testDb = Databases.inMemory()

  // 3x Groups (Admin, Write, Read)
  val adminGroup = Group(1, "Mocked_1_Admin", 1, Group.TYPE_ADMIN)
  val writeGroup = Group(2, "Mocked_1_Write", 1, Group.TYPE_WRITE_ACCESS)
  val readGroup  = Group(3, "Mocked_1_Read", 1, Group.TYPE_READ_ONLY)

  //projects
  val project1 = Project(
    1,
    101,
    "Mocked_1",
    DateTime.now(),
    DateTime.now(),
    None,
    List(adminGroup, writeGroup, readGroup)
  )

  //challenges
  val challenge1 = Challenge(
    1,
    "Challenge1",
    DateTime.now(),
    DateTime.now(),
    None,
    false,
    None,
    ChallengeGeneral(101, 1, ""),
    ChallengeCreation(),
    ChallengePriority(),
    ChallengeExtra()
  )

  //tasks
  val task1               = Task(1, "Task1", DateTime.now(), DateTime.now(), 1, None, None, "")
  val tagDAL              = mock[TagDAL]
  val taskDAL             = mock[TaskDAL]
  val challengeDAL        = mock[ChallengeDAL]
  val virtualChallengeDAL = mock[VirtualChallengeDAL]
  val virtualChallenge = VirtualChallenge(
    1,
    "VChallenge",
    DateTime.now(),
    DateTime.now(),
    None,
    101,
    SearchParameters(),
    DateTime.now()
  )
  val projectService = mock[ProjectService]
  val userService    = mock[UserService]
  val adminUser = User(
    1,
    DateTime.now(),
    DateTime.now(),
    OSMProfile(1, "AdminUser", "", "", Location(0, 0), DateTime.now(), RequestToken("", "")),
    List(adminGroup)
  )
  val actionManager = mock[ActionManager]
  val dataManager   = mock[DataManager]

  val statusActionManager = mock[StatusActionManager]
  val taskBundleDAL       = mock[TaskBundleDAL]
  val taskReviewDAL       = mock[TaskReviewDAL]
  val taskClusterDAL      = mock[TaskClusterDAL]
  val dalManager = new DALManager(
    tagDAL,
    taskDAL,
    challengeDAL,
    virtualChallengeDAL,
    notificationDAL,
    actionManager,
    dataManager,
    taskBundleDAL,
    taskReviewDAL,
    taskClusterDAL,
    statusActionManager
  )
  val groupService          = mock[GroupService]
  val commentService        = mock[CommentService]
  val challengeService      = mock[ChallengeService]
  val userMetricService     = mock[UserMetricService]
  val virtualProjectService = mock[VirtualProjectService]
  val serviceManager = new ServiceManager(
    Providers.of[ProjectService](projectService),
    Providers.of[GroupService](groupService),
    Providers.of[UserService](userService),
    Providers.of[CommentService](commentService),
    Providers.of[ChallengeService](challengeService),
    Providers.of[UserMetricService](userMetricService),
    Providers.of[VirtualProjectService](virtualProjectService)
  )
  val permission = new Permission(Providers.of[DALManager](dalManager), serviceManager)
  var writeUser = User(
    2,
    DateTime.now(),
    DateTime.now(),
    OSMProfile(2, "WriteUser", "", "", Location(0, 0), DateTime.now(), RequestToken("", "")),
    List(writeGroup)
  )
  var readUser = User(
    3,
    DateTime.now(),
    DateTime.now(),
    OSMProfile(3, "ReadUser", "", "", Location(0, 0), DateTime.now(), RequestToken("", "")),
    List(readGroup)
  )
  var owner = User(
    100,
    DateTime.now(),
    DateTime.now(),
    OSMProfile(101, "DefaultOwner", "", "", Location(0, 0), DateTime.now(), RequestToken("", "")),
    List.empty //generally an owner would have to be in an admin group, but here we are making sure the owner permissions are respective regardless or group or lack there of
  )
  var notificationDAL = mock[NotificationDAL]

  def setupMocks(): Unit = {
    // Mocks for Tags
    when(tagDAL.retrieveById(0, None)).thenReturn(None)
    when(tagDAL.retrieveById(1, None)).thenReturn(Some(Tag(1, "Tag")))

    // Mocks for Tasks
    when(taskDAL.retrieveById(0L, None)).thenReturn(None)
    doAnswer(_ => None).when(taskDAL.asInstanceOf[BaseDAL[Long, Task]]).retrieveById(0L, None)
    when(taskDAL.retrieveById(1L, None)).thenReturn(Some(task1))
    doAnswer(_ => Some(task1))
      .when(taskDAL.asInstanceOf[BaseDAL[Long, Task]])
      .retrieveById(1L, None)
    when(taskDAL.retrieveRootObject(eqM(Right(task1)), any())(any())).thenReturn(Some(project1))

    // Mocks for Challenge DAL
    when(challengeDAL.retrieveById(0L, None)).thenReturn(None)
    doAnswer(_ => None)
      .when(challengeDAL.asInstanceOf[BaseDAL[Long, Challenge]])
      .retrieveById(0L, None)
    when(challengeDAL.retrieveById(1L, None)).thenReturn(Some(challenge1))
    doAnswer(_ => Some(challenge1))
      .when(challengeDAL.asInstanceOf[BaseDAL[Long, Challenge]])
      .retrieveById(1L, None)
    when(challengeDAL.retrieveRootObject(eqM(Right(challenge1)), any())(any()))
      .thenReturn(Some(project1))
    // Mocks for Challenge Service
    when(challengeService.retrieve(0L)).thenReturn(None)
    when(challengeService.retrieve(1L)).thenReturn(Some(challenge1))

    // Mocks for Virtual Challenges
    when(virtualChallengeDAL.retrieveById(0, None)).thenReturn(None)
    doAnswer(_ => None)
      .when(virtualChallengeDAL.asInstanceOf[BaseDAL[Long, VirtualChallenge]])
      .retrieveById(0L, None)
    when(virtualChallengeDAL.retrieveById(1, None)).thenReturn(Some(virtualChallenge))
    doAnswer(_ => Some(virtualChallenge))
      .when(virtualChallengeDAL.asInstanceOf[BaseDAL[Long, VirtualChallenge]])
      .retrieveById(1L, None)

    // Mocks for Projects
    when(this.projectService.retrieve(0)).thenReturn(None)
    when(this.projectService.retrieve(1)).thenReturn(Some(project1))

    // Mocks for users
    when(this.userService.retrieve(-999L)).thenReturn(Some(User.superUser))
    doAnswer(_ => Some(User.superUser)).when(this.userService).retrieve(-999L)
    when(this.userService.retrieve(-1L)).thenReturn(Some(User.guestUser))
    doAnswer(_ => Some(User.guestUser)).when(this.userService).retrieve(-1L)
    when(this.userService.retrieve(0L)).thenReturn(None)
    doAnswer(_ => None).when(this.userService).retrieve(0L)
    when(this.userService.retrieve(1L)).thenReturn(Some(adminUser))
    doAnswer(_ => Some(adminUser)).when(this.userService).retrieve(1L)
    when(this.userService.retrieve(2L)).thenReturn(Some(writeUser))
    doAnswer(_ => Some(writeUser)).when(this.userService).retrieve(2L)
    when(this.userService.retrieve(3L)).thenReturn(Some(readUser))
    doAnswer(_ => Some(readUser)).when(this.userService).retrieve(3L)
    when(this.userService.retrieve(100L)).thenReturn(Some(owner))
    doAnswer(_ => Some(owner)).when(this.userService).retrieve(100L)

    // Mocks for User Groups
    when(this.groupService.retrieve(0)).thenReturn(None)
    when(this.groupService.retrieve(1)).thenReturn(Some(adminGroup))
    when(this.groupService.retrieve(2)).thenReturn(Some(writeGroup))
    when(this.groupService.retrieve(3)).thenReturn(Some(readGroup))
  }
}
