// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.services.osm

import org.maproulette.services.osm.OSMType.OSMType
import org.maproulette.utils.Utils
import org.maproulette.exception.InvalidException
import play.api.libs.json.{Json, Reads, Writes}
import scala.xml.{Attribute, Elem, Null, Text, MetaData}

object OSMType extends Enumeration {
  type OSMType = Value
  val NODE, WAY, RELATION = Value

  override def toString(): String = this.toString().toLowerCase
}

/**
  * A class that wraps around the tag change class for submission to the API
  *
  * @param comment Comment for the changes checkin
  * @param changes The changes to be applied to the data
  */
case class TagChangeSubmission(comment: String, changes: List[TagChange])

/**
  * A class that wraps around the OSMChange class for submission to the API
  *
  * @param comment Comment for the changes checkin
  * @param changes The changes to be applied to the data
  */
case class OSMChangeSubmission(comment: String, changes: OSMChange)

/**
  * Case class that will contain all requested additions, updates and deletes. The different
  * actions are defined as follows:
  * Create - Key, Some(Value) (defined by whether the actual object as the key-value or not)
  * Update - Key, Some(Value) (defined by whether the actual object has the key-value or not)
  * Delete - Key, None
  *
  * @param osmId   The id of the object you are requesting the changes for
  * @param osmType The type of object (node, way or relation)
  * @param updates The tag updates that are being requested
  */
case class TagChange(
    osmId: Long,
    osmType: OSMType,
    updates: Map[String, String],
    deletes: List[String],
    version: Option[Int] = None
)

/**
  * The results from making the requested changes to the object.
  *
  * @param osmId   The id of the object you are requesting the changes for
  * @param osmType The type of object (node, way or relation)
  * @param creates A map of all newly created tags and their values
  * @param updates A map of all updated tags with the original value and the new value
  * @param deletes A map of all deleted tags and the value
  */
case class TagChangeResult(
    osmId: Long,
    osmType: OSMType,
    creates: Map[String, String],
    updates: Map[String, (String, String)],
    deletes: Map[String, String]
)

/**
  * Represents a set of changes (creates, updates, and deletes) of OSM elements,
  * encompassing both tag and geometry changes
  *
  * TODO: support deletes
  */
case class OSMChange(creates: Option[List[ElementCreate]], updates: Option[List[ElementUpdate]])

/**
  * Represents changes to tags on an element, with new and modified tags in the
  * `updates` and tag (keys) to be removed in the `deletes`
  */
case class ElementTagChange(updates: Map[String, String], deletes: List[String])

/**
  * Case class that will contain data for creation of a new OSM elements. Currently
  * only creation of new nodes is supported
  *
  * @param osmId     Negative placeholder id of the element being created
  * @param osmType   The type of object (node, way or relation)
  * @param fields    The element's fields, e.g. lat and lon for a node
  * @param tags      The element's tags
  *
  * TODO: support members
  */
case class ElementCreate(
    osmId: Long,
    osmType: OSMType,
    fields: Map[String, String],
    tags: Map[String, String]
) {
  def toCreateElement(changesetId: Int): Elem = {
    val tagNodes =
      for (tagKV <- tags)
        yield <tag/> % Attribute("v", Text(tagKV._2), Attribute("k", Text(tagKV._1), Null))

    val attributes = this.fieldAttributes(
      fields,
      Attribute(
        "changeset",
        Text(changesetId.toString),
        Attribute("version", Text("1"), Attribute("id", Text(osmId.toString), Null))
      )
    )

    osmType match {
      case OSMType.NODE => <node>{tagNodes}</node> % attributes
      case _ =>
        throw new InvalidException(s"Creation of $osmType elements is not currently supported")
    }
  }

  /**
    * Generate chained Attribute instances for the given fields, finishing the
    * chain with the given terminator
    */
  def fieldAttributes(fields: Map[String, String], terminator: MetaData = Null): MetaData = {
    fields.isEmpty match {
      case true => terminator
      case false =>
        Attribute(
          fields.head._1,
          Text(fields.head._2),
          this.fieldAttributes(fields - fields.head._1, terminator)
        )
    }
  }
}

/**
  * Case class that will contain data for updates of existing OSM elements.
  * Currently only changes to tags are supported
  *
  * @param osmId   The id of the object you are requesting the changes for
  * @param osmType The type of object (node, way or relation)
  * @param tags    The tag changes to apply
  *
  * TODO: support members
  */
case class ElementUpdate(
    osmId: Long,
    osmType: OSMType,
    version: Option[Int] = None,
    tags: ElementTagChange
)

object ChangeObjects {
  implicit val enumReads                                      = Utils.enumReads(OSMType)
  implicit val tagChangeReads: Reads[TagChange]               = Json.reads[TagChange]
  implicit val tagChangeResultWrites: Writes[TagChangeResult] = Json.writes[TagChangeResult]
  implicit val tagChangeSubmissionReads: Reads[TagChangeSubmission] =
    Json.reads[TagChangeSubmission]
  implicit val elementTagChangeReads: Reads[ElementTagChange] =
    Json.reads[ElementTagChange]
  implicit val elementCreateReads: Reads[ElementCreate] =
    Json.reads[ElementCreate]
  implicit val elementUpdateReads: Reads[ElementUpdate] =
    Json.reads[ElementUpdate]
  implicit val changeReads: Reads[OSMChange] =
    Json.reads[OSMChange]
  implicit val changeSubmissionReads: Reads[OSMChangeSubmission] =
    Json.reads[OSMChangeSubmission]
}
