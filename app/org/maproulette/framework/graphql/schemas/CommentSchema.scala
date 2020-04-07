/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.graphql.schemas

import javax.inject.Inject
import org.maproulette.framework.graphql.UserContext
import org.maproulette.framework.model.Comment
import org.maproulette.framework.psql.Paging
import org.maproulette.framework.service.CommentService
import sangria.schema._

/**
  * @author mcuthbert
  */
class CommentSchema @Inject() (override val service: CommentService)
    extends MRSchema[Comment]
    with MRSchemaTypes {
  val queries: List[Field[UserContext, Unit]] = List(
    Field(
      name = "comment",
      description = Some("Retrieves a single comment based on the provided id"),
      fieldType = OptionType(CommentType),
      arguments = MRSchema.idArg :: Nil,
      resolve = context => this.service.retrieve(context.arg(MRSchema.idArg))
    ),
    Field(
      name = "comments",
      description =
        Some("Lists comments based on the project, challenge and task filters provided."),
      fieldType = ListType(CommentType),
      arguments = CommentSchema.findCommentArguments,
      resolve = context =>
        this.service.find(
          context.arg(CommentSchema.projectIdsArg).getOrElse(List.empty).toList,
          context.arg(CommentSchema.challengeIdsArg).getOrElse(List.empty).toList,
          context.arg(CommentSchema.taskIdsArg).getOrElse(List.empty).toList,
          Paging()
        )
    )
  )

  val mutations: List[Field[UserContext, Unit]] = List(
    Field(
      name = "createComment",
      description = Some("Creates a new comment for a task"),
      fieldType = CommentType,
      arguments = CommentSchema.taskIdArg :: CommentSchema.commentArg :: CommentSchema.actionIdArg :: Nil,
      resolve = context =>
        this.service.create(
          context.ctx.user,
          context.arg(CommentSchema.taskIdArg),
          context.arg(CommentSchema.commentArg),
          context.arg(CommentSchema.actionIdArg)
        )
    ),
    Field(
      name = "updateComment",
      description = Some("Updates a comment"),
      fieldType = CommentType,
      arguments = MRSchema.idArg :: CommentSchema.commentArg :: Nil,
      resolve = context =>
        this.service.update(
          context.arg(MRSchema.idArg),
          context.arg(CommentSchema.commentArg),
          context.ctx.user
        )
    ),
    Field(
      name = "deleteComment",
      description = Some("Deletes a comment"),
      fieldType = BooleanType,
      arguments = MRSchema.idArg :: CommentSchema.taskIdArg :: Nil,
      resolve = context =>
        this.service.delete(
          context.arg(MRSchema.idArg),
          context.arg(CommentSchema.taskIdArg),
          context.ctx.user
        )
    ),
    Field(
      name = "addToBundle",
      description = Some("Adds a comment to a Task Bundle"),
      fieldType = TaskBundleType,
      arguments = CommentSchema.bundleIdArg :: CommentSchema.commentArg :: CommentSchema.actionIdArg :: Nil,
      resolve = context =>
        this.service.addToBundle(
          context.ctx.user,
          context.arg(CommentSchema.bundleIdArg),
          context.arg(CommentSchema.commentArg),
          context.arg(CommentSchema.actionIdArg)
        )
    )
  )
}

object CommentSchema {
  val projectIdsArg: Argument[Option[Seq[Long]]] =
    Argument(
      "projectIds",
      OptionInputType(ListInputType(LongType)),
      "A list of projects identifiers"
    )
  val challengeIdsArg: Argument[Option[Seq[Long]]] =
    Argument(
      "challengeIds",
      OptionInputType(ListInputType(LongType)),
      "A list of challenge identifiers"
    )
  val taskIdsArg: Argument[Option[Seq[Long]]] =
    Argument("taskIds", OptionInputType(ListInputType(LongType)), "A list of task identifiers")
  val taskIdArg: Argument[Long] = Argument("taskId", LongType, "A task identifier")
  val commentArg: Argument[String] =
    Argument("comment", StringType, "The actual comment attached to a Task or other object")
  val actionIdArg: Argument[Option[Long]] = Argument(
    "actionId",
    OptionInputType(LongType),
    "An optional actionId that can be supplied when adding a comment to a task bundle"
  )
  val bundleIdArg: Argument[Long] = Argument(
    "bundleId",
    LongType,
    "The identifier for the Task Bundle that you are adding a comment too"
  )

  val findCommentArguments =
    MRSchema.pagingLimitArg ::
      MRSchema.pagingOffsetArg ::
      CommentSchema.projectIdsArg ::
      CommentSchema.challengeIdsArg ::
      CommentSchema.taskIdsArg ::
      Nil
}
