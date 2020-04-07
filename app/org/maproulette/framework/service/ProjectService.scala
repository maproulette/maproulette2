/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import java.sql.Connection

import javax.inject.{Inject, Singleton}
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.framework.model._
import org.maproulette.framework.psql._
import org.maproulette.framework.psql.filter._
import org.maproulette.framework.repository.{ChallengeRepository, ProjectRepository}
import org.maproulette.models.ClusteredPoint
import org.maproulette.permissions.Permission
import org.maproulette.session.SearchParameters
import org.slf4j.LoggerFactory
import play.api.libs.json.JsValue

/**
  * The project service handles all the business logic for the Project objects
  *
  * @author mcuthbert
  */
@Singleton
class ProjectService @Inject() (
    repository: ProjectRepository,
    config: Config,
    groupService: GroupService,
    challengeService: ChallengeService,
    permission: Permission
) extends ServiceMixin[Project] {
  // manager for the cache of the projects
  val cacheManager     = new CacheManager[Long, Project](config, Config.CACHE_ID_PROJECTS)
  protected val logger = LoggerFactory.getLogger(this.getClass)

  def children(
      id: Long,
      searchString: String = "",
      onlyEnabled: Boolean = false,
      paging: Paging = Paging(Config.DEFAULT_LIST_SIZE),
      order: Order = Order.simple(s"challenges.${Challenge.FIELD_ID}")
  ): List[Challenge] = {
    // I am in two minds about handling it this way. Firstly one of the tenants of splitting up the
    // work between service and repository layer is that we limit the SQL code to the repository and
    // the business logic to the service. For simple queries this works well as the Query object
    // will handle it cleanly. The difficult comes when you are joining other tables, then you need
    // to inject sql into the Query object. I think generally speaking we should move the code to
    // the repository and have a specialized function for what you are doing, but I am not sure in
    // this case.
    this.challengeService.query(
      Query(
        Filter(
          AND(),
          FilterGroup(
            OR(),
            BaseParameter(s"challenges.${Challenge.FIELD_PARENT_ID}", id),
            SubQueryFilter(
              s"challenges.${Challenge.FIELD_ID}",
              Query.simple(
                List(BaseParameter(s"vp2.${VirtualProject.FIELD_PROJECT_ID}", id)),
                "SELECT vp2.challenge_id FROM virtual_project_challenges vp2"
              )
            )
          ),
          FilterGroup(
            AND(),
            FilterParameter
              .conditional(Challenge.FIELD_ENABLED, onlyEnabled, includeOnlyIfTrue = onlyEnabled),
            FilterParameter.conditional(
              Challenge.FIELD_NAME,
              SQLUtils.search(searchString),
              Operator.ILIKE,
              includeOnlyIfTrue = searchString.nonEmpty
            )
          )
        ),
        s"""SELECT challenges.${ChallengeRepository.standardColumns}, ARRAY_REMOVE(ARRAY_AGG(vp.project_id), NULL) AS virtual_parent_ids FROM challenges
          | LEFT OUTER JOIN virtual_project_challenges vp ON challenges.id = vp.challenge_id""".stripMargin,
        paging,
        order,
        Grouping(s"challenges.${Challenge.FIELD_ID}")
      )
    )
  }

  /**
    * Inserts a project into the database
    *
    * @param project The project to insert into the database
    * @param user The user that is trying to create the project
    * @return The new project with the new project ID
    */
  def create(project: Project, user: User): Project = {
    //permissions don't need to be checked, anyone can create a project
    //this.permission.hasObjectWriteAccess(project, user)
    this.cacheManager.withOptionCaching { () =>
      // only super users can feature a project
      val featured = project.featured && user.isSuperUser
      // first create the project
      val newProject = this.repository.create(project.copy(featured = featured))

      val adminGroup = this.groupService.create(newProject.id, Group.TYPE_ADMIN, User.superUser)
      val writeGroup =
        this.groupService.create(newProject.id, Group.TYPE_WRITE_ACCESS, User.superUser)
      val readGroup = this.groupService.create(newProject.id, Group.TYPE_READ_ONLY, User.superUser)
      this.groupService.addUserToGroup(project.owner, adminGroup, User.superUser)
      Some(newProject.copy(groups = List(adminGroup, writeGroup, readGroup)))
    }.head
  }

  /**
    * Gets a list of all projects that are specific managed by the supplied user
    *
    * @param user The user executing the request
    * @param paging paging object to handle paging in response
    * @param onlyEnabled Only get the managed projects if they are enabled, defaults to false
    * @param onlyOwned Only get the managed projects if they are owned, defaults to false
    * @param searchString The string to search the manage projects by, defaults to empty string and will return all managed projects
    * @param order The ordering for the resultant managed projects
    * @return A list of projects managed by the user
    */
  def getManagedProjects(
      user: User,
      paging: Paging = Paging(),
      onlyEnabled: Boolean = false,
      onlyOwned: Boolean = false,
      searchString: String = "",
      order: Order = Order(List("display_name"), Order.ASC)
  )(implicit c: Option[Connection] = None): List[Project] = {
    if (user.isSuperUser && !onlyOwned) {
      this.find(searchString, paging, onlyEnabled, order)
    } else {
      if (user.groups.isEmpty && !user.isSuperUser) {
        List.empty
      } else {
        // TODO No sql should exist in the service layer
        val customQuery = s"""SELECT distinct p.*, LOWER(p.name), LOWER(p.display_name)
                FROM projects p
                INNER JOIN groups g on g.project_id = p.id"""
        val permissionFilterGroup =
          FilterGroup(
            OR(),
            BaseParameter(s"p.${Project.FIELD_OWNER}", user.osmProfile.id),
            FilterParameter.conditional(
              s"g.${Group.FIELD_ID}",
              user.groups.map(_.id),
              Operator.IN,
              includeOnlyIfTrue = !onlyOwned
            )
          )
        val baseFilterGroup = FilterGroup(
          AND(),
          BaseParameter(s"p.${Project.FIELD_NAME}", SQLUtils.search(searchString), Operator.LIKE),
          FilterParameter.conditional(
            Project.FIELD_ENABLED,
            onlyEnabled,
            includeOnlyIfTrue = onlyEnabled
          )
        )
        this.query(
          Query(
            Filter(AND(), permissionFilterGroup, baseFilterGroup),
            customQuery,
            paging,
            order
          )
        )
      }
    }
  }

  /**
    * Given some Query it will retrieve a list of projects based on the criteria
    *
    * @param search The search string for the name or the display name
    * @param paging paging object to handle paging in response
    * @param order The ordering for the query
    * @return
    */
  def find(
      search: String,
      paging: Paging = Paging(),
      onlyEnabled: Boolean = true,
      order: Order = Order()
  ): List[Project] = {
    val query = Query(
      Filter(
        AND(),
        FilterGroup(
          OR(),
          BaseParameter(
            Project.FIELD_NAME,
            SQLUtils.search(search),
            Operator.ILIKE
          ),
          BaseParameter(
            Project.FIELD_DISPLAY_NAME,
            SQLUtils.search(search),
            Operator.ILIKE
          ),
          FuzzySearchParameter(
            Project.FIELD_DISPLAY_NAME,
            value = search
          )
        ),
        FilterGroup(
          AND(),
          BaseParameter(Project.FIELD_ENABLED, true)
        )
      ),
      paging = paging,
      order = order
    )
    this.query(query)
  }

  /**
    * Gets the featured projects
    *
    * @param onlyEnabled Only include enabled projects
    * @param paging paging object to handle paging in response
    * @return A json array with the featured projects
    */
  def getFeaturedProjects(
      onlyEnabled: Boolean = true,
      paging: Paging = Paging()
  ): List[Project] = {
    this.query(
      Query.simple(
        List(
          BaseParameter(Project.FIELD_FEATURED, true),
          FilterParameter.conditional(
            Project.FIELD_ENABLED,
            onlyEnabled,
            includeOnlyIfTrue = onlyEnabled
          )
        ),
        paging = paging
      )
    )
  }

  /**
    * Updates a project with the given input JSON
    *
    * @param id The id of the project that is being updated
    * @param updates The JSON updates to apply
    * @param user The user that is updating the project
    * @return The newly updated project object
    */
  def update(id: Long, updates: JsValue, user: User): Option[Project] = {
    this.cacheManager
      .withUpdatingCache(id => retrieve(id)) { implicit cachedItem =>
        this.permission.hasObjectWriteAccess(cachedItem, user)
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val displayName =
          (updates \ "displayName").asOpt[String].getOrElse(cachedItem.displayName.getOrElse(""))
        val owner = (updates \ "ownerId").asOpt[Long].getOrElse(cachedItem.owner)
        val description =
          (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val enabled = (updates \ "enabled").asOpt[Boolean] match {
          case Some(_) if !user.isSuperUser && !user.adminForProject(id) =>
            logger.warn(
              s"User [${user.name} - ${user.id}] is not a super user and cannot enable or disable projects"
            )
            cachedItem.enabled
          case Some(e) => e
          case None    => cachedItem.enabled
        }
        val featured = (updates \ "featured").asOpt[Boolean] match {
          case Some(_) if !user.isSuperUser =>
            logger.warn(
              s"User [${user.name} - ${user.id}] is not a super user and cannot feature projects"
            )
            cachedItem.featured
          case Some(f) => f
          case None    => cachedItem.featured
        }

        this.repository.update(
          Project(
            id = id,
            owner = owner,
            name = name,
            displayName = Some(displayName),
            description = Some(description),
            enabled = enabled,
            featured = featured
          )
        )
      }(id = id)
  }

  /**
    * Deletes a project
    *
    * @param id The id of the project
    * @param user The user that is deleting the project
    * @param immediate Whether to delete the project immediately or not
    * @return
    */
  def delete(id: Long, user: User, immediate: Boolean = false): Boolean = {
    this.cacheManager.withDeletingCache(id => retrieve(id)) { implicit deletedItem =>
      this.permission.hasObjectAdminAccess(deletedItem, user)
      this.repository.delete(id, immediate)
      Some(deletedItem)
    }(id = id)
    true
  }

  /**
    * Retrieves a Project based on the given id
    *
    * @param id The id of the project
    * @return An optional Project, None if not found.
    */
  def retrieve(id: Long): Option[Project] = {
    this.cacheManager.withCaching { () =>
      this.repository
        .query(Query.simple(List(BaseParameter(Project.FIELD_ID, id))))
        .headOption
    }(id = id)
  }

  /**
    * Retrieves a Project based on the name
    *
    * @param name The name of the Project
    * @return An optional Project, None if not found.
    */
  def retrieveByName(name: String): Option[Project] = {
    this
      .query(
        Query.simple(
          List(
            BaseParameter(Project.FIELD_NAME, name),
            BaseParameter(Project.FIELD_DISPLAY_NAME, name)
          ),
          key = OR()
        )
      )
      .headOption
  }

  def list(
      ids: List[Long],
      paging: Paging = Paging()
  ): List[Project] = {
    this.query(
      Query.simple(
        List(
          BaseParameter(Project.FIELD_ID, ids, Operator.IN)
        ),
        paging = paging
      )
    )
  }

  def query(query: Query): List[Project] = this.repository.query(query)

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

  /**
    * Retrieves the clustered json points for a searched set of challenges
    *
    * @param params search parameters
    * @return
    */
  def getSearchedClusteredPoints(
      params: SearchParameters,
      paging: Paging = Paging(),
      featured: Boolean = false
  ): List[ClusteredPoint] = {
    this.repository.getSearchedClusteredPoints(params, paging, featured)
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
  ): List[ClusteredPoint] = {
    this.repository.getClusteredPoints(projectId, challengeIds, enabledOnly)
  }
}
