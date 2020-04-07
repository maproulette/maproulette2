/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.data.Actions
import org.maproulette.framework.model.{Group, Project, User}
import org.maproulette.framework.psql._
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.service.GroupService
import org.maproulette.models.{ClusteredPoint, Point, PointReview, Task}
import org.maproulette.session.SearchParameters
import org.maproulette.utils.Readers
import play.api.db.Database
import play.api.libs.json.Json

/**
  * Repository to handle all database actionns related to Projects, no business logic should be
  * found in this class.
  *
  * @author mcuthbert
  */
@Singleton
class ProjectRepository @Inject() (override val db: Database, groupService: GroupService)
    extends RepositoryMixin {

  /**
    * Finds 0 or more projects that match the filter criteria
    *
    * @param query The psql query object containing all the filtering, paging and ordering information
    * @param c An implicit connection, that defaults to None
    * @return The list of projects that match the filter criteria
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[Project] = {
    this.withMRTransaction { implicit c =>
      query.build("SELECT * FROM projects").as(this.parser.*)
    }
  }

  /**
    * For a given id returns the project
    *
    * @param id The id of the project you are looking for
    * @param c An implicit connection, defaults to none and one will be created automatically
    * @return None if not found, otherwise the Project
    */
  def retrieve(id: Long)(implicit c: Option[Connection] = None): Option[Project] = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(List(BaseParameter(Project.FIELD_ID, id)))
        .build("SELECT * FROM projects")
        .as(this.parser.*)
        .headOption
    }
  }

  private def parser: RowParser[Project] =
    ProjectRepository.parser(id => this.groupService.retrieveProjectGroups(id, User.superUser))

  /**
    * Inserts a project into the database
    *
    * @param project The project to insert into the database. The project will failed to be inserted
    *                if a project with the same name already exists. If the id field is set on the
    *                provided project it will be ignored.
    * @param c An implicit connection, that defaults to None
    * @return The project that was inserted now with the generated id
    */
  def create(project: Project)(implicit c: Option[Connection] = None): Project = {
    this.withMRTransaction { implicit c =>
      SQL("""INSERT INTO projects (name, owner_id, display_name, description, enabled, is_virtual, featured)
              VALUES ({name}, {ownerId}, {displayName}, {description}, {enabled}, {virtual}, {featured})
              RETURNING *""")
        .on(
          Symbol("name")        -> project.name,
          Symbol("ownerId")     -> project.owner,
          Symbol("displayName") -> project.displayName,
          Symbol("description") -> project.description.getOrElse(""),
          Symbol("enabled")     -> project.enabled,
          Symbol("virtual")     -> project.isVirtual.getOrElse(false),
          Symbol("featured")    -> project.featured
        )
        .as(this.parser.*)
        .head
    }
  }

  /**
    * Updates a project in the database based on the provided project object
    *
    * @param project The properties of the project to update
    * @param c An implicit connection, that defaults to None
    * @return The project that was updated
    */
  def update(project: Project)(implicit c: Option[Connection] = None): Option[Project] = {
    this.withMRTransaction { implicit c =>
      SQL("""UPDATE projects SET
           name = {name},
           owner_id = {ownerId},
           display_name = {displayName},
           description = {description},
           enabled = {enabled},
           is_virtual = {virtual},
           featured = {featured}
           WHERE id = {id}
           RETURNING *
        """)
        .on(
          Symbol("name")        -> project.name,
          Symbol("ownerId")     -> project.owner,
          Symbol("displayName") -> project.displayName,
          Symbol("description") -> project.description,
          Symbol("enabled")     -> project.enabled,
          Symbol("virtual")     -> project.isVirtual,
          Symbol("featured")    -> project.featured,
          Symbol("id")          -> project.id
        )
        .as(this.parser.*)
        .headOption
    }
  }

  /**
    * Deletes a project from the database, by default it simply sets the project to deleted.
    *
    * @param id The id of the project to delete
    * @param immediate Defaults to false, if set to true it will execute the delete immediately otherwise it will simply update the deleted flag on the project object
    * @param c An implicit conectioo, defaults to None
    * @return
    */
  def delete(id: Long, immediate: Boolean = false)(
      implicit c: Option[Connection] = None
  ): Boolean = {
    this.withMRTransaction { implicit c =>
      if (immediate) {
        Query
          .simple(List(BaseParameter(Project.FIELD_ID, id)))
          .build("DELETE FROM projects")
          .execute()
      } else {
        SQL("""UPDATE projects SET deleted = true WHERE id = {id}""")
          .on(Symbol("id") -> id)
          .execute()
      }
    }
  }

  def getSearchedClusteredPoints(
      params: SearchParameters,
      paging: Paging = Paging(),
      featured: Boolean = false
  )(implicit c: Option[Connection] = None): List[ClusteredPoint] = {
    this.withMRTransaction { implicit c =>
      val tagsEnabled = params.challengeParams.challengeTags.isDefined && params.challengeParams.challengeTags.get.nonEmpty

      val baseQuery = s"""
          SELECT c.id, u.osm_id, u.name, c.name, c.parent_id, p.name, c.blurb,
                  ST_AsGeoJSON(c.location) AS location, ST_AsGeoJSON(c.bounding) AS bounding,
                  c.difficulty, c.last_updated
          FROM challenges c
          INNER JOIN projects p ON p.id = c.parent_id
          INNER JOIN users u ON u.osm_id = c.owner_id
        ${if (tagsEnabled) {
        """
          |INNER JOIN tags_on_challenges tc ON tc.challenge_id = c.id
          |INNER JOIN tags t ON t.id = tc.tag_id
          """.stripMargin
      }}
          """
      val tagFilterString =
        params.challengeParams.challengeTags.getOrElse(List.empty).mkString("%(", "|", ")%")

      Query
        .simple(
          List(
            BaseParameter("c.location", null, Operator.NULL, negate = true),
            SubQueryFilter(
              "",
              Query.simple(
                List(
                  BaseParameter("parent_id", "c.id", useValueDirectly = true),
                  BaseParameter(
                    "status",
                    List(Task.STATUS_CREATED, Task.STATUS_SKIPPED, Task.STATUS_TOO_HARD),
                    Operator.IN
                  )
                ),
                "SELECT id FROM tasks",
                paging = Paging(1)
              ),
              operator = Operator.EXISTS
            ),
            FilterParameter.conditional("c.featured", true, includeOnlyIfTrue = true),
            BaseParameter(
              "c.name",
              SQLUtils.search(params.challengeParams.challengeSearch.getOrElse("")),
              Operator.ILIKE
            ),
            BaseParameter(
              "p.name",
              SQLUtils.search(params.projectSearch.getOrElse("")),
              Operator.ILIKE
            ),
            FilterParameter.conditional(
              "c.enabled",
              params.enabledChallenge,
              includeOnlyIfTrue = params.enabledChallenge
            ),
            FilterParameter.conditional(
              "p.enabled",
              params.enabledProject,
              includeOnlyIfTrue = params.enabledProject
            ),
            BaseParameter("c.deleted", false),
            BaseParameter("p.deleted", false),
            FilterParameter.conditional(
              "c.parent_id",
              params.projectIds.getOrElse(List.empty),
              includeOnlyIfTrue = params.projectIds.getOrElse(List.empty).nonEmpty
            ),
            ConditionalFilterParameter(
              CustomParameter(
                s"c.location @ ST_MakeEnvelope(${params.location.get.left}, ${params.location.get.bottom}, ${params.location.get.right}, ${params.location.get.top}, 4326)"
              ),
              params.location.isDefined
            ),
            FilterParameter.conditional(
              "t.name",
              tagFilterString,
              Operator.SIMILAR_TO,
              includeOnlyIfTrue = tagsEnabled
            )
          ),
          baseQuery,
          paging = paging
        )
        .build()
        .as(ProjectRepository.pointParser.*)
    }
  }

  def getClusteredPoints(
      projectId: Option[Long] = None,
      challengeIds: List[Long] = List.empty,
      enabledOnly: Boolean = true
  )(implicit c: Option[Connection] = None): List[ClusteredPoint] = {
    this.withMRTransaction { implicit c =>
      Query
        .simple(
          List(
            BaseParameter("c.location", null, Operator.NULL, negate = true),
            SubQueryFilter(
              "",
              Query.simple(
                List(
                  BaseParameter("parent_id", "c.id", useValueDirectly = true),
                  BaseParameter(
                    "status",
                    List(Task.STATUS_CREATED, Task.STATUS_SKIPPED, Task.STATUS_TOO_HARD),
                    Operator.IN
                  )
                ),
                "SELECT id FROM tasks",
                paging = Paging(1)
              ),
              operator = Operator.EXISTS
            ),
            FilterParameter.conditional("c.enabled", enabledOnly, includeOnlyIfTrue = enabledOnly),
            FilterParameter.conditional("p.enabled", enabledOnly, includeOnlyIfTrue = enabledOnly),
            BaseParameter("c.deleted", false),
            BaseParameter("p.deleted", false),
            FilterParameter.conditional(
              "c.parent_id",
              projectId.getOrElse(-1),
              includeOnlyIfTrue = projectId.isDefined
            ),
            FilterParameter.conditional(
              "c.id",
              challengeIds,
              Operator.IN,
              includeOnlyIfTrue = challengeIds.nonEmpty
            )
          ),
          """SELECT c.id, u.osm_id, u.name, c.name, c.parent_id, p.name, c.blurb,
                    ST_AsGeoJSON(c.location) AS location, ST_AsGeoJSON(c.bounding) AS bounding,
                    c.difficulty, c.last_updated
              FROM challenges c
              INNER JOIN projects p ON p.id = c.parent_id
              INNER JOIN users u ON u.osm_id = c.owner_id"""
        )
        .build()
        .as(ProjectRepository.pointParser.*)
    }
  }
}

object ProjectRepository extends Readers {
  val pointParser = {
    long("challenges.id") ~
      int("users.osm_id") ~
      str("users.name") ~
      str("challenges.name") ~
      int("challenges.parent_id") ~
      str("projects.name") ~
      get[Option[String]]("challenges.blurb") ~
      str("location") ~
      str("bounding") ~
      get[DateTime]("challenges.last_updated") ~
      int("challenges.difficulty") map {
      case id ~ osm_id ~ username ~ name ~ parentId ~ parentName ~ blurb ~ location ~ bounding ~ modified ~ difficulty =>
        val locationJSON = Json.parse(location)
        val coordinates  = (locationJSON \ "coordinates").as[List[Double]]
        val point        = Point(coordinates(1), coordinates.head)
        val pointReview  = PointReview(None, None, None, None, None)
        val boundingJSON = Json.parse(bounding)
        ClusteredPoint(
          id,
          osm_id,
          username,
          name,
          parentId,
          parentName,
          point,
          boundingJSON,
          blurb.getOrElse(""),
          modified,
          difficulty,
          -1,
          Actions.ITEM_TYPE_CHALLENGE,
          None,
          None,
          None,
          None,
          pointReview,
          -1,
          None,
          None
        )
    }
  }

  // The anorm row parser for the Project to map database records directly to Project objects
  def parser(groupFunc: (Long) => List[Group]): RowParser[Project] = {
    get[Long]("projects.id") ~
      get[Long]("projects.owner_id") ~
      get[String]("projects.name") ~
      get[DateTime]("projects.created") ~
      get[DateTime]("projects.modified") ~
      get[Option[String]]("projects.description") ~
      get[Boolean]("projects.enabled") ~
      get[Option[String]]("projects.display_name") ~
      get[Boolean]("projects.deleted") ~
      get[Boolean]("projects.is_virtual") ~
      get[Boolean]("projects.featured") map {
      case id ~ ownerId ~ name ~ created ~ modified ~ description ~ enabled ~ displayName ~ deleted ~ isVirtual ~ featured =>
        new Project(
          id,
          ownerId,
          name,
          created,
          modified,
          description,
          groupFunc.apply(id),
          enabled,
          displayName,
          deleted,
          Some(isVirtual),
          featured
        )
    }
  }
}
