/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm._
import javax.inject.{Inject, Singleton}
import org.maproulette.framework.model.Group
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.{BaseParameter, FilterParameter, SubQueryFilter}
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class UserGroupRepository @Inject() (override val db: Database) extends RepositoryMixin {

  /**
    * Gets all the groups that a user belongs too
    *
    * @param osmUserId The OSM user if of the user that belongs to all the groups
    * @return A list of groups belonging to the user
    */
  def get(osmUserId: Long): List[Group] = {
    withMRTransaction { implicit c =>
      Query
        .simple(List(BaseParameter(Group.FIELD_UG_OSM_USER_ID, osmUserId)))
        .build("""SELECT groups.* FROM groups
           INNER JOIN user_groups ON user_groups.group_id = groups.id""")
        .as(GroupRepository.parser.*)
    }
  }

  /**
    * Adds a user to a specific group
    *
    * @param osmId The OSM id of the user to add
    * @param groupId The identifier of the group to add the user too
    * @param c An implicit connection
    * @return true if successful
    */
  def addUserToGroup(osmId: Long, groupId: Long)(implicit c: Option[Connection] = None): Boolean = {
    this.withMRTransaction { implicit c =>
      SQL("""INSERT INTO user_groups (osm_user_id, group_id)
          |VALUES ({osmId}, {groupId})
          |ON CONFLICT DO NOTHING""".stripMargin)
        .on(Symbol("osmId") -> osmId, Symbol("groupId") -> groupId)
        .execute()
    }
  }

  def addUserToProject(osmID: Long, groupType: Int, projectId: Long)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      SQL("""INSERT INTO user_groups (osm_user_id, group_id)
            SELECT {id}, id FROM groups
            WHERE group_type = {type} AND project_id = {pid}
            ON CONFLICT DO NOTHING
         """)
        .on(Symbol("id") -> osmID, Symbol("type") -> groupType, Symbol("pid") -> projectId)
        .execute()
    }
  }

  def removeUserFromGroup(osmId: Long, groupId: Long)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(
          List(
            BaseParameter(Group.FIELD_UG_OSM_USER_ID, osmId),
            BaseParameter(Group.FIELD_UG_GROUP_ID, groupId)
          )
        )
        .build("DELETE FROM user_groups")
        .execute()
    }
  }

  def removeUserFromProjectGroups(osmID: Long, projectId: Long, groupType: Int)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(
          List(
            BaseParameter(Group.FIELD_UG_OSM_USER_ID, osmID),
            SubQueryFilter(
              Group.FIELD_UG_GROUP_ID,
              Query.simple(
                List(
                  BaseParameter(Group.FIELD_PROJECT_ID, projectId),
                  FilterParameter.conditional(
                    Group.FIELD_GROUP_TYPE,
                    groupType,
                    includeOnlyIfTrue = groupType != -1
                  )
                ),
                "SELECT id FROM groups"
              )
            )
          )
        )
        .build("DELETE FROM user_groups")
        .execute()
    }
  }
}
