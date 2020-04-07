/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.framework.model.User
import org.maproulette.framework.repository.UserSavedObjectsRepository
import org.maproulette.framework.util.{FrameworkHelper, UserSavedObjectsTag}
import play.api.Application

/**
  * @author mcuthbert
  */
class UserSavedObjectsSpec(implicit val application: Application) extends FrameworkHelper {
  val repository: UserSavedObjectsRepository =
    this.application.injector.instanceOf(classOf[UserSavedObjectsRepository])

  "UserSavedObjectsRepository" should {
    "save challenge(s) to user profile" taggedAs (UserSavedObjectsTag) in {
      this.repository.saveChallenge(this.defaultUser.id, this.defaultChallenge.id)
      val savedChallenges = this.repository.getSavedChallenges(this.defaultUser.id)
      savedChallenges.size mustEqual 1
      savedChallenges.head.id mustEqual this.defaultChallenge.id

      this.repository.unsaveChallenge(this.defaultUser.id, this.defaultChallenge.id)
      val savedChallenges2 = this.repository.getSavedChallenges(this.defaultUser.id)
      savedChallenges2.isEmpty mustEqual true
    }

    "save task(s) to user profile" taggedAs (UserSavedObjectsTag) in {
      this.repository.saveTask(this.defaultUser.id, this.defaultTask.id, this.defaultTask.parent)
      val savedTasks = this.repository.getSavedTasks(this.defaultUser.id)
      savedTasks.size mustEqual 1
      savedTasks.head.id mustEqual this.defaultTask.id

      this.repository.unsaveTask(this.defaultUser.id, this.defaultTask.id)
      val savedTasks2 = this.repository.getSavedTasks(this.defaultUser.id)
      savedTasks2.isEmpty mustEqual true
    }

    "save task(s) to user profile for specific challenges" taggedAs (UserSavedObjectsTag) in {
      this.repository.saveTask(this.defaultUser.id, this.defaultTask.id, this.defaultTask.parent)
      // create a new challenge and task
      val challenge =
        this.challengeDAL.insert(this.getTestChallenge("DUMMY_CHALLENGE_2"), User.superUser)
      val task = this.taskDAL
        .insert(this.getTestTask("DUMMY_TASK_2").copy(parent = challenge.id), User.superUser)
      this.repository.saveTask(this.defaultUser.id, task.id, task.parent)

      val allTasks = this.repository.getSavedTasks(this.defaultUser.id)
      allTasks.size mustEqual 2

      val challenge1Tasks =
        this.repository.getSavedTasks(this.defaultUser.id, List(this.defaultChallenge.id))
      challenge1Tasks.size mustEqual 1
      challenge1Tasks.head.id mustEqual this.defaultTask.id
      val challenge2Tasks = this.repository.getSavedTasks(this.defaultUser.id, List(challenge.id))
      challenge2Tasks.size mustEqual 1
      challenge2Tasks.head.id mustEqual task.id

      // clean up - if this doesn't clean up correctly tests in the "UserService" section will fail
      this.repository.unsaveTask(this.defaultUser.id, this.defaultTask.id)
      this.repository.unsaveTask(this.defaultUser.id, task.id)
    }
  }

  "UserService" should {
    "save challenge(s) in user profile" taggedAs (UserSavedObjectsTag) in {
      this.serviceManager.user
        .saveChallenge(this.defaultUser.id, this.defaultChallenge.id, User.superUser)
      val challenges =
        this.serviceManager.user.getSavedChallenges(this.defaultUser.id, User.superUser)
      challenges.size mustEqual 1
      challenges.head.id mustEqual this.defaultChallenge.id

      this.serviceManager.user
        .unsaveChallenge(this.defaultUser.id, this.defaultChallenge.id, User.superUser)
      val challenges2 =
        this.serviceManager.user.getSavedChallenges(this.defaultUser.id, User.superUser)
      challenges2.size mustEqual 0
    }

    "save task(s) in user profile" taggedAs (UserSavedObjectsTag) in {
      this.serviceManager.user.saveTask(this.defaultUser.id, this.defaultTask.id, User.superUser)
      val tasks = this.serviceManager.user.getSavedTasks(this.defaultUser.id, User.superUser)
      tasks.size mustEqual 1
      tasks.head.id mustEqual this.defaultTask.id

      this.serviceManager.user.unsaveTask(this.defaultUser.id, this.defaultTask.id, User.superUser)
      val tasks2 = this.serviceManager.user.getSavedTasks(this.defaultUser.id, User.superUser)
      tasks2.size mustEqual 0
    }

    "save task(s) to user profile for specific challenges" taggedAs (UserSavedObjectsTag) in {
      // create a new challenge and task
      val challenge =
        this.challengeDAL.insert(this.getTestChallenge("DUMMY_CHALLENGE_3"), User.superUser)
      val task = this.taskDAL
        .insert(this.getTestTask("DUMMY_TASK_3").copy(parent = challenge.id), User.superUser)
      this.serviceManager.user.saveTask(this.defaultUser.id, this.defaultTask.id, User.superUser)
      this.serviceManager.user.saveTask(this.defaultUser.id, task.id, User.superUser)

      val tasks = this.serviceManager.user.getSavedTasks(this.defaultUser.id, User.superUser)
      tasks.size mustEqual 2

      val challenge1Tasks = this.serviceManager.user
        .getSavedTasks(this.defaultUser.id, User.superUser, List(this.defaultChallenge.id))
      challenge1Tasks.size mustEqual 1
      challenge1Tasks.head.id mustEqual this.defaultTask.id

      val challenge2Tasks =
        this.serviceManager.user
          .getSavedTasks(this.defaultUser.id, User.superUser, List(challenge.id))
      challenge2Tasks.size mustEqual 1
      challenge2Tasks.head.id mustEqual task.id
    }
  }

  override implicit val projectTestName: String = "UserSavedObjectsSpecProject"
}
