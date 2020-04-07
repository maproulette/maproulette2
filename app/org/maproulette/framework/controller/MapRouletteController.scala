/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.controller

import org.joda.time.DateTime
import org.maproulette.framework.model.User
import org.maproulette.utils.Utils
import play.api.libs.json.{JodaWrites, JsValue}
import play.api.mvc.AbstractController

/**
  * @author mcuthbert
  */
trait MapRouletteController extends SessionController {
  this: AbstractController =>

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @param user The user executing the request
    * @return
    */
  def updateBody(body: JsValue, user: User): JsValue = {
    var jsonBody = Utils.insertJsonID(body)
    jsonBody =
      Utils.insertIntoJson(jsonBody, "created", DateTime.now())(JodaWrites.JodaDateTimeNumberWrites)
    Utils.insertIntoJson(jsonBody, "modified", DateTime.now())(JodaWrites.JodaDateTimeNumberWrites)
  }
}
