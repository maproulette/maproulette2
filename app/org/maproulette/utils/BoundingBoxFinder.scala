package org.maproulette.utils

import play.api.libs.json.Json
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray

/**
  * Parses bounding boxes for country codes from a file.
  *
  * @author krotstan
  */
object BoundingBoxFinder {
  private val input_file = "./conf/country-code-bounding-box.json"
  private val jsonContent = scala.io.Source.fromFile(input_file).mkString
  private val jsonData = Json.parse(jsonContent).as[JsObject]

  /**
   * Returns a map of country code to bounding box locations.
   * (ie. "US" => "-125.0, 25.0, -66.96, 49.5")
   */
  def boundingBoxforAll(): scala.collection.mutable.Map[String,String] = {
    val ccDataMap = collection.mutable.Map[String, String]()

    // Data is in format "US" => ["United States", [-125.0, 25.0, -66.96, 49.5]]
    jsonData.keys.foreach( countryCode => {
      val result = (jsonData \ countryCode).toString
      val index = result.indexOf(',')

      // Fetch just the bounding box coordinates
      val boundingBox = result.slice(index + 2, result.length - 3)

      ccDataMap += (countryCode -> boundingBox)
    })

    return ccDataMap
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
    return countryCodeString.substring(1, countryCodeString.length()-1)
  }
}
