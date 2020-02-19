package org.maproulette.utils

import com.google.inject.util.Providers
import org.joda.time.DateTime
import org.maproulette.data.{ActionManager, DataManager, StatusActionManager}
import org.maproulette.models._
import org.maproulette.models.dal._
import org.maproulette.permissions.Permission
import org.maproulette.session._
import org.maproulette.session.dal.{UserDAL, UserGroupDAL}
import org.mockito.ArgumentMatchers.{eq => eqM, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.db.Databases
import play.api.libs.oauth.RequestToken

/**
  * @author mcuthbert
  */
trait TestSpec extends MockitoSugar {

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

  //surveys
  val survey1 = Challenge(
    1,
    "Survey1",
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
  val surveyDAL  = mock[SurveyDAL]
  val projectDAL = mock[ProjectDAL]
  val userDAL    = mock[UserDAL]
  val adminUser = User(
    1,
    DateTime.now(),
    DateTime.now(),
    OSMProfile(1, "AdminUser", "", "", Location(0, 0), DateTime.now(), RequestToken("", "")),
    List(adminGroup)
  )
  val userGroupDAL  = mock[UserGroupDAL]
  val actionManager = mock[ActionManager]
  val dataManager   = mock[DataManager]

  val statusActionManager = mock[StatusActionManager]
  val commentDAL          = mock[CommentDAL]
  val taskBundleDAL       = mock[TaskBundleDAL]
  val taskReviewDAL       = mock[TaskReviewDAL]
  val taskClusterDAL      = mock[TaskClusterDAL]
  val dalManager = new DALManager(
    tagDAL,
    taskDAL,
    challengeDAL,
    virtualChallengeDAL,
    surveyDAL,
    projectDAL,
    userDAL,
    userGroupDAL,
    notificationDAL,
    actionManager,
    dataManager,
    commentDAL,
    taskBundleDAL,
    taskReviewDAL,
    taskClusterDAL,
    statusActionManager
  )
  val permission = new Permission(Providers.of[DALManager](dalManager))
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
    doAnswer(_ => Some(project1))
      .when(taskDAL.asInstanceOf[BaseDAL[Long, Task]])
      .retrieveRootObject(eqM(Right(task1)), any())(any())

    // Mocks for Challenges
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

    // Mocks for Virtual Challenges
    when(virtualChallengeDAL.retrieveById(0, None)).thenReturn(None)
    doAnswer(_ => None)
      .when(virtualChallengeDAL.asInstanceOf[BaseDAL[Long, VirtualChallenge]])
      .retrieveById(0L, None)
    when(virtualChallengeDAL.retrieveById(1, None)).thenReturn(Some(virtualChallenge))
    doAnswer(_ => Some(virtualChallenge))
      .when(virtualChallengeDAL.asInstanceOf[BaseDAL[Long, VirtualChallenge]])
      .retrieveById(1L, None)

    // Mocks for Surveys
    when(surveyDAL.retrieveById(0L, None)).thenReturn(None)
    doAnswer(_ => None)
      .when(surveyDAL.asInstanceOf[BaseDAL[Long, Challenge]])
      .retrieveById(0L, None)
    when(surveyDAL.retrieveById(1L, None)).thenReturn(Some(survey1))
    doAnswer(_ => Some(survey1))
      .when(surveyDAL.asInstanceOf[BaseDAL[Long, Challenge]])
      .retrieveById(1L, None)

    // Mocks for Projects
    when(projectDAL.retrieveById(0, None)).thenReturn(None)
    when(projectDAL.retrieveById(1, None)).thenReturn(Some(project1))

    // Mocks for users
    when(userDAL.retrieveById(-999L, None)).thenReturn(Some(User.superUser))
    doAnswer(_ => Some(User.superUser))
      .when(userDAL.asInstanceOf[BaseDAL[Long, User]])
      .retrieveById(-999L, None)
    when(userDAL.retrieveById(-1L, None)).thenReturn(Some(User.guestUser))
    doAnswer(_ => Some(User.guestUser))
      .when(userDAL.asInstanceOf[BaseDAL[Long, User]])
      .retrieveById(-1L, None)
    when(userDAL.retrieveById(0L, None)).thenReturn(None)
    doAnswer(_ => None).when(userDAL.asInstanceOf[BaseDAL[Long, User]]).retrieveById(0L, None)
    when(userDAL.retrieveById(1L, None)).thenReturn(Some(adminUser))
    doAnswer(_ => Some(adminUser))
      .when(userDAL.asInstanceOf[BaseDAL[Long, User]])
      .retrieveById(1L, None)
    when(userDAL.retrieveById(2L, None)).thenReturn(Some(writeUser))
    doAnswer(_ => Some(writeUser))
      .when(userDAL.asInstanceOf[BaseDAL[Long, User]])
      .retrieveById(2L, None)
    when(userDAL.retrieveById(3L, None)).thenReturn(Some(readUser))
    doAnswer(_ => Some(readUser))
      .when(userDAL.asInstanceOf[BaseDAL[Long, User]])
      .retrieveById(3L, None)
    when(userDAL.retrieveById(100L, None)).thenReturn(Some(owner))
    doAnswer(_ => Some(owner))
      .when(userDAL.asInstanceOf[BaseDAL[Long, User]])
      .retrieveById(100L, None)

    // Mocks for User Groups
    when(userGroupDAL.getGroup(0, None)).thenReturn(None)
    when(userGroupDAL.getGroup(1, None)).thenReturn(Some(adminGroup))
    when(userGroupDAL.getGroup(2, None)).thenReturn(Some(writeGroup))
    when(userGroupDAL.getGroup(3, None)).thenReturn(Some(readGroup))
  }
}
