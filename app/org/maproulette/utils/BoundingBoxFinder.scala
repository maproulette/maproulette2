/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.utils

import javax.inject.{Inject, Singleton}
import play.Environment
import play.api.libs.json.{JsObject, Json}

/**
  * Parses bounding boxes for country codes from a file.
  *
  * @author krotstan
  */

@Singleton
class BoundingBoxFinder @Inject() (implicit val env: Environment) {
  private val jsonContent =
    scala.io.Source.fromInputStream(env.resourceAsStream("country-code-bounding-box.json")).mkString
  private val jsonData = Json.parse(jsonContent).as[JsObject]

  /**
    * Returns a map of country code to bounding box locations.
    * (ie. "US" => "-125.0, 25.0, -66.96, 49.5")
    */
  def boundingBoxforAll(): scala.collection.mutable.Map[String, String] = {
    val ccDataMap = collection.mutable.Map[String, String]()

    // Data is in format "US" => ["United States", [-125.0, 25.0, -66.96, 49.5]]
    jsonData.keys.foreach(countryCode => {
      val result = (jsonData \ countryCode).toString
      val index  = result.indexOf(',')

      // Fetch just the bounding box coordinates
      val boundingBox = result.slice(index + 2, result.length - 3)

      ccDataMap += (countryCode -> boundingBox)
    })
    ccDataMap
  }

  /**
    * Returns a bounding box just for the specified country code.
    * (ie. "-125.0, 25.0, -66.96, 49.5")
    *
    * @param countryCode
    */
  def boundingBoxforCountry(countryCode: String): String = {
    val countryCodeData = jsonData \\ countryCode

    var countryCodeString = countryCodeData(0)(1).toString()
    countryCodeString.substring(1, countryCodeString.length() - 1)
  }
}
