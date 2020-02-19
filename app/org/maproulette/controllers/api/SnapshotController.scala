// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import akka.util.ByteString
import org.maproulette.Config
import org.maproulette.data._
import org.maproulette.models.{Challenge, Task}
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SessionManager
import org.maproulette.utils.Utils
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.json.JodaWrites._
import play.api.http.HttpEntity

/**
  * @author krotstan
  */
class SnapshotController @Inject() (
    sessionManager: SessionManager,
    config: Config,
    actionManager: ActionManager,
    components: ControllerComponents,
    snapshotManager: SnapshotManager
) extends AbstractController(components) {

  implicit val actionWrites           = actionManager.actionItemWrites
  implicit val dateWrites             = Writes.dateWrites("yyyy-MM-dd")
  implicit val dateTimeWrites         = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss")
  implicit val actionSummaryWrites    = Json.writes[ActionSummary]
  implicit val challengeSummaryWrites = Json.writes[ChallengeSummary]
  implicit val stringIntMap: Writes[Map[String, Int]] = new Writes[Map[String, Int]] {
    def writes(map: Map[String, Int]): JsValue =
      Json.obj(map.map {
        case (s, i) =>
          val ret: (String, JsValueWrapper) = s.toString -> JsNumber(i)
          ret
      }.toSeq: _*)
  }
  implicit val snapshotWrites     = Json.writes[ReviewActions]
  implicit val reviewActionWrites = Json.writes[Snapshot]

  def recordChallengeSnapshot(challengeId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        Ok(Json.toJson(snapshotManager.recordChallengeSnapshot(challengeId)))
      }
  }

  def getChallengeSnapshotList(challengeId: Long, includeAllData: Boolean): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        if (includeAllData) {
          Ok(Json.toJson(snapshotManager.getAllChallengeSnapshots(challengeId)))
        } else {
          Ok(Json.toJson(snapshotManager.getChallengeSnapshotList(challengeId)))
        }
      }
    }

  def getChallengeSnapshot(snapshotId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.userAwareRequest { implicit user =>
        Ok(Json.toJson(snapshotManager.getChallengeSnapshot(snapshotId)))
      }
  }

  def exportChallengeSnapshots(challengeId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        Result(
          header = ResponseHeader(
            OK,
            Map(
              CONTENT_DISPOSITION -> s"attachment; filename=challenge_${challengeId}_snapshots.csv"
            )
          ),
          body = HttpEntity.Strict(
            ByteString(
              "Snapshot_Time,Snapshot_ID,Challenge_ID,Challenge_Name,Challenge_Status," +
                "Total," + Task.statusMap.values.mkString(",") + "," +
                "High_Total," + Task.statusMap.values.map(v => "High_" + v).mkString(",") + "," +
                "Medium_Total," + Task.statusMap.values
                .map(v => "Medium_" + v)
                .mkString(",") + "," +
                "Low_Total," + Task.statusMap.values.map(v => "Low_" + v).mkString(",") +
                "\n"
            ).concat(ByteString(_extractSnapshots(challengeId).mkString("\n"))),
            Some("text/csv; header=present")
          )
        )
      }
  }

  private def _extractSnapshots(challengeId: Long): Seq[String] = {
    val snapshots = snapshotManager.getAllChallengeSnapshots(challengeId)

    snapshots.map(snapshot => {
      val priorityActions = snapshot.priorityActions.get

      s"${snapshot.created},${snapshot.id},${snapshot.itemId},${snapshot.name},${snapshot.status.getOrElse("")}," +
        snapshot.actions.get.values.mkString(",") + "," +
        priorityActions(Challenge.PRIORITY_HIGH.toString).values.mkString(",") + "," +
        priorityActions(Challenge.PRIORITY_MEDIUM.toString).values.mkString(",") + "," +
        priorityActions(Challenge.PRIORITY_LOW.toString).values.mkString(",")
    })
  }

}
