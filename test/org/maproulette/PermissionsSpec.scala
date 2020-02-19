package org.maproulette

import org.maproulette.data._
import org.maproulette.session.User
import org.maproulette.utils.TestSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class PermissionsSpec extends PlaySpec with TestSpec with BeforeAndAfterAll {

  implicit val id: Long = 1L

  override def beforeAll(): Unit = {
    this.setupMocks()
  }

  "Permissions" should {
    // Everybody can read any project
    "Read access on projects" in {
      permission.hasReadAccess(ProjectType(), User.guestUser)
      permission.hasReadAccess(ProjectType(), userDAL.retrieveById(1, None).get)
      permission.hasReadAccess(ProjectType(), userDAL.retrieveById(2, None).get)
      permission.hasReadAccess(ProjectType(), userDAL.retrieveById(3, None).get)
      permission.hasReadAccess(ProjectType(), userDAL.retrieveById(100, None).get)
    }

    // Everybody can read any challenge
    "Read access on challenges" in {
      permission.hasReadAccess(ChallengeType(), User.guestUser)
      permission.hasReadAccess(ChallengeType(), userDAL.retrieveById(1, None).get)
      permission.hasReadAccess(ChallengeType(), userDAL.retrieveById(2, None).get)
      permission.hasReadAccess(ChallengeType(), userDAL.retrieveById(3, None).get)
      permission.hasReadAccess(ChallengeType(), userDAL.retrieveById(100, None).get)
    }

    // Everybody can read any survey
    "Read access on Surveys" in {
      permission.hasReadAccess(SurveyType(), User.guestUser)
      permission.hasReadAccess(SurveyType(), userDAL.retrieveById(1, None).get)
      permission.hasReadAccess(SurveyType(), userDAL.retrieveById(2, None).get)
      permission.hasReadAccess(SurveyType(), userDAL.retrieveById(3, None).get)
      permission.hasReadAccess(SurveyType(), userDAL.retrieveById(100, None).get)
    }

    // Everybody can read any task
    "Read access on Tasks" in {
      permission.hasReadAccess(TaskType(), User.guestUser)(1)
      permission.hasReadAccess(TaskType(), userDAL.retrieveById(1, None).get)
      permission.hasReadAccess(TaskType(), userDAL.retrieveById(2, None).get)
      permission.hasReadAccess(TaskType(), userDAL.retrieveById(3, None).get)
    }

    // Only Super users and the actual user can read user information
    "Read access on Users" in {
      an[IllegalAccessException] should be thrownBy permission.hasReadAccess(
        UserType(),
        User.guestUser
      )
      permission.hasReadAccess(UserType(), User.superUser)
      permission.hasReadAccess(UserType(), userDAL.retrieveById(1, None).get)
      an[IllegalAccessException] should be thrownBy permission.hasReadAccess(
        UserType(),
        userDAL.retrieveById(2, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasReadAccess(
        UserType(),
        userDAL.retrieveById(3, None).get
      )
    }

    // Everybody can read any tag
    "Read access on Tags" in {
      permission.hasReadAccess(TagType(), User.guestUser)
      permission.hasReadAccess(TagType(), userDAL.retrieveById(1, None).get)
      permission.hasReadAccess(TagType(), userDAL.retrieveById(2, None).get)
      permission.hasReadAccess(TagType(), userDAL.retrieveById(3, None).get)
    }

    // Everybody can read virtual challenges
    "Read access on Virtual Challenges" in {
      permission.hasReadAccess(VirtualChallengeType(), User.guestUser)
      permission.hasReadAccess(VirtualChallengeType(), userDAL.retrieveById(1, None).get)
      permission.hasReadAccess(VirtualChallengeType(), userDAL.retrieveById(2, None).get)
      permission.hasReadAccess(VirtualChallengeType(), userDAL.retrieveById(3, None).get)
      permission.hasReadAccess(VirtualChallengeType(), userDAL.retrieveById(100, None).get)
    }

    // Only superusers and admin's for the project can read groups, or owners of the project which should always be in the admin group
    "Read access on Groups" in {
      an[IllegalAccessException] should be thrownBy permission.hasReadAccess(
        GroupType(),
        User.guestUser
      )
      permission.hasReadAccess(GroupType(), User.superUser)
      permission.hasReadAccess(GroupType(), userDAL.retrieveById(1, None).get)
      an[IllegalAccessException] should be thrownBy permission.hasReadAccess(
        GroupType(),
        userDAL.retrieveById(2, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasReadAccess(
        GroupType(),
        userDAL.retrieveById(3, None).get
      )
      permission.hasReadAccess(GroupType(), userDAL.retrieveById(100, None).get)
    }

    // Users in Admin and Write groups should have write access to projects
    "Write access in Projects" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        ProjectType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        ProjectType(),
        userDAL.retrieveById(3, None).get
      )
      permission.hasWriteAccess(ProjectType(), User.superUser)
      permission.hasWriteAccess(ProjectType(), userDAL.retrieveById(2, None).get)
      permission.hasWriteAccess(ProjectType(), userDAL.retrieveById(1, None).get)
    }

    // Users in Admin and Write groups should have write access to challenges
    "Write access in Challenges" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        ChallengeType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        ChallengeType(),
        userDAL.retrieveById(3, None).get
      )
      permission.hasWriteAccess(ChallengeType(), userDAL.retrieveById(2, None).get)
      permission.hasWriteAccess(ChallengeType(), userDAL.retrieveById(1, None).get)
      permission.hasWriteAccess(ChallengeType(), userDAL.retrieveById(100, None).get)
      permission.hasWriteAccess(ChallengeType(), User.superUser)
    }

    // Users in Admin and Write groups should have write access to tasks
    "Write access in Tasks" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        TaskType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        TaskType(),
        userDAL.retrieveById(3L, None).get
      )
      permission.hasWriteAccess(TaskType(), userDAL.retrieveById(2L, None).get)
      permission.hasWriteAccess(TaskType(), userDAL.retrieveById(1L, None).get)
      permission.hasWriteAccess(TaskType(), userDAL.retrieveById(100L, None).get)
      permission.hasWriteAccess(TaskType(), User.superUser)
    }

    // Only super users and the actual user have
    "Write access in Users" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        UserType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        UserType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        UserType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasWriteAccess(UserType(), userDAL.retrieveById(1L, None).get)
      permission.hasWriteAccess(UserType(), User.superUser)
    }

    "Write access in Tags" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        TaskType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        TaskType(),
        userDAL.retrieveById(3L, None).get
      )
      permission.hasWriteAccess(TaskType(), userDAL.retrieveById(2L, None).get)
      permission.hasWriteAccess(TaskType(), userDAL.retrieveById(1L, None).get)
    }

    "Write access in Virtual Challenges" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        VirtualChallengeType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        VirtualChallengeType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        VirtualChallengeType(),
        userDAL.retrieveById(2L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        VirtualChallengeType(),
        userDAL.retrieveById(1L, None).get
      )
      permission.hasWriteAccess(VirtualChallengeType(), User.superUser)
      permission.hasWriteAccess(VirtualChallengeType(), userDAL.retrieveById(100L, None).get)
    }

    "Write access in Groups" in {
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        GroupType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        GroupType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasWriteAccess(
        GroupType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasWriteAccess(GroupType(), userDAL.retrieveById(1L, None).get)
      permission.hasWriteAccess(GroupType(), userDAL.retrieveById(100L, None).get)
      permission.hasWriteAccess(GroupType(), User.superUser)
    }

    "Admin access for Projects" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        ProjectType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        ProjectType(),
        userDAL.retrieveById(3L, None).get
      )
      permission.hasAdminAccess(ProjectType(), User.superUser)
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        ProjectType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasAdminAccess(ProjectType(), userDAL.retrieveById(1L, None).get)
    }

    "Admin access for Challenges" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        ChallengeType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        ChallengeType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        ChallengeType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasAdminAccess(ChallengeType(), userDAL.retrieveById(1L, None).get)
      permission.hasAdminAccess(ChallengeType(), userDAL.retrieveById(100L, None).get)
      permission.hasAdminAccess(ChallengeType(), User.superUser)
    }

    "Admin access for Tasks" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        TaskType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        TaskType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        TaskType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasAdminAccess(TaskType(), userDAL.retrieveById(1L, None).get)
      permission.hasAdminAccess(TaskType(), userDAL.retrieveById(100L, None).get)
      permission.hasAdminAccess(TaskType(), User.superUser)
    }

    "Admin access for Users" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        UserType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        UserType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        UserType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasAdminAccess(UserType(), userDAL.retrieveById(1L, None).get)
      permission.hasAdminAccess(UserType(), User.superUser)
    }

    "Admin access for Tags" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        TaskType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        TaskType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        TaskType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasAdminAccess(TaskType(), userDAL.retrieveById(1L, None).get)
    }

    "Admin access for Virtual Challenges" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        VirtualChallengeType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        VirtualChallengeType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        VirtualChallengeType(),
        userDAL.retrieveById(2L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        VirtualChallengeType(),
        userDAL.retrieveById(1L, None).get
      )
      permission.hasAdminAccess(VirtualChallengeType(), User.superUser)
      permission.hasAdminAccess(VirtualChallengeType(), userDAL.retrieveById(100L, None).get)
    }

    "Admin access for Groups" in {
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        GroupType(),
        User.guestUser
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        GroupType(),
        userDAL.retrieveById(3L, None).get
      )
      an[IllegalAccessException] should be thrownBy permission.hasAdminAccess(
        GroupType(),
        userDAL.retrieveById(2L, None).get
      )
      permission.hasAdminAccess(GroupType(), userDAL.retrieveById(1L, None).get)
      permission.hasAdminAccess(GroupType(), userDAL.retrieveById(100L, None).get)
      permission.hasAdminAccess(GroupType(), User.superUser)
    }
  }
}
