/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.permissions

import java.sql.Connection

import com.google.inject.Singleton
import javax.inject.{Inject, Provider}
import org.maproulette.cache.CacheObject
import org.maproulette.data._
import org.maproulette.exception.NotFoundException
import org.maproulette.framework.model._
import org.maproulette.framework.service.ServiceManager
import org.maproulette.models._
import org.maproulette.models.dal.DALManager

/**
  * @author cuthbertm
  */
@Singleton
class Permission @Inject() (
    dalManager: Provider[DALManager],
    serviceManager: ServiceManager
) {

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
    this.getItem(itemType) match {
      case Some(obj) => this.hasObjectReadAccess(obj, user)
      case _ =>
        throw new NotFoundException(
          s"No ${Actions.getTypeName(itemType.typeId).getOrElse("Unknown")} found using id [$id] to check read access"
        )
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
          this.serviceManager.group.retrieve(id) match {
            case Some(obj) =>
              this.hasObjectWriteAccess(obj.asInstanceOf[CacheObject[Long]], user, groupType)
            case _ =>
              throw new NotFoundException(
                s"No Group found using id [$id] to check write access"
              )
          }
        case _ =>
          this.getItem(itemType)(id, c) match {
            case Some(obj) =>
              this.hasObjectWriteAccess(obj.asInstanceOf[CacheObject[Long]], user, groupType)
            case _ =>
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
  def hasObjectAdminAccess(obj: Any, user: User)(
      implicit c: Option[Connection] = None
  ): Unit =
    this.hasObjectWriteAccess(obj, user, Group.TYPE_ADMIN)

  /**
    * Checks whether a user has write permission to the given object
    *
    * @param obj  The object in question
    * @param user The user requesting write access
    */
  def hasObjectWriteAccess(
      obj: Any,
      user: User,
      groupType: Int = Group.TYPE_WRITE_ACCESS
  )(implicit c: Option[Connection] = None): Unit =
    if (!user.isSuperUser) {
      obj match {
        case u: User => this.hasObjectReadAccess(u, user)
        case p: Project =>
          this.hasProjectAccess(Some(p), user, groupType)
        case c: Challenge =>
          if (c.general.owner != user.osmProfile.id) {
            this.hasProjectAccess(
              dalManager
                .get()
                .challenge
                .retrieveRootObject(Right(c), user),
              user,
              groupType
            )
          }
        case vc: VirtualChallenge =>
          if (vc.ownerId != user.osmProfile.id) {
            throw new IllegalAccessException(
              s"Only super users or the owner of the Virtual Challenge can write to it."
            )
          }
        case t: Task =>
          this.hasProjectAccess(
            dalManager.get().task.retrieveRootObject(Right(t), user),
            user,
            groupType
          )
        case tag: Tag =>
          this.hasReadAccess(TagType(), user)(tag.id)
        case g: Group =>
          this.hasProjectTypeAccess(user, Group.TYPE_ADMIN)(g.projectId)
        case b: Bundle =>
          if (b.owner != user.osmProfile.id) {
            throw new IllegalAccessException(
              s"Only super users or the owner of the bundle can write to it."
            )
          }
        case _ =>
          throw new IllegalAccessException(s"Unknown object type provided ${obj.toString}")
      }
    }

  /**
    * Checks to see if the object has read access to the object in request. Will throw an
    * IllegalAccessException if it does not have access. Read access is available on all objects
    * except User objects, users can only read their own user objects or all user objects if they are
    * a super user.
    *
    * @param obj  The object you are checking to see if the user has read access on the object
    * @param user The user requesting the access.
    */
  def hasObjectReadAccess(obj: Any, user: User)(
      implicit c: Option[Connection] = None
  ): Unit = if (!user.isSuperUser) {
    obj match {
      case u: User if u.id != user.id & u.osmProfile.id != user.osmProfile.id =>
        throw new IllegalAccessException(
          s"User does not have read access to requested user object [${u.id}]"
        )
      case g: Group =>
        this.hasProjectTypeAccess(user)(g.projectId)
      case _ => // don't do anything, they have access
    }
  }

  def hasProjectTypeAccess(
      user: User,
      groupType: Int = Group.TYPE_ADMIN
  )(implicit id: Long, c: Option[Connection] = None): Unit =
    this.hasProjectAccess(this.serviceManager.project.retrieve(id), user, groupType)

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

  private def getItem(
      itemType: ItemType
  )(implicit id: Long, c: Option[Connection] = None): Option[_] = {
    try {
      dalManager.get().getManager(itemType).retrieveById
    } catch {
      case _: NotFoundException =>
        // if the dal manager doesn't have it then maybe the ServiceManager does
        serviceManager.getService(itemType).retrieve(id)
      case e: Throwable => throw e
    }
  }
}
