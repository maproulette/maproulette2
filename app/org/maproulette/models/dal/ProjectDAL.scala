// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.dal.UserGroupDAL
import org.maproulette.session.{Group, SearchParameters, User}
import org.maproulette.exception.NotFoundException
import org.maproulette.models.dal.mixin.OwnerMixin
import play.api.db.Database
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.ListBuffer

/**
  * Specific functions for the project data access layer
  *
  * @author cuthbertm
  */
@Singleton
class ProjectDAL @Inject() (
    override val db: Database,
    childDAL: ChallengeDAL,
    surveyDAL: SurveyDAL,
    userGroupDAL: UserGroupDAL,
    override val permission: Permission,
    config: Config
) extends ParentDAL[Long, Project, Challenge]
    with OwnerMixin[Project] {

  // manager for the cache of the projects
  override val cacheManager = new CacheManager[Long, Project](config, Config.CACHE_ID_PROJECTS)
  // table name for projects
  override val tableName: String = "projects"
  // table name for project children, challenges
  override val childTable: String = "challenges"
  // anorm row parser for child as defined by the challenge data access layer
  override val childParser  = childDAL.parser
  override val childColumns = childDAL.retrieveColumns

  // The anorm row parser for the Project to map database records directly to Project objects
  override val parser: RowParser[Project] = {
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
          userGroupDAL.getProjectGroups(id, User.superUser),
          enabled,
          displayName,
          deleted,
          Some(isVirtual),
          featured
        )
    }
  }

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
      int("challenges.difficulty") ~
      int("challenges.challenge_type") map {
      case id ~ osm_id ~ username ~ name ~ parentId ~ parentName ~ blurb ~ location ~ bounding ~ modified ~ difficulty ~ challengeType =>
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
          challengeType,
          -1,
          None,
          None,
          pointReview,
          -1,
          None,
          None
        )
    }
  }

  /**
    * Inserts a new project object into the database
    *
    * @param project The project to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(project: Project, user: User)(
      implicit c: Option[Connection] = None
  ): Project = {
    //permissions don't need to be checked, anyone can create a project
    //this.permission.hasObjectWriteAccess(project, user)
    this.cacheManager.withOptionCaching { () =>
      val isVirtual = project.isVirtual match {
        case Some(v) => v
        case _       => false
      }

      // Only super users can feature a project
      val featured = project.featured && user.isSuperUser

      val newProject = this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO projects (name, owner_id, display_name, description, enabled, is_virtual, featured)
              VALUES (${project.name}, ${user.osmProfile.id}, ${project.displayName}, ${project.description}, ${project.enabled}, ${isVirtual}, ${featured})
              RETURNING *""".as(parser.*).head
      }
      db.withTransaction { implicit c =>
        // Every new project needs to have a admin group created for them
        this.userGroupDAL.createGroup(newProject.id, Group.TYPE_ADMIN, User.superUser)
        this.userGroupDAL.createGroup(newProject.id, Group.TYPE_WRITE_ACCESS, User.superUser)
        this.userGroupDAL.createGroup(newProject.id, Group.TYPE_READ_ONLY, User.superUser)
        Some(newProject)
      }
    }.get
  }

  /**
    * Updates a project in the database
    *
    * @param updates A json object containing all the fields that are too be updated.
    * @param id      The id of the object that you are updating.
    * @return An optional object, it will return None if no object found with a matching id that was supplied.
    */
  override def update(
      updates: JsValue,
      user: User
  )(implicit id: Long, c: Option[Connection] = None): Option[Project] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasObjectWriteAccess(cachedItem, user)
      this.withMRTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val displayName =
          (updates \ "displayName").asOpt[String].getOrElse(cachedItem.displayName.getOrElse(""))
        val owner = (updates \ "ownerId").asOpt[Long].getOrElse(cachedItem.owner)
        val description =
          (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val enabled = (updates \ "enabled").asOpt[Boolean] match {
          case Some(e) if !user.isSuperUser && !user.adminForProject(id) =>
            logger.warn(
              s"User [${user.name} - ${user.id}] is not a super user and cannot enable or disable projects"
            )
            cachedItem.enabled
          case Some(e) => e
          case None    => cachedItem.enabled
        }
        val featured = (updates \ "featured").asOpt[Boolean] match {
          case Some(f) if !user.isSuperUser =>
            logger.warn(
              s"User [${user.name} - ${user.id}] is not a super user and cannot feature projects"
            )
            cachedItem.featured
          case Some(f) => f
          case None    => cachedItem.featured
        }

        SQL"""UPDATE projects SET name = $name,
              owner_id = $owner,
              display_name = $displayName,
              description = $description,
              enabled = $enabled,
              featured = $featured
              WHERE id = $id RETURNING *""".as(this.parser.*).headOption
      }
    }
  }

  /**
    * This fetch function will retreive a list of projects with the given projectIds
    *
    * @param projectList The projectIds to fetch
    * @return A list of tags that contain the supplied prefix
    */
  def fetch(projectList: Option[List[Long]] = None): List[Project] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM ${this.tableName}
                      WHERE TRUE ${this.getLongListFilter(projectList, "id")}"""
      SQL(query).as(this.parser.*)
    }
  }

  /**
    * Retrieves a list of projects that are featured
    *
    * @param onlyEnabled Restrict to enabled projects
    * @param limit       Limit the number of results to be returned
    * @param offset      For paging, ie. the page number starting at 0
    * @return A list of projects
    */
  def getFeaturedProjects(
      onlyEnabled: Boolean = true,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0
  )(implicit c: Option[Connection] = None): List[Project] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM ${this.tableName}
            WHERE featured = TRUE ${this.enabled(onlyEnabled)}
            LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""

      SQL(query).on(Symbol("offset") -> offset).as(this.parser.*)
    }
  }

  /**
    * This find function will search the "name" and "display_name" fields for
    * any references of the search string. So will be wrapped by %%, eg. LIKE %test%
    *
    * @param searchString The string to search for within the name field
    * @param limit        Limit the number of results to be returned
    * @param offset       For paging, ie. the page number starting at 0
    * @return A list of tags that contain the supplied prefix
    */
  override def find(
      searchString: String,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0,
      onlyEnabled: Boolean = false,
      orderColumn: String = "id",
      orderDirection: String = "ASC"
  )(implicit parentId: Long = -1, c: Option[Connection] = None): List[Project] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM ${this.tableName}
                      WHERE ${this.searchField("name")(None)} OR
                      ${this.searchField("display_name")(None)} OR
                      ${this.fuzzySearch("display_name")(None)} ${this.enabled(onlyEnabled)}
                      ${this.parentFilter(parentId)}
                      ${this.order(
          orderColumn = Some(orderColumn),
          orderDirection = orderDirection,
          nameFix = true
        )}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on(Symbol("ss") -> searchString, Symbol("offset") -> offset).as(this.parser.*)
    }
  }

  /**
    * Gets a list of all projects that are specific managed by the supplied user
    *
    * @param user The user executing the request
    * @return A list of projects managed by the user
    */
  def listManagedProjects(
      user: User,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0,
      onlyEnabled: Boolean = false,
      onlyOwned: Boolean = false,
      searchString: String = "",
      sort: String = "display_name"
  )(implicit c: Option[Connection] = None): List[Project] = {
    if (user.isSuperUser && !onlyOwned) {
      this.list(limit, offset, onlyEnabled, searchString, sort)
    } else {
      this.withMRConnection { implicit c =>
        if (user.groups.isEmpty && !user.isSuperUser) {
          List.empty
        } else {
          var permissionMatch = "p.owner_id = {osmId}"
          if (!onlyOwned) {
            permissionMatch = permissionMatch + " OR g.id IN ({ids})"
          }

          val query =
            s"""SELECT distinct p.*, LOWER(p.name), LOWER(p.display_name)
                FROM projects p
                INNER JOIN groups g on g.project_id = p.id
                WHERE ${permissionMatch}
                ${this.searchField("p.name")} ${this.enabled(onlyEnabled)}
                ${this.order(
              orderColumn = Some(sort),
              orderDirection = "ASC",
              nameFix = true,
              ignoreCase = (sort == "p.name" || sort == "p.display_name")
            )}
                LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""

          SQL(query)
            .on(
              Symbol("ss")     -> this.search(searchString),
              Symbol("offset") -> ToParameterValue.apply[Int].apply(offset),
              Symbol("osmId")  -> user.osmProfile.id,
              Symbol("ids")    -> user.groups.map(_.id)
            )
            .as(this.parser.*)
        }
      }
    }
  }

  /**
    * Lists the children of the parent
    *
    * @param limit  limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param id     The parent ID
    * @return A list of children objects
    */
  override def listChildren(
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0,
      onlyEnabled: Boolean = false,
      searchString: String = "",
      orderColumn: String = "challenges.id",
      orderDirection: String = "ASC"
  )(implicit id: Long, c: Option[Connection] = None): List[Challenge] = {
    this.retrieveById match {
      case Some(project) =>
        this.withMRConnection { implicit c =>
          val query =
            s"""SELECT challenges.${this.childColumns}, array_remove(array_agg(vp.project_id), NULL) AS virtual_parent_ids FROM challenges
                          LEFT OUTER JOIN virtual_project_challenges vp ON challenges.id = vp.challenge_id
                          WHERE
                          (challenges.id IN (SELECT vp2.challenge_id FROM virtual_project_challenges vp2
                                            WHERE vp2.project_id = {id})
                            OR challenges.parent_id = {id})
                          ${this.enabled(onlyEnabled)}
                          ${this.searchField("name")}
                          GROUP BY challenges.id
                          ${this.order(Some(orderColumn), orderDirection)}
                          LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""

          SQL(query)
            .on(
              Symbol("ss")     -> this.search(searchString),
              Symbol("id")     -> ToParameterValue.apply[Long](p = keyToStatement).apply(id),
              Symbol("offset") -> offset
            )
            .as(this.childDAL.withVirtualParentParser.*)
        }
      case _ =>
        throw new NotFoundException("No project found with id $id")
    }
  }

  /**
    * Gets all the counts of challenges and surveys for each available project
    *
    * @param user         The user executing the request, will limit the response to only accesible projects
    * @param limit        To limit the number of project counts to return
    * @param offset       Paging starting at 0
    * @param onlyEnabled  Whether to list only enabled projects
    * @param searchString To search by project name
    * @param c            implicit connection, if not supplied will open new connection
    * @return A map of project ids to tuple with number of challenge and survey children for the project
    */
  def getChildrenCounts(
      user: User,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0,
      onlyEnabled: Boolean = false,
      searchString: String = ""
  )(implicit c: Option[Connection] = None): Map[Long, (Int, Int)] = {
    this.withMRConnection { implicit c =>
      val parser = for {
        id         <- long("id")
        challenges <- int("challenges")
        surveys    <- int("surveys")
      } yield (id, challenges, surveys)
      val query =
        s"""SELECT p.id,
                      SUM(CASE c.challenge_type WHEN 1 THEN 1 ELSE 0 END) AS challenges,
                      SUM(CASE c.challenge_type WHEN 4 THEN 1 ELSE 0 END) AS surveys
                    FROM projects p
                    INNER JOIN groups g ON g.project_id = p.id
                    INNER JOIN challenges c ON c.parent_id = p.id
                    WHERE (1=${if (user.isSuperUser) {
          1
        } else {
          0
        }} OR g.id IN ({ids}))
                     AND c.deleted = false and p.deleted = false
                    ${this.searchField("p.name")} ${this.enabled(onlyEnabled, "p")}
                    GROUP BY p.id
                    LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query)
        .on(Symbol("ss") -> this.search(searchString), Symbol("ids") -> user.groups.map(_.id))
        .as(parser.*)
        .map(v => v._1 -> (v._2, v._3))
        .toMap
    }
  }

  /**
    * Retrieves the clustered json points for a searched set of challenges
    *
    * @param params search parameters
    * @return
    */
  def getSearchedClusteredPoints(
      params: SearchParameters,
      limit: Int = 0,
      offset: Int = 0,
      featured: Boolean = false
  )(implicit c: Option[Connection] = None): List[ClusteredPoint] = {
    this.withMRConnection { implicit c =>
      val parameters = new ListBuffer[NamedParameter]()
      // the named parameter for the challenge name
      parameters += (Symbol("cs") -> this.search(
        params.challengeParams.challengeSearch.getOrElse("")
      ))
      parameters += (Symbol("ps") -> this.search(params.projectSearch.getOrElse("")))
      // search by tags if any
      val challengeTags =
        if (params.challengeParams.challengeTags.isDefined && params.challengeParams.challengeTags.get.nonEmpty) {
          val tags = params.challengeParams.challengeTags.get.zipWithIndex.map {
            case (v, i) =>
              parameters += (s"tag_$i" -> this.search(v))
              s"t.name LIKE {tag_$i}"
          }
          (
            """
            |INNER JOIN tags_on_challenges tc ON tc.challenge_id = c.id
            |INNER JOIN tags t ON t.id = tc.tag_id
          """.stripMargin,
            s"AND ${tags.mkString(" OR ")}"
          )
        } else {
          ("", "")
        }
      // search by location bounding box
      val locationClause = params.location match {
        case Some(l) =>
          s"AND c.location @ ST_MakeEnvelope(${l.left}, ${l.bottom}, ${l.right}, ${l.top}, 4326)"
        case None => ""
      }
      val query =
        s"""
          SELECT c.id, u.osm_id, u.name, c.name, c.parent_id, p.name, c.blurb,
                  ST_AsGeoJSON(c.location) AS location, ST_AsGeoJSON(c.bounding) AS bounding,
                  c.difficulty, c.challenge_type, c.last_updated
          FROM challenges c
          INNER JOIN projects p ON p.id = c.parent_id
          INNER JOIN users u ON u.osm_id = c.owner_id
          ${challengeTags._1}
          WHERE c.location IS NOT NULL AND EXISTS (
            SELECT id FROM tasks
            WHERE parent_id = c.id AND status IN (${Task.STATUS_CREATED},${Task.STATUS_SKIPPED},${Task.STATUS_TOO_HARD}) LIMIT 1)
          ${if (featured) {
          " AND c.featured = true"
        } else {
          ""
        }}
          ${this.searchField("c.name", "cs")}
          ${this.searchField("p.name", "ps")}
          ${this.enabled(params.enabledChallenge, "c")} ${this.enabled(params.enabledProject, "p")}
          AND c.deleted = false and p.deleted = false
          ${params.getProjectIds match {
          case Some(v) if v.nonEmpty => s" AND c.parent_id IN (${v.mkString(",")})"
          case None                  => ""
        }}
          $locationClause
          ${challengeTags._2}
          LIMIT ${sqlLimit(limit)} OFFSET $offset
         """
      this.sqlWithParameters(query, parameters).as(this.pointParser.*)
    }
  }

  /**
    * Retrieves the clustered json for challenges
    *
    * @param projectId    The project id for the requested challenges, if None, then retrieve all challenges
    * @param challengeIds A list of challengeId's that you can filter the result by
    * @param enabledOnly  Show only the enabled challenges
    * @return A list of ClusteredPoint objects
    */
  def getClusteredPoints(
      projectId: Option[Long] = None,
      challengeIds: List[Long] = List.empty,
      enabledOnly: Boolean = true
  )(implicit c: Option[Connection] = None): List[ClusteredPoint] = {
    this.withMRConnection { implicit c =>
      SQL"""SELECT c.id, u.osm_id, u.name, c.name, c.parent_id, p.name, c.blurb,
                    ST_AsGeoJSON(c.location) AS location, ST_AsGeoJSON(c.bounding) AS bounding,
                    c.difficulty, c.challenge_type, c.last_updated
              FROM challenges c
              INNER JOIN projects p ON p.id = c.parent_id
              INNER JOIN users u ON u.osm_id = c.owner_id
              WHERE c.location IS NOT NULL AND EXISTS (
                SELECT id FROM tasks
                WHERE parent_id = c.id AND status IN (${Task.STATUS_CREATED},${Task.STATUS_SKIPPED},${Task.STATUS_TOO_HARD}) LIMIT 1)
              #${this.enabled(enabledOnly, "c")} #${this.enabled(enabledOnly, "p")}
              AND c.deleted = false AND p.deleted = false
              #${if (projectId.isDefined) {
        s" AND c.parent_id = ${projectId.get}"
      } else {
        ""
      }}
              #${if (challengeIds.nonEmpty) {
        s" AND c.id IN (${challengeIds.mkString(",")})"
      } else {
        ""
      }}
        """.as(this.pointParser.*)
    }
  }

  /**
    * Clears the project cache
    *
    * @param id If id is supplied will only remove the project with that id
    */
  def clearCache(id: Long = -1): Unit = {
    if (id > -1) {
      this.cacheManager.cache.remove(id)
    } else {
      this.cacheManager.clearCaches
    }
  }
}
