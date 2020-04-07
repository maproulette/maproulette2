/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import java.util.UUID

import org.maproulette.framework.model._
import org.maproulette.framework.psql.filter.{BaseParameter, Operator}
import org.maproulette.framework.psql.{Paging, Query}
import org.maproulette.framework.repository.UserRepository
import org.maproulette.framework.service.UserService
import org.maproulette.framework.util.{FrameworkHelper, UserTag}
import org.scalatest.Matchers._
import play.api.Application
import play.api.libs.oauth.RequestToken

/**
  * @author mcuthbert
  */
class UserSpec(implicit val application: Application) extends FrameworkHelper {
  val userRepository: UserRepository = this.application.injector.instanceOf(classOf[UserRepository])
  val userService: UserService       = this.serviceManager.user

  "UserRepository" should {
    "upsert user" taggedAs (UserTag) in {
      val insertedUser  = this.insertBaseUser(1, "name1")
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.get mustEqual insertedUser
    }

    "update user" taggedAs (UserTag) in {
      val insertedUser  = this.insertBaseUser(2, "name2")
      val updatedApiKey = UUID.randomUUID().toString
      val updateUser = insertedUser.copy(
        osmProfile = insertedUser.osmProfile.copy(
          displayName = "name3",
          avatarURL = "UPDATE_avatarURL",
          requestToken = RequestToken("UPDATED_TOKEN", "UPDATED_SECRET")
        ),
        apiKey = Some(updatedApiKey),
        settings = UserSettings(
          Some(1),
          Some(2),
          Some("id"),
          Some("basemap"),
          Some("en-US"),
          Some("email_address"),
          Some(true),
          Some(false),
          Some(5),
          Some(true)
        )
      )
      this.userRepository.update(updateUser, "POINT (14.0 22.0)")
      val updatedUser = this.repositoryGet(insertedUser.id).get
      updatedUser.osmProfile.displayName mustEqual updateUser.osmProfile.displayName
      updatedUser.osmProfile.avatarURL mustEqual updateUser.osmProfile.avatarURL
      updatedUser.osmProfile.requestToken mustEqual updateUser.osmProfile.requestToken
      // API Key should not be allowed to updated here
      updatedUser.apiKey mustEqual insertedUser.apiKey
      updatedUser.settings mustEqual updateUser.settings
    }

    "update API key" taggedAs (UserTag) in {
      val insertedUser =
        this.userRepository.upsert(this.getTestUser(3, "APITest"), "TestAPIKey", "POINT (20 40)")
      this.userRepository.updateAPIKey(insertedUser.id, "NEW_updated_key")
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.get.apiKey.get mustEqual "NEW_updated_key"
    }

    "delete user" taggedAs (UserTag) in {
      val insertedUser = this.userRepository
        .upsert(this.getTestUser(4, "DeleteTest"), "TestAPIKey", "POINT (20 40)")
      this.userRepository.delete(insertedUser.id)
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.isEmpty mustEqual true
    }

    "delete user by OSMID" taggedAs (UserTag) in {
      val insertedUser = this.userRepository
        .upsert(this.getTestUser(5, "DeleteByOidTest"), "TestAPIKey", "POINT (20 40)")
      this.userRepository.deleteByOSMID(5)
      val retrievedUser = this.repositoryGet(insertedUser.id)
      retrievedUser.isEmpty mustEqual true
    }

    "update user score" taggedAs (UserTag) in {
      val insertedUser = this.userRepository
        .upsert(this.getTestUser(61, "UpdateUserO"), "TestAPIKey", "POINT (20 40)")
      val updatedUser = this.userRepository.updateUserScore(
        insertedUser.id,
        List(
          BaseParameter(
            UserMetrics.FIELD_SCORE,
            s"=(${UserMetrics.FIELD_SCORE}+1000)",
            Operator.CUSTOM
          ),
          BaseParameter(
            UserMetrics.FIELD_TOTAL_REJECTED,
            s"=(${UserMetrics.FIELD_TOTAL_REJECTED}+1)",
            Operator.CUSTOM
          )
        )
      )
    }

    "get users managing projects" taggedAs (UserTag) in {
      val managers = this.userRepository.getUsersManagingProject(this.defaultProject.id)
      managers.size mustEqual 1
      managers.head.osmId mustEqual this.defaultUser.osmProfile.id
      val managers2 =
        this.userRepository.getUsersManagingProject(this.defaultProject.id, Some(List.empty))
      managers2.size mustEqual 1
      managers2.head.osmId mustEqual this.defaultUser.osmProfile.id
      val managers3 = this.userRepository
        .getUsersManagingProject(this.defaultProject.id, Some(List(this.defaultUser.id)))
      managers3.size mustEqual 0
      val managers4 = this.userRepository
        .getUsersManagingProject(this.defaultProject.id, Some(List(this.defaultUser.osmProfile.id)))
      managers4.size mustEqual 1
      managers4.head.osmId mustEqual this.defaultUser.osmProfile.id
    }
  }

  "UserService" should {
    "not allow retrieval of user by API key if not actual user" taggedAs (UserTag) in {
      val firstUser =
        this.userService.create(this.getTestUser(6, "FailedUserTest"), User.superUser)
      val secondUser =
        this.userService.create(this.getTestUser(7, "FailedUserTest2"), User.superUser)
      intercept[IllegalAccessException] {
        this.userService.retrieveByAPIKey(firstUser.id, firstUser.apiKey.get, secondUser)
      }
    }

    "retrieve Users" taggedAs (UserTag) in {
      val insertedUser =
        this.userService.create(this.getTestUser(8, "InsertUserServiceTest"), User.superUser)
      // get the user by their API Key and id
      val retrievedUser1 =
        this.userService.retrieveByAPIKey(insertedUser.id, insertedUser.apiKey.get, User.superUser)
      retrievedUser1.get mustEqual insertedUser
      // get the user by their API Key and OSM user id
      val retrievedUser2 = this.userService
        .retrieveByAPIKey(insertedUser.osmProfile.id, insertedUser.apiKey.get, User.superUser)
      retrievedUser2.get mustEqual insertedUser
      // get the user by their username and API Key
      val retrievedUser3 = this.userService
        .retrieveByUsernameAndAPIKey(insertedUser.osmProfile.displayName, insertedUser.apiKey.get)
      retrievedUser3.get mustEqual insertedUser
      // get the user by their OSM username
      val retrievedUser4 =
        this.userService.retrieveByOSMUsername(insertedUser.osmProfile.displayName, User.superUser)
      retrievedUser4.get mustEqual insertedUser
      // get the user based off of the requestToken
      val retrievedUser5 = this.userService.matchByRequestToken(
        insertedUser.id,
        insertedUser.osmProfile.requestToken,
        User.superUser
      )
      retrievedUser5.get mustEqual insertedUser
      // get the user simply based off their id
      val retrievedUser6 = this.userService.retrieve(insertedUser.id)
      retrievedUser6.get mustEqual insertedUser
      // get the user by their OSM user id
      val retrievedUser7 = this.userService.retrieveByOSMId(insertedUser.osmProfile.id)
      retrievedUser7
    }

    "not allow retrieval of user by OSM username if not super user" taggedAs (UserTag) in {
      val firstUser =
        this.userService.create(this.getTestUser(9, "FailedORetrievalUser1"), User.superUser)
      val secondUser =
        this.userService.create(this.getTestUser(10, "FailedORetrievalUser2"), User.superUser)
      intercept[IllegalAccessException] {
        this.userService.retrieveByOSMUsername(firstUser.osmProfile.displayName, secondUser)
      }
    }

    "generate new API key" taggedAs (UserTag) in {
      val insertedUser =
        this.userService.create(this.getTestUser(11, "APIGenerationTestUser"), User.superUser)
      val newAPIUser = this.userService.generateAPIKey(insertedUser, User.superUser)
      newAPIUser.get.apiKey must not be insertedUser.apiKey
    }

    "retrieve list of users" taggedAs (UserTag) in {
      val user1 =
        this.userService.create(this.getTestUser(12, "ListRetrievalTestUser1"), User.superUser)
      val user2 =
        this.userService.create(this.getTestUser(13, "ListRetrievalTestUser2"), User.superUser)
      val userList = this.userService.retrieveListById(List(user1.id, user2.id))
      userList.size mustEqual 2
      List(user1, user2).contains(userList.head) mustEqual true
      List(user1, user2).contains(userList(1)) mustEqual true
    }

    "delete user" taggedAs (UserTag) in {
      val user =
        this.userService.create(this.getTestUser(14, "DeleteTest"), User.superUser)
      this.userService.delete(user.id, User.superUser)
      this.repositoryGet(user.id).isEmpty mustEqual true
    }

    "delete user by OSM id" taggedAs (UserTag) in {
      val user =
        this.userService.create(this.getTestUser(15, "DeleteByOidTest"), User.superUser)
      this.userService.deleteByOsmID(user.osmProfile.id, User.superUser)
      this.repositoryGet(user.id).isEmpty mustEqual true
    }

    "Search by OSM Username" taggedAs (UserTag) in {
      val user1 =
        this.userService.create(this.getTestUser(16, "OSMUsernameSearch1"), User.superUser)
      val user2 =
        this.userService.create(this.getTestUser(17, "OSMUsernameSearch2"), User.superUser)
      val user3 =
        this.userService.create(this.getTestUser(18, "OSMUsernameNotRelated"), User.superUser)

      val searchResultUser1 = UserSearchResult(
        user1.osmProfile.id,
        user1.osmProfile.displayName,
        user1.osmProfile.avatarURL
      )
      val searchResultUser2 = UserSearchResult(
        user2.osmProfile.id,
        user2.osmProfile.displayName,
        user2.osmProfile.avatarURL
      )
      val searchResultUser3 = UserSearchResult(
        user3.osmProfile.id,
        user3.osmProfile.displayName,
        user3.osmProfile.avatarURL
      )

      val users1 = this.userService.searchByOSMUsername("Search")
      users1.size mustEqual 2
      List(searchResultUser1, searchResultUser2).contains(users1.head.toSearchResult) mustEqual true
      List(searchResultUser1, searchResultUser2).contains(users1(1).toSearchResult) mustEqual true

      val users2 = this.userService.searchByOSMUsername("Not")
      users2.size mustEqual 1
      users2.head.toSearchResult mustEqual searchResultUser3

      val users3 = this.userService.searchByOSMUsername("NONE")
      users3.size mustEqual 0

      val searchResultsList = List(searchResultUser1, searchResultUser2, searchResultUser3)
      val users4            = this.userService.searchByOSMUsername("OSM", Paging(1))
      users4.size mustEqual 1
      val firstUser = users4.head.toSearchResult
      searchResultsList.contains(firstUser) mustEqual true

      val users5 = this.userService.searchByOSMUsername("OSM", Paging(1, 1))
      users5.size mustEqual 1
      val secondUser = users5.head.toSearchResult
      searchResultsList.contains(secondUser) mustEqual true
      secondUser should not be firstUser
      val users6 = this.userService.searchByOSMUsername("OSM", Paging(1, 2))
      users6.size mustEqual 1
      val thirdUser = users6.head.toSearchResult
      searchResultsList.contains(thirdUser) mustEqual true
      thirdUser should not be firstUser
      thirdUser should not be secondUser
    }

    "get users managing projects" taggedAs (UserTag) in {
      val managers =
        this.userService.getUsersManagingProject(this.defaultProject.id, user = User.superUser)
      managers.size mustEqual 1
      managers.head.osmId mustEqual this.defaultUser.osmProfile.id
      val managers2 =
        this.userService
          .getUsersManagingProject(this.defaultProject.id, Some(List.empty), User.superUser)
      managers2.size mustEqual 1
      managers2.head.osmId mustEqual this.defaultUser.osmProfile.id
      val managers3 = this.userService
        .getUsersManagingProject(
          this.defaultProject.id,
          Some(List(this.defaultUser.id)),
          User.superUser
        )
      managers3.size mustEqual 0
      val managers4 = this.userService
        .getUsersManagingProject(
          this.defaultProject.id,
          Some(List(this.defaultUser.osmProfile.id)),
          User.superUser
        )
      managers4.size mustEqual 1
      managers4.head.osmId mustEqual this.defaultUser.osmProfile.id
    }
  }

  override implicit val projectTestName: String = "UserSpecProject"

  private def repositoryGet(id: Long): Option[User] = {
    this.userRepository
      .query(
        Query.simple(
          List(BaseParameter(User.FIELD_ID, id))
        )
      )
      .headOption
  }

  private def insertBaseUser(osmId: Long, osmName: String): User =
    this.userService.create(this.getTestUser(osmId, osmName), User.superUser)
}
