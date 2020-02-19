// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.permissions

import java.sql.Connection

import com.google.inject.Singleton
import javax.inject.{Inject, Provider}
import org.maproulette.data._
import org.maproulette.exception.NotFoundException
import org.maproulette.models._
import org.maproulette.models.dal.DALManager
import org.maproulette.session.{Group, User}

/**
  * @author cuthbertm
  */
@Singleton
class Permission @Inject() (dalManager: Provider[DALManager]) {

  /**
    * Checks to see if the object has read access to the object in request. Will throw an
    * IllegalAccessException if it does not have access. Read access is available on all objects
    * except User objects, users can only read their own user objects or all user objects if they are
    * a super user.
    *
    * @param obj  The object you are checking to see if the user has read access on the object
    * @param user The user requesting the access.
    */
  def hasObjectReadAccess(obj: BaseObject[Long], user: User)(
      implicit c: Option[Connection] = None
  ): Unit = if (!user.isSuperUser) {
    obj.itemType match {
      case UserType()
          if obj.id != user.id & obj.asInstanceOf[User].osmProfile.id != user.osmProfile.id =>
        throw new IllegalAccessException(
          s"User does not have read access to requested user object [${obj.id}]"
        )
      case GroupType() =>
        this.hasProjectTypeAccess(user)(obj.asInstanceOf[Group].projectId)
      case _ => // don't do anything, they have access
    }
  }

  /**
    * Checks read access based purely on the id and item type, will throw a NotFoundException if
    * no object of the given type with the given id can be found. Delegates permission check to
    * object based hasReadAccess function
    *
    * @param itemType The type of item that user is checked read access against
    * @param user     The user checking whether they have access or not
    * @param id       The id of the object
    */
  def hasReadAccess(
      itemType: ItemType,
      user: User
  )(implicit id: Long, c: Option[Connection] = None): Unit = {
    val retrieveFunction = itemType match {
      case UserType()  => dalManager.get().user.retrieveById
      case GroupType() => dalManager.get().userGroup.getGroup
      case _           => dalManager.get().getManager(itemType).retrieveById
    }
    retrieveFunction match {
      case Some(obj) => this.hasObjectReadAccess(obj.asInstanceOf[BaseObject[Long]], user)
      case None =>
        throw new NotFoundException(
          s"No ${Actions.getTypeName(itemType.typeId).getOrElse("Unknown")} found using id [$id] to check read access"
        )
    }
  }

  /**
    * Checks whether a user has write permission to the given object
    *
    * @param obj  The object in question
    * @param user The user requesting write access
    */
  def hasObjectWriteAccess(
      obj: BaseObject[Long],
      user: User,
      groupType: Int = Group.TYPE_WRITE_ACCESS
  )(implicit c: Option[Connection] = None): Unit =
    if (!user.isSuperUser) {
      obj.itemType match {
        case UserType() => this.hasObjectReadAccess(obj, user)
        case ProjectType() =>
          this.hasProjectAccess(Some(obj.asInstanceOf[Project]), user, groupType)
        case ChallengeType() | SurveyType() =>
          if (obj.asInstanceOf[Challenge].general.owner != user.osmProfile.id) {
            this.hasProjectAccess(
              dalManager
                .get()
                .challenge
                .retrieveRootObject(Right(obj.asInstanceOf[Challenge]), user),
              user,
              groupType
            )
          }
        case VirtualChallengeType() =>
          if (obj.asInstanceOf[VirtualChallenge].ownerId != user.osmProfile.id) {
            throw new IllegalAccessException(
              s"Only super users or the owner of the Virtual Challenge can write to it."
            )
          }
        case TaskType() =>
          this.hasProjectAccess(
            dalManager.get().task.retrieveRootObject(Right(obj.asInstanceOf[Task]), user),
            user,
            groupType
          )
        case TagType() =>
        case GroupType() =>
          this.hasProjectTypeAccess(user, Group.TYPE_ADMIN)(obj.asInstanceOf[Group].projectId)
        case BundleType() =>
          if (obj.asInstanceOf[Bundle].owner != user.osmProfile.id) {
            throw new IllegalAccessException(
              s"Only super users or the owner of the bundle can write to it."
            )
          }
        case _ =>
          throw new IllegalAccessException(s"Unknown object type ${obj.itemType.toString}")
      }
    }

  def hasWriteAccess(itemType: ItemType, user: User, groupType: Int = Group.TYPE_WRITE_ACCESS)(
      implicit id: Long,
      c: Option[Connection] = None
  ): Unit =
    if (!user.isSuperUser) {
      itemType match {
        case UserType() =>
          // we use read access here, simply because read and write access on a user is the same in that
          // you are required to the super user or owner to access the User object. It is much stricter
          // than other objects in the system
          this.hasReadAccess(itemType, user)
        case GroupType() =>
          dalManager.get().userGroup.getGroup match {
            case Some(obj) =>
              this.hasObjectWriteAccess(obj.asInstanceOf[BaseObject[Long]], user, groupType)
            case None =>
              throw new NotFoundException(
                s"No Group found using id [$id] to check write access"
              )
          }
        case _ =>
          dalManager.get().getManager(itemType).retrieveById match {
            case Some(obj) =>
              this.hasObjectWriteAccess(obj.asInstanceOf[BaseObject[Long]], user, groupType)
            case None =>
              throw new NotFoundException(
                s"No ${Actions.getTypeName(itemType.typeId).getOrElse("Unknown")} found using id [$id] to check write access"
              )
          }
      }
    }

  /**
    * Uses the hasWriteAccess function, but switches to check for users membership to the admin group instead of the write group
    *
    * @param obj  The object that we are checking to see if they have access to it
    * @param user The user making the request
    * @param c    An implicit database connection
    */
  def hasObjectAdminAccess(obj: BaseObject[Long], user: User)(
      implicit c: Option[Connection] = None
  ): Unit =
    this.hasObjectWriteAccess(obj, user, Group.TYPE_ADMIN)

  /**
    * Uses the hasWriteAccess function, but switches to check for users membership to the admin group instead of the write group
    *
    * @param itemType The type of object that the user want's access too
    * @param user     The user making the request
    * @param id       The id of the object
    * @param c        An implicit database connection
    */
  def hasAdminAccess(
      itemType: ItemType,
      user: User
  )(implicit id: Long, c: Option[Connection] = None): Unit =
    this.hasWriteAccess(itemType, user, Group.TYPE_ADMIN)

  /**
    * Checks if a user is a super user
    *
    * @param user
    */
  def hasSuperAccess(user: User): Unit = if (!user.isSuperUser) {
    throw new IllegalAccessException(s"Only super users can perform this action.")
  }

  def hasProjectTypeAccess(
      user: User,
      groupType: Int = Group.TYPE_ADMIN
  )(implicit id: Long, c: Option[Connection] = None): Unit =
    this.hasProjectAccess(dalManager.get().project.retrieveById, user, groupType)

  def hasProjectAccess(project: Option[Project], user: User, groupType: Int = Group.TYPE_ADMIN)(
      implicit c: Option[Connection] = None
  ): Unit = if (!user.isSuperUser) {
    project match {
      case Some(p) =>
        if (project.get.owner != user.osmProfile.id && !user.groups
              .exists(g => p.id == g.projectId && g.groupType <= groupType)) {
          throw new IllegalAccessException(
            s"User [${user.id}] does not have access to this project [${p.id}]"
          )
        }
      case None =>
        throw new NotFoundException(s"No project found to check access for object")
    }
  }
}
