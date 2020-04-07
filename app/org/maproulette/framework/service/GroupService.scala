/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import java.sql.Connection

import javax.inject.{Inject, Singleton}
import org.maproulette.Config
import org.maproulette.cache.{BasicCache, CacheManager, ListCacheObject}
import org.maproulette.exception.InvalidException
import org.maproulette.framework.model.{Group, User}
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseParameter, Operator}
import org.maproulette.framework.repository.{GroupRepository, UserGroupRepository}
import org.maproulette.permissions.Permission

/**
  * @author mcuthbert
  */
@Singleton
class GroupService @Inject() (
    repository: GroupRepository,
    userGroupRepository: UserGroupRepository,
    config: Config,
    permission: Permission
) extends ServiceMixin[Group] {
  // The cachemanager for groups
  val cache = new CacheManager[Long, Group](config, Config.CACHE_ID_USERGROUPS)
  // The cache manager containing project to list of group ID's
  val projectCache = new BasicCache[Long, ListCacheObject[Long]](config)
  // The cache manager containing user to list of group ID's
  val userCache = new BasicCache[Long, ListCacheObject[Long]](config)

  /**
    * Using this function would always fail, as it requires super user access
    *
    * @param query The query to match against to retrieve the objects
    * @return The list of objects
    */
  override def query(query: Query): List[Group] = this.query(query, User.guestUser)

  def query(query: Query, user: User): List[Group] = {
    this.hasAccess(user)
    this.repository.query(query)
  }

  def create(projectId: Long, groupType: Int, user: User, groupName: String = ""): Group = {
    this.hasAccess(user)
    val name = groupName match {
      case "" =>
        groupType match {
          case Group.TYPE_ADMIN        => s"${projectId}_Admin"
          case Group.TYPE_WRITE_ACCESS => s"${projectId}_Write"
          case Group.TYPE_READ_ONLY    => s"${projectId}_Read"
          case _                       => throw new InvalidException("Invalid group type supplied to create group")
        }
      case n => n
    }
    this.cache.withOptionCaching { () =>
      Some(this.repository.create(Group(-1, name, projectId, groupType)))
    } match {
      case Some(v) =>
        // add to project cache
        projectCache.get(projectId) match {
          case Some(groups) =>
            projectCache.add(projectId, new ListCacheObject(groups.list :+ v.id))
          case None =>
        }
        v
      case None =>
        throw new Exception(
          s"""For some reason a group was inserted but no group object returned.
                                            Possible data inconsistent possible, so operation cancelled."""
        )
    }
  }

  def update(id: Long, newName: String, user: User): Group = {
    this.hasAccess(user)
    this.cache
      .withUpdatingCache(id => retrieve(id)) { implicit cachedItem =>
        Some(this.repository.update(Group(id, newName, cachedItem.projectId, cachedItem.groupType)))
      }(id = id)
      .head
  }

  def retrieve(id: Long): Option[Group] = {
    this.cache.withCaching { () =>
      this.repository.query(Query.simple(List(BaseParameter(Group.FIELD_ID, id)))).headOption
    }(id = id)
  }

  def retrieveUserGroups(osmUserId: Long, user: User): List[Group] =
    this.getGroups(osmUserId, this.userCache, this.userGroupRepository.get, user)

  /**
    * Uses the cache to retrieve groups from the Project and User group caches
    *
    * @param id The primary id of either the Project or the user (osmUserId)
    * @param cache The cache to retrieve the data from, either Project or User
    * @param groupFunc The function to retrieve the group list from
    * @param user The user that is executing the query
    * @param c The implicit connection, defaults to None
    * @return The list of groups
    */
  private def getGroups(
      id: Long,
      cache: BasicCache[Long, ListCacheObject[Long]],
      groupFunc: Long => List[Group],
      user: User
  )(implicit c: Option[Connection] = None): List[Group] = {
    this.hasAccess(user)
    val retGroups = cache.get(id) match {
      case Some(ids) =>
        // from the id cache, get all the groups in the group cache, if any are missing re-get everything
        val projectGroups = ids.list.flatMap(id => this.cache.cache.get(id))
        if (projectGroups.lengthCompare(ids.list.size) != 0) {
          List.empty[Group]
        } else {
          projectGroups
        }
      case None => List.empty
    }
    retGroups match {
      case l if l.nonEmpty => l
      case _ =>
        val groupList = groupFunc.apply(id)
        // add all the retrieved objects to the cache
        groupList.foreach(group => this.cache.cache.addObject(group))
        cache.add(id, new ListCacheObject(groupList.map(_.id)))
        groupList
    }
  }

  /**
    * Access for user functions are limited to super users
    *
    * @param user A super user
    */
  private def hasAccess(user: User): Unit = {
    if (!user.isSuperUser) {
      throw new IllegalAccessException("Only super users have access to group objects.")
    }
  }

  def retrieveProjectGroups(projectId: Long, user: User): List[Group] =
    this.getGroups(
      projectId,
      this.projectCache,
      id => {
        this.repository.query(
          Query.simple(
            List(BaseParameter("project_id", id, Operator.EQ))
          )
        )
      },
      user
    )

  /**
    * Add a user to a group
    *
    * @param osmId The OSM ID of the user to add to the project
    * @param group The group that user is being added too
    * @param user  The user that is adding the user to the project
    */
  def addUserToGroup(osmId: Long, group: Group, user: User): Unit = {
    this.permission.hasSuperAccess(user)
    this.clearCache(osmId = osmId)
    this.userGroupRepository.addUserToGroup(osmId, group.id)
  }

  def removeUserFromProjectGroups(
      osmID: Long,
      projectId: Long,
      groupType: Int,
      user: User
  ): Unit = {
    this.permission.hasSuperAccess(user)
    this.clearCache(projectId)
    this.userGroupRepository.removeUserFromProjectGroups(osmID, projectId, groupType)
  }

  /**
    * Clears both the project and user cache
    *
    * @param projectId The id of the project to clear in the project cache
    * @param osmId The osmId of the user in the cache to clear
    * @param groupId The id of the group to clear in the project and user cache
    */
  def clearCache(projectId: Long = -1, osmId: Long = -1, groupId: Long = -1): Unit = {
    this.clearCacheType(projectId, groupId, this.projectCache)
    this.clearCacheType(osmId, groupId, this.userCache)
  }

  private def clearCacheType(
      id: Long = -1,
      groupId: Long = -1,
      cache: BasicCache[Long, ListCacheObject[Long]]
  ): Unit = {
    if (id > -1) {
      if (groupId > -1) {
        cache.get(id) match {
          case Some(v) =>
            cache.add(id, new ListCacheObject(v.list.filter(_ != groupId)))
          case None => //ignore
        }
      } else {
        cache.remove(id)
      }
    } else {
      if (groupId > -1) {
        cache.cache.foreach(v => {
          cache.add(v._1, new ListCacheObject(v._2.value.list.filter(id => id != groupId)))
        })
      } else {
        cache.clear()
      }
    }
  }

  /**
    * Removes a user from a group
    *
    * @param osmId The OSM ID of the user
    * @param group The group that you are removing from the user
    * @param user  The user executing the request
    */
  def removeUserFromGroup(osmId: Long, group: Group, user: User): Unit = {
    this.permission.hasSuperAccess(user)
    this.clearCache(osmId = osmId)
    this.userGroupRepository.removeUserFromGroup(osmId, group.id)
  }

  def addUserToProject(osmID: Long, groupType: Int, projectId: Long, user: User): Unit = {
    this.permission.hasSuperAccess(user)
    this.clearCache(projectId)
    this.userGroupRepository.addUserToProject(osmID, groupType, projectId)
  }

  /**
    * Deletes a group from the database
    *
    * @param id The id of the group
    * @param user The user trying to delete the group
    * @return true if successful
    */
  def delete(id: Long, user: User): Boolean = {
    this.hasAccess(user)
    this.cache.withCacheIDDeletion { () =>
      this.clearCache(-1, -1, id)
      this.repository.delete(id)
    }(ids = List(id))
  }
}
