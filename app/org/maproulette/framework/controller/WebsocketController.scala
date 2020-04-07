/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.controller

import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject
import org.maproulette.exception.StatusMessages
import org.maproulette.models.dal.DALManager
import org.maproulette.provider.websockets.{WebSocketActor, WebSocketMessages}
import org.maproulette.session.SessionManager
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.mvc._

/**
  * @author nrotstan
  */
class WebSocketController @Inject() (
    dalManager: DALManager,
    sessionManager: SessionManager,
    components: ControllerComponents
)(implicit system: ActorSystem, mat: Materializer)
    extends AbstractController(components)
    with StatusMessages {

  // implicit reads and writes used for various JSON messages
  implicit val clientMessageReads = WebSocketMessages.clientMessageReads
  implicit val messageFlowTransformer =
    MessageFlowTransformer.jsonMessageFlowTransformer[WebSocketMessages.ClientMessage, JsValue]

  /**
    * Instantiate a WebSocketActor to handle communication for a new client
    * websocket connection
    */
  def socket = WebSocket.accept[WebSocketMessages.ClientMessage, JsValue] { request =>
    ActorFlow.actorRef { out =>
      WebSocketActor.props(out)
    }
  }
}
