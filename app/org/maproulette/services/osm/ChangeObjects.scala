package org.maproulette.services.osm

import org.maproulette.services.osm.OSMType.OSMType
import org.maproulette.utils.Utils
import play.api.libs.json.{Json, Reads, Writes}

object OSMType extends Enumeration {
  type OSMType = Value
  val NODE, WAY, RELATION = Value

  override def toString() : String = this.toString().toLowerCase
}

/**
  * A class that wraps around the tag change class for submission to the API
  *
  * @param comment Comment for the changes checkin
  * @param changes The changes to be applied to the data
  */
case class TagChangeSubmission(comment:String, changes:List[TagChange])

/**
  * Case class that will contain all requested additions, updates and deletes. The different
  * actions are defined as follows:
  * Create - Key, Some(Value) (defined by whether the actual object as the key-value or not)
  * Update - Key, Some(Value) (defined by whether the actual object has the key-value or not)
  * Delete - Key, None
  *
  * @param osmId The id of the object you are requesting the changes for
  * @param osmType The type of object (node, way or relation)
  * @param updates The tag updates that are being requested
  */
case class TagChange(osmId:Long, osmType:OSMType, updates:Map[String, String], deletes:List[String], version:Option[Int]=None)

/**
  * The results from making the requested changes to the object.
  *
  * @param osmId The id of the object you are requesting the changes for
  * @param osmType The type of object (node, way or relation)
  * @param creates A map of all newly created tags and their values
  * @param updates A map of all updated tags with the original value and the new value
  * @param deletes A map of all deleted tags and the value
  */
case class TagChangeResult(osmId:Long, osmType:OSMType, creates:Map[String, String], updates:Map[String, (String, String)], deletes:Map[String, String])

object ChangeObjects {
  implicit val enumReads = Utils.enumReads(OSMType)
  implicit val tagChangeReads: Reads[TagChange] = Json.reads[TagChange]
  implicit val tagChangeResultWrites: Writes[TagChangeResult] = Json.writes[TagChangeResult]
  implicit val tagChangeSubmissionReads: Reads[TagChangeSubmission] = Json.reads[TagChangeSubmission]
}
