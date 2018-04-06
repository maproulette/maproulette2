package org.maproulette.models

import org.maproulette.session.SearchParameters
import play.api.libs.json._

/**
  * Task cluster object that contains information about a cluster containing various tasks
  *
  * @author mcuthbert
  */
case class TaskCluster(clusterId:Int, numberOfPoints:Int, params:SearchParameters, point:Point, bounding:JsValue = Json.toJson("{}")) extends DefaultWrites

object TaskCluster {
  implicit val pointWrites: Writes[Point] = Json.writes[Point]
  implicit val pointReads: Reads[Point] = Json.reads[Point]
  implicit val taskClusterWrites: Writes[TaskCluster] = Json.writes[TaskCluster]
  implicit val taskClusterReads: Reads[TaskCluster] = Json.reads[TaskCluster]
}
