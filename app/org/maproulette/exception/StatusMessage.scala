/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.exception

import play.api.libs.json.{JsValue, Json, Reads, Writes}

/**
  * @author cuthbertm
  */
trait StatusMessages {
  implicit val statusMessageWrites = StatusMessage.statusMessageWrites
  implicit val statusMessageReads  = StatusMessage.statusMessageReads
}

case class StatusMessage(status: String, message: JsValue)

object StatusMessage {
  implicit val statusMessageWrites: Writes[StatusMessage] = Json.writes[StatusMessage]
  implicit val statusMessageReads: Reads[StatusMessage]   = Json.reads[StatusMessage]
}
