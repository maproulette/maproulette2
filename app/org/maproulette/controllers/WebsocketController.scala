package controllers

import javax.inject.Inject
import org.maproulette.exception.{StatusMessage, StatusMessages}
import org.maproulette.models.dal.DALManager
import org.maproulette.session.SessionManager
import org.maproulette.provider.websockets.WebSocketActor
import org.maproulette.provider.websockets.WebSocketMessages
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.mvc.WebSocket.MessageFlowTransformer
import play.api.libs.streams.ActorFlow
import akka.actor.ActorSystem
import akka.stream.Materializer

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
