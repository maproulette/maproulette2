/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.provider.websockets

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish}

/**
  * WebSocketPublisher is an Akka actor that is responsible for publishing
  * server-initiated messages to the Akka mediator that manages the
  * publish/subscribe of the various message types to the WebSocketActor
  * instances that represent the client websockets.
  *
  * Note that server code should generally use the WebSocketProvider.sendMessage
  * method, rather than trying to access this actor directly, so that it need
  * not worry about interfacing with the Akka actor system.
  *
  * @author nrotstan
  */
class WebSocketPublisher extends Actor {
  val mediator = DistributedPubSub(context.system).mediator

  def receive: PartialFunction[Any, Unit] = {
    case message: WebSocketMessages.ServerMessage =>
      message.meta.subscriptionName match {
        case Some(name) => mediator ! Publish(name, message)
        case None       => None // Ignore messages not intended for publication
      }
  }
}
