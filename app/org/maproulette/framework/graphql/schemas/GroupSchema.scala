/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.graphql.schemas

import javax.inject.Inject
import org.maproulette.framework.graphql.UserContext
import org.maproulette.framework.model.Group
import org.maproulette.framework.service.GroupService
import sangria.schema._

/**
  * @author mcuthbert
  */
class GroupSchema @Inject() (override val service: GroupService)
    extends MRSchema[Group]
    with MRSchemaTypes {
  val queries: List[Field[UserContext, Unit]] = List(
    Field(
      name = "group",
      description = Some("Retrieve a group based on an ID"),
      fieldType = OptionType(GroupType),
      arguments = MRSchema.idArg :: Nil,
      resolve = context => this.service.retrieve(context.arg(MRSchema.idArg))
    ),
    Field(
      name = "projectgroups",
      description = Some("Retrieve all the groups associated with a specific project"),
      fieldType = ListType(GroupType),
      arguments = MRSchema.idArg :: Nil,
      resolve =
        context => this.service.retrieveProjectGroups(context.arg(MRSchema.idArg), context.ctx.user)
    ),
    Field(
      name = "usergroups",
      description = Some("Retrieve all the groups that a specific user is a member of"),
      fieldType = ListType(GroupType),
      arguments = MRSchema.osmIdArg :: Nil,
      resolve =
        context => this.service.retrieveUserGroups(context.arg(MRSchema.osmIdArg), context.ctx.user)
    )
  )

  val mutations: List[Field[UserContext, Unit]] = List(
    Field(
      name = "addUserToGroup",
      description = Some("Adds a specific user to a specific group"),
      fieldType = GroupType,
      arguments = MRSchema.osmIdArg :: MRSchema.idArg :: Nil,
      resolve = context => {
        val group = this.retrieveObject(context.arg(MRSchema.idArg))
        this.service.addUserToGroup(context.arg(MRSchema.osmIdArg), group, context.ctx.user)
        group
      }
    ),
    Field(
      name = "removeUserFromProjectGroups",
      description = Some("Remove a user from the specific group types in a specific project"),
      fieldType = BooleanType,
      arguments = MRSchema.osmIdArg :: MRSchema.idArg :: GroupSchema.groupTypeArgument :: Nil,
      resolve = context => {
        this.service.removeUserFromProjectGroups(
          context.arg(MRSchema.osmIdArg),
          context.arg(MRSchema.idArg),
          context.arg(GroupSchema.groupTypeArgument),
          context.ctx.user
        )
        true
      }
    ),
    Field(
      name = "removeUserFromGroup",
      description = Some("Remove a user from a specific group"),
      fieldType = BooleanType,
      arguments = MRSchema.osmIdArg :: MRSchema.idArg :: Nil,
      resolve = context => {
        this.service.removeUserFromGroup(
          context.arg(MRSchema.osmIdArg),
          this.retrieveObject(context.arg(MRSchema.idArg)),
          context.ctx.user
        )
        true
      }
    ),
    Field(
      name = "deleteGroup",
      description = Some("Delete a group based on the provided identifier"),
      fieldType = BooleanType,
      arguments = MRSchema.idArg :: Nil,
      resolve = context => this.service.delete(context.arg(MRSchema.idArg), context.ctx.user)
    ),
    Field(
      name = "createGroup",
      description = Some("Create a group of a specific group type"),
      fieldType = GroupType,
      arguments = ProjectSchema.projectIdArg :: GroupSchema.groupTypeArgument :: MRSchema.nameArg :: Nil,
      resolve = context =>
        this.service.create(
          context.arg(ProjectSchema.projectIdArg),
          context.arg(GroupSchema.groupTypeArgument),
          context.ctx.user,
          context.arg(MRSchema.nameArg)
        )
    ),
    Field(
      name = "updateGroup",
      description = Some("Updates a group name"),
      fieldType = GroupType,
      arguments = MRSchema.idArg :: MRSchema.nameArg :: Nil,
      resolve = context =>
        this.service
          .update(context.arg(MRSchema.idArg), context.arg(MRSchema.nameArg), context.ctx.user)
    )
  )
}

object GroupSchema {
  val groupTypeArgument = Argument(
    "groupType",
    IntType,
    "The type of group, -1 = SUPER USER, 1 = ADMIN, 2 = WRITE, 3 = READ"
  )
}
