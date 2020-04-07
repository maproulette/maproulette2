/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.framework.model.{Comment, User}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseParameter, Operator}
import org.maproulette.framework.repository.CommentRepository
import org.maproulette.framework.service.CommentService
import org.maproulette.framework.util.{CommentTag, FrameworkHelper}
import play.api.Application

/**
  * @author mcuthbert
  */
class CommentSpec(implicit val application: Application) extends FrameworkHelper {

  val commentRepository: CommentRepository =
    this.application.injector.instanceOf(classOf[CommentRepository])
  val commentService: CommentService = this.application.injector.instanceOf(classOf[CommentService])

  "CommentRepository" should {
    "add comment into database" taggedAs (CommentTag) in {
      val comment =
        this.commentRepository.create(
          User.superUser,
          defaultTask.id,
          "Adding comment into database",
          None
        )

      comment.osm_id mustEqual User.superUser.osmProfile.id
      comment.taskId mustEqual defaultTask.id
      comment.challengeId mustEqual defaultChallenge.id
      comment.projectId mustEqual defaultProject.id
      comment.actionId.isEmpty mustEqual true

      val retrievedComment = this.repositoryGet(comment.id).get
      comment mustEqual retrievedComment
    }

    "update comment in the database" taggedAs (CommentTag) in {
      val comment = this.commentRepository.create(
        User.superUser,
        defaultTask.id,
        "update comment in the database",
        None
      )
      val retrievedComment = this.repositoryGet(comment.id).get
      retrievedComment.comment mustEqual "update comment in the database"
      this.commentRepository.update(retrievedComment.id, "Test Update")
      val updatedComment = this.commentService.retrieve(comment.id).get
      updatedComment.comment mustEqual "Test Update"
    }

    "delete a comment in the database" taggedAs (CommentTag) in {
      val comment = this.commentRepository.create(
        User.superUser,
        defaultTask.id,
        "delete a comment in the database",
        None
      )
      val retrievedComment = this.repositoryGet(comment.id)
      retrievedComment.isDefined mustEqual true
      this.commentRepository.delete(retrievedComment.get.id)
      val deletedComment = this.repositoryGet(comment.id)
      deletedComment.isEmpty mustEqual true
    }

    "find a specific comment" taggedAs (CommentTag) in {
      this.commentRepository.create(User.superUser, defaultTask.id, "find a specific comment", None)
      val comment =
        this.commentRepository.create(
          User.superUser,
          defaultTask.id,
          "find a specific comment 2",
          None
        )
      val comments = this.commentRepository
        .query(Query.simple(List(BaseParameter("comment", "%2", Operator.LIKE))))
      comments.size mustEqual 1
      comments.head.id mustEqual comment.id
      comments.head.comment mustEqual "find a specific comment 2"
    }
  }

  "CommentService" should {
    "add comment into database" taggedAs (CommentTag) in {
      val comment          = this.commentService.create(User.superUser, defaultTask.id, "GP Add", None)
      val retrievedComment = this.commentService.retrieve(comment.id)
      retrievedComment.get mustEqual comment
    }

    "update a comment in the database" taggedAs (CommentTag) in {
      val comment = this.commentService.create(User.superUser, defaultTask.id, "GP update", None)
      this.commentService.update(comment.id, "GP update Test", User.superUser)
      val retrievedComment = this.commentService.retrieve(comment.id)
      retrievedComment.get.comment mustEqual "GP update Test"
    }

    "delete a comment in the database" taggedAs (CommentTag) in {
      val comment = this.commentService.create(User.superUser, defaultTask.id, "GP delete", None)
      this.commentService.delete(comment.id, comment.taskId, User.superUser)
      this.commentService.retrieve(comment.id).isEmpty mustEqual true
    }

    "Fail on trying to delete a comment with no associated task" taggedAs (CommentTag) in {
      intercept[NotFoundException] {
        val comment =
          this.commentService.create(User.superUser, defaultTask.id, "GP delete attempt", None)
        this.commentService.delete(comment.id, 12355, User.superUser)
      }
    }

    "Fail on trying to update a comment that doesn't exist" taggedAs (CommentTag) in {
      intercept[NotFoundException] {
        this.commentService.update(894, "UpdateTest", User.superUser)
      }
    }

    "Fail on update when empty string provided" taggedAs (CommentTag) in {
      intercept[InvalidException] {
        this.commentService.update(-1, "", User.superUser)
      }
    }

    "Fail on update when null string provided" taggedAs (CommentTag) in {
      intercept[InvalidException] {
        this.commentService.update(-1, null, User.superUser)
      }
    }

    "Only super user or original user can update comment" taggedAs (CommentTag) in {
      intercept[IllegalAccessException] {
        val comment =
          this.commentService.create(User.superUser, defaultTask.id, "Default comment", None)
        this.commentService.update(comment.id, "New Commet", User.guestUser)
      }
    }

    "Find comments for a specific project, challenge and task" taggedAs (CommentTag) in {
      val comment =
        this.commentService.create(User.superUser, defaultTask.id, "Default Comment", None)
      val projectComments =
        this.commentService.find(List(comment.projectId), List.empty, List.empty)
      projectComments.head mustEqual comment
      val challengeComments =
        this.commentService.find(List.empty, List(comment.challengeId), List.empty)
      challengeComments.head mustEqual comment
      val taskComments = this.commentService.find(List.empty, List.empty, List(comment.taskId))
      taskComments.head mustEqual comment
      val all = this.commentService
        .find(List(comment.projectId), List(comment.challengeId), List(comment.taskId))
      all.head mustEqual comment
    }
  }

  override implicit val projectTestName: String = "CommentSpecProject"

  private def repositoryGet(id: Long): Option[Comment] = {
    this.commentRepository
      .query(
        Query.simple(
          List(BaseParameter(s"task_comments.${Comment.FIELD_ID}", id))
        )
      )
      .headOption
  }
}
