// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.exception.UniqueViolationException
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.{Group, SearchParameters, User}
import org.maproulette.session.dal.UserGroupDAL
import play.api.Logger
import play.api.db.Database
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.ListBuffer

/**
  * Specific functions for the project data access layer
  *
  * @author cuthbertm
  */
@Singleton
class ProjectDAL @Inject() (override val db:Database,
                            childDAL:ChallengeDAL,
                            surveyDAL:SurveyDAL,
                            userGroupDAL: UserGroupDAL,
                            override val permission:Permission)
  extends ParentDAL[Long, Project, Challenge] with OwnerMixin[Project] {

  // manager for the cache of the projects
  override val cacheManager = new CacheManager[Long, Project]
  // table name for projects
  override val tableName: String = "projects"
  // table name for project children, challenges
  override val childTable: String = "challenges"
  // anorm row parser for child as defined by the challenge data access layer
  override val childParser = childDAL.parser
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
      get[Boolean]("projects.deleted") map {
      case id ~ ownerId ~ name ~ created ~ modified ~ description ~ enabled ~ displayName ~ deleted =>
        new Project(id, ownerId, name, created, modified, description, userGroupDAL.getProjectGroups(id, User.superUser), enabled, displayName, deleted)
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
        val coordinates = (locationJSON \ "coordinates").as[List[Double]]
        val point = Point(coordinates(1), coordinates.head)
        val boundingJSON = Json.parse(bounding)
        ClusteredPoint(id, osm_id, username, name, parentId, parentName, point, boundingJSON, blurb.getOrElse(""), modified, difficulty, challengeType, -1, -1)
    }
  }

  /**
    * Inserts a new project object into the database
    *
    * @param project The project to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(project: Project, user:User)(implicit c:Option[Connection]=None): Project = {
    //permissions don't need to be checked, anyone can create a project
    //this.permission.hasObjectWriteAccess(project, user)
    this.cacheManager.withOptionCaching { () =>
      // only super users can enable or disable projects
      val setProject = if (!user.isSuperUser || user.adminForProject(project.id)) {
        Logger.warn(s"User [${user.name} - ${user.id}] is not a super user and cannot enable or disable projects")
        project.copy(enabled = false)
      } else {
        project
      }
      val newProject = this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO projects (name, owner_id, display_name, description, enabled)
              VALUES (${setProject.name}, ${user.osmProfile.id}, ${setProject.displayName}, ${setProject.description}, ${setProject.enabled})
              ON CONFLICT(LOWER(name)) DO NOTHING RETURNING *""".as(parser.*).headOption
      }
      newProject match {
        case Some(proj) =>
          // todo: this should be in the above transaction, but for some reason the fkey won't allow it
          db.withTransaction { implicit c =>
            // Every new project needs to have a admin group created for them
            this.userGroupDAL.createGroup(proj.id, proj.name + "_Admin", Group.TYPE_ADMIN, User.superUser)
            this.userGroupDAL.createGroup(proj.id, proj.name + "_Write", Group.TYPE_WRITE_ACCESS, User.superUser)
            this.userGroupDAL.createGroup(proj.id, proj.name + "_Read", Group.TYPE_READ_ONLY, User.superUser)
            Some(proj)
          }
        case None =>
          throw new UniqueViolationException(s"Project with name ${project.name} already exists in the database.")
      }
    }.get
  }

  /**
    * Updates a project in the database
    *
    * @param updates A json object containing all the fields that are too be updated.
    * @param id The id of the object that you are updating.
    * @return An optional object, it will return None if no object found with a matching id that was supplied.
    */
  override def update(updates:JsValue, user:User)(implicit id:Long, c:Option[Connection]=None): Option[Project] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasObjectWriteAccess(cachedItem, user)
      this.withMRTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val displayName = (updates \ "displayName").asOpt[String].getOrElse(cachedItem.displayName.getOrElse(""))
        val owner = (updates \ "ownerId").asOpt[Long].getOrElse(cachedItem.owner)
        val description = (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val enabled = (updates \ "enabled").asOpt[Boolean] match {
          case Some(e) if !user.isSuperUser && !user.adminForProject(id) =>
            Logger.warn(s"User [${user.name} - ${user.id}] is not a super user and cannot enable or disable projects")
            cachedItem.enabled
          case Some(e) => e
          case None => cachedItem.enabled
        }

        SQL"""UPDATE projects SET name = $name,
              owner_id = $owner,
              display_name = $displayName,
              description = $description,
              enabled = $enabled
              WHERE id = $id RETURNING *""".as(this.parser.*).headOption
      }
    }
  }

  /**
    * Gets a list of all projects that are specific managed by the supplied user
    *
    * @param user The user executing the request
    * @return A list of projects managed by the user
    */
  def listManagedProjects(user:User, limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0, onlyEnabled:Boolean=false,
                          searchString:String="")(implicit c:Option[Connection]=None) : List[Project] = {
    if (user.isSuperUser) {
      this.list(limit, offset, onlyEnabled, searchString)
    } else {
      this.withMRConnection { implicit c =>
        if (user.groups.isEmpty) {
          List.empty
        } else {
          val query =
            s"""SELECT p.* FROM projects p
              INNER JOIN groups g ON g.project_id = p.id
              WHERE g.id IN ({ids}) ${this.searchField("p.name")} ${this.enabled(onlyEnabled)}
              LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
          SQL(query).on('ss -> this.search(searchString), 'offset -> ParameterValue.toParameterValue(offset),
            'ids -> user.groups.map(_.id))
            .as(this.parser.*)
        }
      }
    }
  }

  /**
    * Gets all the counts of challenges and surveys for each available project
    *
    * @param user The user executing the request, will limit the response to only accesible projects
    * @param limit To limit the number of project counts to return
    * @param offset Paging starting at 0
    * @param onlyEnabled Whether to list only enabled projects
    * @param searchString To search by project name
    * @param c implicit connection, if not supplied will open new connection
    * @return A map of project ids to tuple with number of challenge and survey children for the project
    */
  def getChildrenCounts(user:User, limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0, onlyEnabled:Boolean=false,
                        searchString:String="")(implicit c:Option[Connection]=None) : Map[Long, (Int, Int)] = {
    this.withMRConnection { implicit c =>
      val parser = for {
        id <- long("id")
        challenges <- int("challenges")
        surveys <- int("surveys")
      } yield (id, challenges, surveys)
      val query = s"""SELECT p.id,
                      SUM(CASE c.challenge_type WHEN 1 THEN 1 ELSE 0 END) AS challenges,
                      SUM(CASE c.challenge_type WHEN 4 THEN 1 ELSE 0 END) AS surveys
                    FROM projects p
                    INNER JOIN groups g ON g.project_id = p.id
                    INNER JOIN challenges c ON c.parent_id = p.id
                    WHERE (1=${if (user.isSuperUser) { 1 } else { 0 }} OR g.id IN ({ids}))
                     AND c.deleted = false and p.deleted = false
                    ${this.searchField("p.name")} ${this.enabled(onlyEnabled, "p")}
                    GROUP BY p.id
                    LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query).on('ss -> this.search(searchString), 'ids -> user.groups.map(_.id)).as(parser.*)
        .map(v => v._1 -> (v._2, v._3)).toMap
    }
  }

  /**
    * Retrieves the clustered json points for a searched set of challenges
    *
    * @param params search parameters
    * @return
    */
  def getSearchedClusteredPoints(params: SearchParameters, limit:Int=0, offset:Int=0, featured:Boolean=false)
                                (implicit c:Option[Connection]=None) : List[ClusteredPoint] = {
    this.withMRConnection { implicit c =>
      val parameters = new ListBuffer[NamedParameter]()
      // the named parameter for the challenge name
      parameters += ('cs -> this.search(params.challengeSearch.getOrElse("")))
      parameters += ('ps -> this.search(params.projectSearch.getOrElse("")))
      // search by tags if any
      val challengeTags = if (params.challengeTags.isDefined && params.challengeTags.get.nonEmpty) {
        val tags = params.challengeTags.get.zipWithIndex.map{
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
        case Some(l) => s"AND c.location @ ST_MakeEnvelope(${l.left}, ${l.bottom}, ${l.right}, ${l.top}, 4326)"
        case None => ""
      }
      val query = s"""
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
          ${if (featured) { " AND c.featured = true"} else {""}}
          ${this.searchField("c.name", "cs")}
          ${this.searchField("p.name", "ps")}
          ${this.enabled(params.enabledChallenge, "c")} ${this.enabled(params.enabledProject, "p")}
          AND c.deleted = false and p.deleted = false
          ${params.getProjectIds match {
              case Some(v) if v.nonEmpty => s" AND c.parent_id IN (${v.mkString(",")})"
              case None => ""
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
    * @param projectId The project id for the requested challenges, if None, then retrieve all challenges
    * @param challengeIds A list of challengeId's that you can filter the result by
    * @param enabledOnly Show only the enabled challenges
    * @return A list of ClusteredPoint objects
    */
  def getClusteredPoints(projectId:Option[Long]=None, challengeIds:List[Long]=List.empty,
                              enabledOnly:Boolean=true)(implicit c:Option[Connection]=None) : List[ClusteredPoint] = {
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
              #${if(projectId.isDefined) { s" AND c.parent_id = ${projectId.get}"} else { "" }}
              #${if(challengeIds.nonEmpty) { s" AND c.id IN (${challengeIds.mkString(",")})"} else { "" }}
        """.as(this.pointParser.*)
    }
  }
}
