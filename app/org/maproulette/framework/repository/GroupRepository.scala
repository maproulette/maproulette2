/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm.Macro.ColumnNaming
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
class GroupRepository @Inject() (val db: Database) extends RepositoryMixin {

  /**
    * Retrieves a single Group matching the given id
    *
    * @param id The id for the group
    * @param c An implicit connection, defaults to None
    * @return The group matching the id, None if not found
    */
  def retrieve(id: Long)(implicit c: Option[Connection] = None): Option[Group] = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(List(BaseParameter(Group.FIELD_ID, id)))
        .build("SELECT * FROM groups")
        .as(GroupRepository.parser.*)
        .headOption
    }
  }

  /**
    * Finds a list of groups matching the criteria given by the psqlQuery
    *
    * @param query The psqlQuery including filter, paging and ordering
    * @param c An implicit connection, defaults to None
    * @return A list of groups matching the psqlQuery criteria
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[Group] = {
    this.withMRTransaction { implicit c =>
      query.build("SELECT * FROM groups").as(GroupRepository.parser.*)
    }
  }

  /**
    * Inserts a group into the database
    *
    * @param group The group to insert into the database. If id is set on the object it will be ignored.
    * @param c An implicit connection, defaults to None
    * @return A list of groups matching the psqlQuery criteria
    */
  def create(group: Group)(implicit c: Option[Connection] = None): Group = {
    this.withMRTransaction { implicit c =>
      SQL("""INSERT INTO groups (project_id, name, group_type)
           VALUES ({projectId}, {name}, {groupType}) RETURNING *""")
        .on(
          Symbol("projectId") -> group.projectId,
          Symbol("name")      -> group.name,
          Symbol("groupType") -> group.groupType
        )
        .as(GroupRepository.parser.*)
        .head
    }
  }

  /**
    * Updates a group, the only value you can update in a group is the name
    *
    * @param group The group to update, which will include the name variable
    * @param c An implicit connection, that defaults to None
    * @return The updated group
    */
  def update(group: Group)(implicit c: Option[Connection] = None): Group = {
    this.withMRTransaction { implicit c =>
      SQL("""UPDATE groups SET name = {name} WHERE id = {id} RETURNING *""")
        .on(Symbol("name") -> group.name, Symbol("id") -> group.id)
        .as(GroupRepository.parser.*)
        .head
    }
  }

  /**
    * Deletes a group from the database
    *
    * @param id The id for the group
    * @param c An implicit connection, that defaults to None
    * @return true if successfully deleted
    */
  def delete(id: Long)(implicit c: Option[Connection] = None): Boolean = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(List(BaseParameter(Group.FIELD_ID, id)))
        .build("DELETE FROM groups")
        .execute()
    }
  }
}

object GroupRepository {
  // The anorm row parser to convert group records from the database to group objects
  val parser: RowParser[Group] = Macro.namedParser[Group](ColumnNaming.SnakeCase)
}
