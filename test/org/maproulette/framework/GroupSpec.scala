/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.framework.model.{Group, Project, User}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.{GroupRepository, UserGroupRepository}
import org.maproulette.framework.service.GroupService
import org.maproulette.framework.util.{FrameworkHelper, GroupTag, UserGroupTag}
import play.api.Application

/**
  * @author mcuthbert
  */
class GroupSpec(implicit val application: Application) extends FrameworkHelper {
  val repository: GroupRepository = this.application.injector.instanceOf(classOf[GroupRepository])
  val userGroupRepository: UserGroupRepository =
    this.application.injector.instanceOf(classOf[UserGroupRepository])
  val service: GroupService = this.serviceManager.group

  var defaultGroup: Group = null
  var randomUser: User    = null

  "GroupRepository" should {
    "perform a basic query" taggedAs (GroupTag) in {
      val groups = this.repository
        .query(Query.simple(List(BaseParameter(Group.FIELD_ID, this.defaultGroup.id))))
      groups.size mustEqual 1
      groups.head.id mustEqual this.defaultGroup.id
    }

    "retrieve a group" taggedAs (GroupTag) in {
      val group = this.repository.retrieve(this.defaultGroup.id)
      group.get mustEqual this.defaultGroup
      val nonGroup = this.repository.retrieve(2345L)
      nonGroup.isEmpty mustEqual true
    }

    "create a group" taggedAs (GroupTag) in {
      val createdGroup = this.repository
        .create(Group(-1, "RandomCreateGroup", this.defaultProject.id, Group.TYPE_ADMIN))
      val retrievedGroup = this.repository.retrieve(createdGroup.id)
      retrievedGroup.get mustEqual createdGroup
    }

    "update a group" taggedAs (GroupTag) in {
      val randomGroup = this.repository
        .create(Group(-1, "RandomGroup", this.defaultProject.id, Group.TYPE_WRITE_ACCESS))
      // the project_id and type fields should be ignored, as you can only update the name
      this.repository.update(Group(randomGroup.id, "UpdatedName", 1234, Group.TYPE_READ_ONLY))
      val group = this.service.retrieve(randomGroup.id)
      group.get.name mustEqual "UpdatedName"
      group.get.projectId mustEqual randomGroup.projectId
      group.get.groupType mustEqual randomGroup.groupType
    }

    "delete a group" taggedAs (GroupTag) in {
      val group =
        this.repository.create(Group(-1, "RANDOM_GROUP", this.defaultProject.id, Group.TYPE_ADMIN))
      this.repository.delete(group.id)
    }
  }

  "UserGroupRepository" should {
    "handle user groups based on OSM id" taggedAs (UserGroupTag) in {
      this.userGroupRepository.addUserToGroup(this.randomUser.osmProfile.id, defaultGroup.id)
      val groups = this.userGroupRepository.get(this.randomUser.osmProfile.id)
      groups.size mustEqual 1
      groups.head mustEqual defaultGroup

      this.userGroupRepository.removeUserFromGroup(this.randomUser.osmProfile.id, defaultGroup.id)
      val groups2 = this.userGroupRepository.get(this.randomUser.osmProfile.id)
      groups2.size mustEqual 0
    }

    "handle user project groups based on OSM id" taggedAs (UserGroupTag) in {
      val project = this.serviceManager.project
        .create(Project(-1, User.superUser.id, "GroupTestingProject"), User.superUser)
      val projectReadGroup = project.groups.filter(_.groupType == Group.TYPE_READ_ONLY).head
      // add the user all all the project groups
      this.userGroupRepository
        .addUserToProject(this.randomUser.osmProfile.id, Group.TYPE_READ_ONLY, project.id)
      val groups = this.userGroupRepository.get(this.randomUser.osmProfile.id)
      groups.size mustEqual 1
      groups.head mustEqual projectReadGroup

      // don't remove user from a group it doesn't exist in
      this.userGroupRepository.removeUserFromProjectGroups(
        this.randomUser.osmProfile.id,
        project.id,
        Group.TYPE_WRITE_ACCESS
      )
      val groups2 = this.userGroupRepository.get(this.randomUser.osmProfile.id)
      groups2.size mustEqual 1

      this.userGroupRepository.removeUserFromProjectGroups(
        this.randomUser.osmProfile.id,
        project.id,
        Group.TYPE_READ_ONLY
      )
      val groups3 = this.userGroupRepository.get(this.randomUser.osmProfile.id)
      groups3.size mustEqual 0
    }
  }

  "GroupService" should {
    "not allow non super user to create groups" taggedAs (GroupTag) in {
      intercept[IllegalAccessException] {
        this.service.create(1, 1, User.guestUser)
      }
    }

    "not allow non super user to update groups" taggedAs (GroupTag) in {
      intercept[IllegalAccessException] {
        this.service.update(1, "NewName", User.guestUser)
      }
    }

    "not allow non super user to delete groups" taggedAs (GroupTag) in {
      intercept[IllegalAccessException] {
        this.service.delete(1, User.guestUser)
      }
    }

    "not allow non super user to retrieve user groups" taggedAs (GroupTag) in {
      intercept[IllegalAccessException] {
        this.service.retrieveUserGroups(1, User.guestUser)
      }
    }

    "not allow non super user to retrieve project groups" taggedAs (GroupTag) in {
      intercept[IllegalAccessException] {
        this.service.retrieveProjectGroups(1, User.guestUser)
      }
    }

    "create a new group" taggedAs (GroupTag) in {
      val group = this.service.create(this.defaultProject.id, Group.TYPE_ADMIN, User.superUser)
      group.projectId mustEqual this.defaultProject.id
      group.groupType mustEqual Group.TYPE_ADMIN
      group.name mustEqual s"${this.defaultProject.id}_Admin"
      val retrievedGroup = this.service.retrieve(group.id)
      retrievedGroup.get mustEqual group
    }

    "update a group" taggedAs (GroupTag) in {
      val group = this.service.create(this.defaultProject.id, Group.TYPE_READ_ONLY, User.superUser)
      this.service.update(group.id, "UpdatedNewNameForGroup", User.superUser)
      val retrievedGroup = this.service.retrieve(group.id)
      retrievedGroup.get.id mustEqual group.id
      retrievedGroup.get.name mustEqual "UpdatedNewNameForGroup"
    }

    "handle user groups for OSM user" taggedAs (GroupTag) in {
      this.service.addUserToGroup(this.randomUser.osmProfile.id, defaultGroup, User.superUser)
      val groups = this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups.size mustEqual 1
      groups.head mustEqual defaultGroup

      this.service.removeUserFromGroup(this.randomUser.osmProfile.id, defaultGroup, User.superUser)
      val groups2 = this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups2.size mustEqual 0
    }

    "handle user project groups based on OSM id" taggedAs (GroupTag) in {
      val project = this.serviceManager.project
        .create(Project(-1, User.superUser.id, "GroupTestingProject2"), User.superUser)
      val projectReadGroup = project.groups.filter(_.groupType == Group.TYPE_READ_ONLY).head
      // add the user all all the project groups
      this.service
        .addUserToProject(
          this.randomUser.osmProfile.id,
          Group.TYPE_READ_ONLY,
          project.id,
          User.superUser
        )
      val groups = this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups.size mustEqual 1
      groups.head mustEqual projectReadGroup

      // don't remove user from a group it doesn't exist in
      this.service.removeUserFromProjectGroups(
        this.randomUser.osmProfile.id,
        project.id,
        Group.TYPE_WRITE_ACCESS,
        User.superUser
      )
      val groups2 =
        this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups2.size mustEqual 1

      this.service.removeUserFromProjectGroups(
        this.randomUser.osmProfile.id,
        project.id,
        Group.TYPE_READ_ONLY,
        User.superUser
      )
      val groups3 =
        this.service.retrieveUserGroups(this.randomUser.osmProfile.id, User.superUser)
      groups3.size mustEqual 0
    }

    "retrieve project groups" taggedAs (GroupTag) in {
      val project = this.serviceManager.project
        .create(Project(-1, User.superUser.id, "GroupTestingProject3"), User.superUser)
      val projectGroups = this.service.retrieveProjectGroups(project.id, User.superUser)
      projectGroups.size mustEqual 3
      this.service.create(project.id, Group.TYPE_ADMIN, User.superUser, "NewAdminGroup")
      val groups = this.service.retrieveProjectGroups(project.id, User.superUser)
      groups.size mustEqual 4
    }
  }

  override implicit val projectTestName: String = "GroupSpecProject"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    defaultGroup = this.service.create(this.defaultProject.id, Group.TYPE_ADMIN, User.superUser)
    randomUser = this.serviceManager.user.create(
      this.getTestUser(12345, "RandomOUser"),
      User.superUser
    )
  }
}
