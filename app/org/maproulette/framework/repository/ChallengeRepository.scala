/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm.SqlParser.get
import anorm.{RowParser, ~}
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.framework.model._
import org.maproulette.framework.psql.Query
import play.api.db.Database

/**
  * The challenge repository handles all the querying with the databases related to challenge objects
  *
  * @author mcuthbert
  */
@Singleton
class ChallengeRepository @Inject() (override val db: Database) extends RepositoryMixin {

  /**
    * Query function that allows a user to build their own query against the Challenge table
    *
    * @param query The query to execute
    * @param c An implicit connection
    * @return A list of returned Challenges
    */
  def query(query: Query)(implicit c: Option[Connection] = None): List[Challenge] = {
    withMRConnection { implicit c =>
      query
        .build(s"SELECT ${ChallengeRepository.standardColumns} FROM challenges")
        .as(ChallengeRepository.parser.*)
    }
  }
}

object ChallengeRepository {
  val standardColumns: String =
    "*, ST_AsGeoJSON(location) AS locationJSON, ST_AsGeoJSON(bounding) AS boundingJSON"

  /**
    * The row parser for Anorm to enable the object to be read from the retrieved row directly
    * to the Challenge object.
    */
  val parser: RowParser[Challenge] = {
    get[Long]("challenges.id") ~
      get[String]("challenges.name") ~
      get[DateTime]("challenges.created") ~
      get[DateTime]("challenges.modified") ~
      get[Option[String]]("challenges.description") ~
      get[Option[String]]("challenges.info_link") ~
      get[Long]("challenges.owner_id") ~
      get[Long]("challenges.parent_id") ~
      get[String]("challenges.instruction") ~
      get[Int]("challenges.difficulty") ~
      get[Option[String]]("challenges.blurb") ~
      get[Boolean]("challenges.enabled") ~
      get[Boolean]("challenges.featured") ~
      get[Boolean]("challenges.has_suggested_fixes") ~
      get[Option[Int]]("challenges.popularity") ~
      get[Option[String]]("challenges.checkin_comment") ~
      get[Option[String]]("challenges.checkin_source") ~
      get[Option[String]]("challenges.overpass_ql") ~
      get[Option[String]]("challenges.remote_geo_json") ~
      get[Option[Int]]("challenges.status") ~
      get[Option[String]]("challenges.status_message") ~
      get[Int]("challenges.default_priority") ~
      get[Option[String]]("challenges.high_priority_rule") ~
      get[Option[String]]("challenges.medium_priority_rule") ~
      get[Option[String]]("challenges.low_priority_rule") ~
      get[Int]("challenges.default_zoom") ~
      get[Int]("challenges.min_zoom") ~
      get[Int]("challenges.max_zoom") ~
      get[Option[Int]]("challenges.default_basemap") ~
      get[Option[String]]("challenges.default_basemap_id") ~
      get[Option[String]]("challenges.custom_basemap") ~
      get[Boolean]("challenges.updatetasks") ~
      get[Option[String]]("challenges.exportable_properties") ~
      get[Option[String]]("challenges.osm_id_property") ~
      get[Option[String]]("challenges.preferred_tags") ~
      get[Option[String]]("challenges.task_styles") ~
      get[Option[DateTime]]("challenges.last_task_refresh") ~
      get[Option[DateTime]]("challenges.data_origin_date") ~
      get[Option[String]]("locationJSON") ~
      get[Option[String]]("boundingJSON") ~
      get[Boolean]("deleted") map {
      case id ~ name ~ created ~ modified ~ description ~ infoLink ~ ownerId ~ parentId ~ instruction ~
            difficulty ~ blurb ~ enabled ~ featured ~ hasSuggestedFixes ~ popularity ~ checkin_comment ~
            checkin_source ~ overpassql ~ remoteGeoJson ~ status ~ statusMessage ~ defaultPriority ~ highPriorityRule ~
            mediumPriorityRule ~ lowPriorityRule ~ defaultZoom ~ minZoom ~ maxZoom ~ defaultBasemap ~ defaultBasemapId ~
            customBasemap ~ updateTasks ~ exportableProperties ~ osmIdProperty ~ preferredTags ~ taskStyles ~ lastTaskRefresh ~
            dataOriginDate ~ location ~ bounding ~ deleted =>
        val hpr = highPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r                                                                => r
        }
        val mpr = mediumPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r                                                                => r
        }
        val lpr = lowPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r                                                                => r
        }
        new Challenge(
          id,
          name,
          created,
          modified,
          description,
          deleted,
          infoLink,
          ChallengeGeneral(
            ownerId,
            parentId,
            instruction,
            difficulty,
            blurb,
            enabled,
            featured,
            hasSuggestedFixes,
            popularity,
            checkin_comment.getOrElse(""),
            checkin_source.getOrElse(""),
            None
          ),
          ChallengeCreation(overpassql, remoteGeoJson),
          ChallengePriority(defaultPriority, hpr, mpr, lpr),
          ChallengeExtra(
            defaultZoom,
            minZoom,
            maxZoom,
            defaultBasemap,
            defaultBasemapId,
            customBasemap,
            updateTasks,
            exportableProperties,
            osmIdProperty,
            preferredTags,
            taskStyles
          ),
          status,
          statusMessage,
          lastTaskRefresh,
          dataOriginDate,
          location,
          bounding
        )
    }
  }
}
