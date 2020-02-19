// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.session.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Singleton}
import org.maproulette.Config
import org.maproulette.cache.{ListCacheObject, BasicCache, CacheManager}
import org.maproulette.exception.InvalidException
import org.maproulette.models.utils.TransactionManager
import org.maproulette.session.{Group, User}
import play.api.db.Database

/**
  * Data Access Layer for groups
  * todo: add permissions, although most of this will be behind the scenes
  *
  * @author cuthbertm
  */
@Singleton
class UserGroupDAL @Inject() (val db: Database, config: Config) extends TransactionManager {

  // The cache manager for the users
  val cacheManager = new CacheManager[Long, Group](config, Config.CACHE_ID_USERGROUPS)
  // The cache manager containing project to list of group ID's
  val projectCache = new BasicCache[Long, ListCacheObject[Long]](config)
  // The cache manager containing user to list of group ID's
  val userCache = new BasicCache[Long, ListCacheObject[Long]](config)

  // The anorm row parser to convert group records from the database to group objects
  val parser: RowParser[Group] = {
    get[Long]("groups.id") ~
      get[String]("groups.name") ~
      get[Long]("groups.project_id") ~
      get[Int]("groups.group_type") map {
      case id ~ name ~ projectId ~ groupType =>
        // If the modified date is too old, then lets update this user information from OSM
        new Group(id, name, projectId, groupType)
    }
  }

  /**
    * Creates a new group given a name and groupType id
    *
    * @param projectId The id of the project that this group is created under
    * @param groupType current only 1 (Admin) however currently no restriction on what you can supply here
    * @return The new group
    */
  def createGroup(projectId: Long, groupType: Int, user: User)(
      implicit c: Option[Connection] = None
  ): Group = {
    this.hasAccess(user)
    val groupPostfix: String = groupType match {
      case Group.TYPE_ADMIN        => "Admin"
      case Group.TYPE_WRITE_ACCESS => "Write"
      case Group.TYPE_READ_ONLY    => "Read"
      case _                       => throw new InvalidException("Invalid group type supplied to create group")
    }
    val groupName: String = s"${projectId}_$groupPostfix"
    this.cacheManager.withOptionCaching { () =>
      this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO groups (project_id, name, group_type)
             VALUES ($projectId, $groupName, $groupType) RETURNING *""".as(this.parser.*).headOption
      }
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

  /**
    * Update the group name, groups can only have their names modified. The project id for a group
    * cannot be updated
    *
    * @param groupId The id for the group
    * @param newName The new name of the group
    * @return The updated group
    */
  def updateGroup(groupId: Long, newName: String, user: User)(
      implicit c: Option[Connection] = None
  ): Option[Group] = {
    this.hasAccess(user)
    implicit val gid = groupId
    this.cacheManager.withUpdatingCache(Long => getGroup) { implicit cachedItem =>
      this.withMRTransaction { implicit c =>
        SQL"""UPDATE groups SET name = $newName WHERE id = $groupId RETURNING *"""
          .as(this.parser.*)
          .headOption
      }
    }
  }

  def getGroup(implicit groupId: Long, c: Option[Connection] = None): Option[Group] = {
    this.cacheManager.withCaching { () =>
      this.withMRConnection { implicit c =>
        SQL"""SELECT * FROM groups WHERE id = $groupId""".as(this.parser.*).headOption
      }
    }
  }

  /**
    * Deletes the group
    *
    * @param groupId The id of the group to delete
    * @return 1 or 0, the number of rows deleted. Can never be more than one, 0 if no group with id found to delete
    */
  def deleteGroup(groupId: Long, user: User)(implicit c: Option[Connection] = None): Int = {
    this.hasAccess(user)
    implicit val ids: List[Long] = List(groupId)
    this.cacheManager.withCacheIDDeletion { () =>
      clearCache(-1, groupId)
      this.withMRTransaction { implicit c =>
        SQL"""DELETE FROM groups WHERE id = $groupId""".executeUpdate()
      }
    }
  }

  /**
    * Clears both the project and user cache
    *
    * @param id
    * @param groupId
    */
  def clearCache(id: Long = -1, groupId: Long = -1): Unit = {
    clearProjectCache(id, groupId)
    clearUserCache(id, groupId)
  }

  /**
    * Clears the project cache
    *
    * @param id If id is supplied will only remove the project with that id
    */
  def clearProjectCache(id: Long = -1, groupId: Long = -1): Unit = {
    clearCacheType(id, groupId, this.projectCache)
  }

  /**
    * Clears the user cache
    *
    * @param osmId If osmId is supplied will only remove the user with that osmId
    */
  def clearUserCache(osmId: Long = -1, groupId: Long = -1): Unit = {
    clearCacheType(osmId, groupId, this.userCache)
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
    * Deletes the group
    *
    * @param name The name of the group to delete
    * @return 1 or 0, the number of rows deleted. Can never be more than one, 0 if no group with name found to delete
    */
  def deleteGroupByName(name: String, user: User)(implicit c: Option[Connection] = None): Int = {
    this.hasAccess(user)
    implicit val names: List[String] = List(name)
    // get the cache item first so we can remove from user and project caches
    this.cacheManager.getByName(name) match {
      case Some(v) => clearCache(-1, v.id)
      case None    =>
    }
    this.cacheManager.withCacheNameDeletion { () =>
      this.withMRTransaction { implicit c =>
        SQL"""DELETE FROM groups WHERE name = $name""".executeUpdate()
      }
    }
  }

  /**
    * Gets all the groups that a specific user belongs too
    *
    * @param osmUserId The osm id of the user
    * @return A list of groups the user belongs too
    */
  def getUserGroups(osmUserId: Long, user: User)(
      implicit c: Option[Connection] = None
  ): List[Group] = {
    getGroups(
      osmUserId,
      this.userCache,
      s"""SELECT * FROM groups g
        INNER JOIN user_groups ug ON ug.group_id = g.id
        WHERE ug.osm_user_id = $osmUserId""",
      user
    )
  }

  private def getGroups(
      id: Long,
      cache: BasicCache[Long, ListCacheObject[Long]],
      sql: String,
      user: User
  )(implicit c: Option[Connection] = None): List[Group] = {
    this.hasAccess(user)
    val retGroups = cache.get(id) match {
      case Some(ids) =>
        // from the id cache, get all the groups in the group cache, if any are missing re-get everything
        val projectGroups = ids.list.flatMap(id => this.cacheManager.cache.get(id))
        if (projectGroups.lengthCompare(ids.list.size) != 0) {
          List.empty[Group]
        } else {
          projectGroups
        }
      case None => List.empty
    }
    retGroups match {
      case l if l.nonEmpty => l
      case l =>
        val groupList = this.withMRConnection { implicit c =>
          SQL"""#$sql""".as(this.parser.*)
        }
        // add all the retrieved objects to the cache
        groupList.foreach(group => this.cacheManager.cache.addObject(group))
        cache.add(id, new ListCacheObject(groupList.map(_.id)))
        groupList
    }
  }

  /**
    * Gets all the groups that a specific project has
    *
    * @param projectId The project id
    * @return
    */
  def getProjectGroups(projectId: Long, user: User)(
      implicit c: Option[Connection] = None
  ): List[Group] = {
    getGroups(
      projectId,
      this.projectCache,
      s"SELECT * FROM groups g WHERE project_id = $projectId",
      user
    )
  }
}
