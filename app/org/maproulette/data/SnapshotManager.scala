// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.data

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.models.{Task, Challenge}
import org.maproulette.models.utils.{AND, DALHelper, WHERE}
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SearchParameters
import org.maproulette.utils.BoundingBoxFinder
import org.maproulette.exception.NotFoundException
import play.api.Application
import play.api.db.Database

case class ReviewActions(
    total: Int,
    requested: Int,
    approved: Int,
    rejected: Int,
    assisted: Int,
    disputed: Int
)

case class Snapshot(
    id: Long,
    itemId: Long,
    typeId: Long,
    name: String,
    status: Option[Int],
    created: DateTime,
    actions: Option[ActionSummary] = None,
    priorityActions: Option[Map[String, ActionSummary]] = None,
    reviewActions: Option[ReviewActions] = None
)

/**
  * @author krotstan
  */
@Singleton
class SnapshotManager @Inject() (
    config: Config,
    db: Database,
    challengeDAL: ChallengeDAL,
    boundingBoxFinder: BoundingBoxFinder
)(implicit application: Application)
    extends DataManager(config, db, boundingBoxFinder) {

  val snapshotBriefParser: RowParser[Snapshot] = {
    get[Long]("id") ~
      get[Long]("challenge_id") ~
      get[String]("challenge_name") ~
      get[Option[Int]]("challenge_status") ~
      get[DateTime]("created") ~
      get[Long]("type") map {
      case id ~ challengeId ~ name ~ status ~ created ~ typeId =>
        Snapshot(id, challengeId, typeId, name, status, created)
    }
  }

  val snapshotParser = for {
    id                  <- get[Long]("id")
    challengeId         <- get[Long]("challenge_id")
    name                <- get[String]("challenge_name")
    status              <- get[Option[Int]]("challenge_status")
    created             <- get[DateTime]("created")
    typeId              <- get[Long]("type_id")
    available           <- int("available")
    fixed               <- int("fixed")
    falsePositive       <- int("false_positive")
    skipped             <- int("skipped")
    deleted             <- int("deleted")
    alreadyFixed        <- int("already_fixed")
    tooHard             <- int("too_hard")
    answered            <- int("answered")
    validated           <- int("validated")
    disabled            <- int("disabled")
    availableLow        <- int("availableLow")
    fixedLow            <- int("fixedLow")
    falsePositiveLow    <- int("false_positiveLow")
    skippedLow          <- int("skippedLow")
    deletedLow          <- int("deletedLow")
    alreadyFixedLow     <- int("already_fixedLow")
    tooHardLow          <- int("too_hardLow")
    answeredLow         <- int("answeredLow")
    validatedLow        <- int("validatedLow")
    disabledLow         <- int("disabledLow")
    availableMedium     <- int("availableMedium")
    fixedMedium         <- int("fixedMedium")
    falsePositiveMedium <- int("false_positiveMedium")
    skippedMedium       <- int("skippedMedium")
    deletedMedium       <- int("deletedMedium")
    alreadyFixedMedium  <- int("already_fixedMedium")
    tooHardMedium       <- int("too_hardMedium")
    answeredMedium      <- int("answeredMedium")
    validatedMedium     <- int("validatedMedium")
    disabledMedium      <- int("disabledMedium")
    availableHigh       <- int("availableHigh")
    fixedHigh           <- int("fixedHigh")
    falsePositiveHigh   <- int("false_positiveHigh")
    skippedHigh         <- int("skippedHigh")
    deletedHigh         <- int("deletedHigh")
    alreadyFixedHigh    <- int("already_fixedHigh")
    tooHardHigh         <- int("too_hardHigh")
    answeredHigh        <- int("answeredHigh")
    validatedHigh       <- int("validatedHigh")
    disabledHigh        <- int("disabledHigh")
    reviewRequested     <- int("requested")
    reviewApproved      <- int("approved")
    reviewRejected      <- int("rejected")
    reviewAssisted      <- int("assisted")
    reviewDisputed      <- int("disputed")
  } yield Snapshot(
    id,
    challengeId,
    typeId,
    name,
    status,
    created,
    Some(
      ActionSummary(
        (available + fixed + falsePositive + skipped + deleted +
          alreadyFixed + tooHard + answered + validated + disabled),
        available,
        fixed,
        falsePositive,
        skipped,
        deleted,
        alreadyFixed,
        tooHard,
        answered,
        validated,
        disabled
      )
    ),
    Some(
      Map(
        Challenge.PRIORITY_LOW.toString -> ActionSummary(
          (availableLow + fixedLow + falsePositiveLow + skippedLow + deletedLow +
            alreadyFixedLow + tooHardLow + answeredLow + validatedLow + disabledLow),
          availableLow,
          fixedLow,
          falsePositiveLow,
          skippedLow,
          deletedLow,
          alreadyFixedLow,
          tooHardLow,
          answeredLow,
          validatedLow,
          disabledLow
        ),
        Challenge.PRIORITY_MEDIUM.toString -> ActionSummary(
          (availableMedium + fixedMedium + falsePositiveMedium + skippedMedium + deletedMedium +
            alreadyFixedMedium + tooHardMedium + answeredMedium + validatedMedium + disabledMedium),
          availableMedium,
          fixedMedium,
          falsePositiveMedium,
          skippedMedium,
          deletedMedium,
          alreadyFixedMedium,
          tooHardMedium,
          answeredMedium,
          validatedMedium,
          disabledMedium
        ),
        Challenge.PRIORITY_HIGH.toString -> ActionSummary(
          (availableHigh + fixedHigh + falsePositiveHigh + skippedHigh + deletedHigh +
            alreadyFixedHigh + tooHardHigh + answeredHigh + validatedHigh + disabledHigh),
          availableHigh,
          fixedHigh,
          falsePositiveHigh,
          skippedHigh,
          deletedHigh,
          alreadyFixedHigh,
          tooHardHigh,
          answeredHigh,
          validatedHigh,
          disabledHigh
        )
      )
    ),
    Some(
      ReviewActions(
        (reviewRequested + reviewApproved + reviewRejected + reviewAssisted + reviewDisputed),
        reviewRequested,
        reviewApproved,
        reviewRejected,
        reviewAssisted,
        reviewDisputed
      )
    )
  )

  def getChallengeSnapshotList(challengeId: Long): List[Snapshot] = {
    db.withConnection { implicit c =>
      val query = s"""
        SELECT id, challenge_id, challenge_name, challenge_status, created,
          (SELECT type_id from completion_snapshots where id = completion_snapshot_id) as type
        FROM challenge_snapshots WHERE challenge_id = ${challengeId}
      """
      SQL(query).as(snapshotBriefParser.*)
    }
  }

  def getAllChallengeSnapshots(challengeId: Long): List[Snapshot] = {
    db.withConnection { implicit c =>
      val query = s"""
        ${_buildSnapshotQuery()}
        WHERE snap.challenge_id = ${challengeId}
      """

      SQL(query).as(snapshotParser.*)
    }
  }

  def getChallengeSnapshot(snapshotId: Long): Snapshot = {
    db.withConnection { implicit c =>
      val query = s"""
        ${_buildSnapshotQuery()}
        WHERE snap.id = ${snapshotId}
      """

      println(query)
      SQL(query).as(snapshotParser.*).head
    }
  }

  def recordChallengeSnapshot(challengeId: Long, manual: Boolean = true): Long = {
    db.withConnection { implicit c =>
      val challenge = challengeDAL.retrieveById(challengeId) match {
        case Some(c) => c
        case None =>
          throw new NotFoundException(
            s"Could not record snapshot, no challenge with id $challengeId found."
          )
      }

      val result = this.getChallengeSummary(challengeId = Some(challengeId)) match {
        case l if (l.size > 0) => l.head
        case _                 => ChallengeSummary(challengeId, "", ActionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
      }

      val resultLow =
        this.getChallengeSummary(
          challengeId = Some(challengeId),
          priority = Some(List(Challenge.PRIORITY_LOW))
        ) match {
          case l if (l.size > 0) => l.head
          case _ =>
            ChallengeSummary(result.id, result.name, ActionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        }

      val resultMedium =
        this.getChallengeSummary(
          challengeId = Some(challengeId),
          priority = Some(List(Challenge.PRIORITY_MEDIUM))
        ) match {
          case l if (l.size > 0) => l.head
          case _ =>
            ChallengeSummary(result.id, result.name, ActionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        }

      val resultHigh =
        this.getChallengeSummary(
          challengeId = Some(challengeId),
          priority = Some(List(Challenge.PRIORITY_HIGH))
        ) match {
          case l if (l.size > 0) => l.head
          case _ =>
            ChallengeSummary(result.id, result.name, ActionSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        }

      val allPriorities = _recordCompletionSnapshot(result, None)
      val lowPriorities = _recordCompletionSnapshot(resultLow, Some(Challenge.PRIORITY_LOW))
      val mediumPriorities =
        _recordCompletionSnapshot(resultMedium, Some(Challenge.PRIORITY_MEDIUM))
      val highPriorities = _recordCompletionSnapshot(resultHigh, Some(Challenge.PRIORITY_HIGH))

      val reviewQuery      = s"""
        INSERT INTO review_snapshots
         (type_id, item_id, requested, approved, rejected, assisted, disputed)
        SELECT ${Actions.ITEM_TYPE_CHALLENGE}, ${challengeId},
            COUNT(review_status) FILTER (where review_status = 0) AS requested,
            COUNT(review_status) FILTER (where review_status = 1) AS approved,
            COUNT(review_status) FILTER (where review_status = 2) AS rejected,
            COUNT(review_status) FILTER (where review_status = 3) AS assisted,
            COUNT(review_status) FILTER (where review_status = 4) AS disputed
           FROM task_review
            INNER JOIN tasks t ON t.id = task_review.task_id
            INNER JOIN challenges c ON c.id = t.parent_id
           WHERE c.id = ${challengeId}
      """
      val reviewSnapshotId = SQL(reviewQuery).executeInsert().map(id => id)

      val query = s"""
        INSERT INTO challenge_snapshots (
          challenge_id,
          challenge_name,
          challenge_status,
          completion_snapshot_id,
          low_completion_snapshot_id,
          medium_completion_snapshot_id,
          high_completion_snapshot_id,
          review_snapshot_id,
          manual)
        VALUES
          ({id}, {name}, {status}, {all}, {low}, {medium}, {high}, {review}, {manual})
      """
      SQL(query)
        .on(
          Symbol("id")     -> challengeId,
          Symbol("name")   -> result.name,
          Symbol("status") -> challenge.status,
          Symbol("all")    -> allPriorities,
          Symbol("low")    -> lowPriorities,
          Symbol("medium") -> mediumPriorities,
          Symbol("high")   -> highPriorities,
          Symbol("review") -> reviewSnapshotId,
          Symbol("manual") -> manual
        )
        .executeInsert()
        .map(id => id)
        .head
    }
  }

  private def _recordCompletionSnapshot(
      summary: ChallengeSummary,
      priority: Option[Integer]
  ): Option[Long] = {
    db.withConnection { implicit c =>
      val query = s"""
        INSERT INTO completion_snapshots
         (type_id,
          item_id,
          priority,
          available,
          fixed,
          false_positive,
          skipped,
          deleted,
          already_fixed,
          too_hard,
          answered,
          validated,
          disabled)
        VALUES (${Actions.ITEM_TYPE_CHALLENGE}, {id}, {priority}, {available}, {fixed},
                {false_positive}, {skipped}, {deleted}, {already_fixed}, {too_hard},
                {answered}, {validated}, {disabled})
      """
      SQL(query)
        .on(
          Symbol("id")             -> summary.id,
          Symbol("priority")       -> priority,
          Symbol("available")      -> summary.actions.available,
          Symbol("fixed")          -> summary.actions.fixed,
          Symbol("false_positive") -> summary.actions.falsePositive,
          Symbol("skipped")        -> summary.actions.skipped,
          Symbol("deleted")        -> summary.actions.deleted,
          Symbol("already_fixed")  -> summary.actions.alreadyFixed,
          Symbol("too_hard")       -> summary.actions.tooHard,
          Symbol("answered")       -> summary.actions.answered,
          Symbol("validated")      -> summary.actions.validated,
          Symbol("disabled")       -> summary.actions.disabled
        )
        .executeInsert()
        .map(id => id)
    }
  }

  private def _buildSnapshotQuery(): String = {
    val cols = new StringBuilder()
    List("", "Low", "Medium", "High").foreach(p => {
      cols ++=
        s"""
         cs${p}.available as available${p},
         cs${p}.fixed as fixed${p},
         cs${p}.false_positive as false_positive${p},
         cs${p}.skipped as skipped${p},
         cs${p}.deleted as deleted${p},
         cs${p}.already_fixed as already_fixed${p},
         cs${p}.too_hard as too_hard${p},
         cs${p}.answered as answered${p},
         cs${p}.validated as validated${p},
         cs${p}.disabled as disabled${p},
      """
    })

    s"""
      SELECT snap.id as id, challenge_id, challenge_name, challenge_status, snap.created,
             cs.type_id as type_id, $cols review_snapshots.*
      FROM challenge_snapshots snap
      INNER JOIN completion_snapshots cs ON cs.id = completion_snapshot_id
      INNER JOIN completion_snapshots csLow ON csLow.id = low_completion_snapshot_id
      INNER JOIN completion_snapshots csMedium ON csMedium.id = medium_completion_snapshot_id
      INNER JOIN completion_snapshots csHigh ON csHigh.id = high_completion_snapshot_id
      INNER JOIN review_snapshots ON review_snapshots.id = review_snapshot_id
    """
  }
}
