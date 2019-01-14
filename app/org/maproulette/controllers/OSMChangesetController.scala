package org.maproulette.controllers

import javax.inject.{Inject, Singleton}
import org.maproulette.exception.{StatusMessage, StatusMessages}
import org.maproulette.services.osm._
import org.maproulette.session.SessionManager
import play.api.libs.json.{JsError, Json}
import play.api.mvc._

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import scala.xml.Elem

/**
  * @author mcuthbert
  */
@Singleton
class OSMChangesetController @Inject() (components: ControllerComponents,
                                        sessionManager: SessionManager,
                                        changeService:ChangesetService,
                                        bodyParsers:PlayBodyParsers) extends AbstractController(components) with StatusMessages {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val tagChangeReads = ChangeObjects.tagChangeReads
  implicit val tagChangeResultWrites = ChangeObjects.tagChangeResultWrites
  implicit val tagChangeSubmissionReads = ChangeObjects.tagChangeSubmissionReads

  /**
    * Returns the changes requested by the user without submitting it to OSM. This will be a json
    * format that will contain before and after results for the requested tag changes
    *
    * @return
    */
  def testTagChange(changeType:String) = Action.async(bodyParsers.json) { implicit request =>
    this.sessionManager.authenticatedFutureRequest { implicit user =>
      val result = request.body.validate[List[TagChange]]
      result.fold(
        errors => {
          Future { BadRequest(Json.toJson(StatusMessage("KO", JsError.toJson(errors)))) }
        },
        element => {
          val p = Promise[Result]
          val future = changeType match {
            case OSMChangesetController.CHANGETYPE_OSMCHANGE => changeService.getOsmChange(element)
            case _ => changeService.testTagChange(element)
          }
          future onComplete {
            case Success(res) =>
              changeType match {
                case OSMChangesetController.CHANGETYPE_OSMCHANGE => p success Ok(res.asInstanceOf[Elem]).as("text/xml")
                case _ => p success Ok(Json.toJson(res.asInstanceOf[List[TagChangeResult]]))
              }
            case Failure(f) => p failure f
          }
          p.future
        }
      )
    }
  }

  /**
    * Submits a tag change to the open street map servers to be applied to the data.
    *
    * @return
    */
  def submitTagChange() = Action.async(bodyParsers.json) { implicit request =>
    this.sessionManager.authenticatedFutureRequest { implicit user =>
      val result = request.body.validate[TagChangeSubmission]
      result.fold(
        errors => {
          Future { BadRequest(Json.toJson(StatusMessage("KO", JsError.toJson(errors)))) }
        },
        element => {
          val p = Promise[Result]
          changeService.submitTagChange(element.changes, element.comment, user.osmProfile.requestToken) onComplete {
            case Success(res) => p success Ok(res)
            case Failure(f) => p failure f
          }
          p.future
        }
      )
    }
  }

  // todo create API that will allow user to submit an OSMChange XML directly into the service. The biggest potential
  // downside to this approach, is that there is little control over what is submitted.
}

object OSMChangesetController {
  val CHANGETYPE_OSMCHANGE = "osmchange"
  val CHANGETYPE_DELTA = "delta"
}
