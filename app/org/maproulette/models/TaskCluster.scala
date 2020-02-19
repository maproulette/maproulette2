// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.maproulette.session.SearchParameters
import play.api.libs.json._

/**
  * Task cluster object that contains information about a cluster containing various tasks
  *
  * @author mcuthbert
  */
case class TaskCluster(
    clusterId: Int,
    numberOfPoints: Int,
    taskId: Option[Long],
    taskStatus: Option[Int],
    taskPriority: Option[Int],
    params: SearchParameters,
    point: Point,
    bounding: JsValue = Json.toJson("{}"),
    challengeIds: List[Long],
    geometries: Option[JsValue] = None
) extends DefaultWrites

object TaskCluster {
  implicit val pointWrites: Writes[Point]             = Json.writes[Point]
  implicit val pointReads: Reads[Point]               = Json.reads[Point]
  implicit val taskClusterWrites: Writes[TaskCluster] = Json.writes[TaskCluster]
  implicit val taskClusterReads: Reads[TaskCluster]   = Json.reads[TaskCluster]
}
