package org.maproulette.controllers.api

import java.sql.Connection
import javax.inject.Inject

import io.swagger.annotations.{Api, ApiOperation}
import org.maproulette.actions._
import org.maproulette.controllers.CRUDController
import org.maproulette.models.dal.{TagDAL, TaskDAL}
import org.maproulette.models.{Challenge, Tag, Task}
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.session.{SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.Action

/**
  * The Task controller handles all operations for the Task objects.
  * This includes CRUD operations and searching/listing.
  * See {@link org.maproulette.controllers.CRUDController} for more details on CRUD object operations
  *
  * @author cuthbertm
  */
@Api(value = "/Task", description = "Operations for Tasks", protocols = "http")
class TaskController @Inject() (override val sessionManager: SessionManager,
                                override val actionManager: ActionManager,
                                override val dal:TaskDAL,
                                override val tagDAL: TagDAL)
  extends CRUDController[Task] with TagsMixin[Task] {

  // json reads for automatically reading Tasks from a posted json body
  override implicit val tReads: Reads[Task] = Task.taskReads
  // json writes for automatically writing Tasks to a json body response
  override implicit val tWrites: Writes[Task] = Task.taskWrites
  // The type of object that this controller deals with.
  override implicit val itemType = TaskType()
  // json reads for automatically reading Tags from a posted json body
  implicit val tagReads: Reads[Tag] = Tag.tagReads
  override def dalWithTags = dal

  private def updateGeometryData(body: JsValue) : JsValue = {
    (body \ "geometries").asOpt[String] match {
      case Some(value) =>
        // if it is a string, then it is either GeoJSON or a WKB
        // just check to see if { is the first character and then we can assume it is GeoJSON
        if (value.charAt(0) != '{') {
          // TODO:
          body
        } else {
          // just return the body because it handles this case correctly
          body
        }
      case None =>
        // if it maps to None then it simply could be that it is a JSON object
        (body \ "geometries").asOpt[JsValue] match {
          case Some(value) =>
            // need to convert to a string for the case class otherwise validation will fail
            Utils.insertIntoJson(body, "geometries", value.toString(), true)
          case None =>
            // if the geometries are not supplied then just leave it
            body
        }
    }
  }

  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user:User): JsValue = {
    // add a default priority, this will be updated later when the task is created if there are
    // priority rules defined in the challenge parent
    val updatedBody = Utils.insertIntoJson(body, "priority", Challenge.PRIORITY_HIGH)(IntWrites)
    // We need to update the geometries to make sure that we handle all the different types of
    // geometries that you can deal with like WKB or GeoJSON
    updateGeometryData(super.updateCreateBody(updatedBody, user))
  }


  /**
    * In the case where you need to update the update body, usually you would not update it, but
    * just in case.
    *
    * @param body The request body
    * @return The updated request body
    */
  override def updateUpdateBody(body: JsValue, user:User): JsValue =
    updateGeometryData(super.updateUpdateBody(body, user))

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body          The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user          The user that is executing the function
    */
  override def extractAndCreate(body: JsValue, createdObject: Task, user: User)
                               (implicit c:Connection=null): Unit = extractTags(body, createdObject, user)

  /**
    * Gets a json list of tags of the task
    *
    * @param id The id of the task containing the tags
    * @return The html Result containing json array of tags
    */
  @ApiOperation(value = "Gets the tags for ta task", nickname = "getTagsForTask", httpMethod = "GET")
  def getTagsForTask(implicit id: Long) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(getTags(id)))
    }
  }

  /**
    * Gets a random task(s) given the provided tags.
    *
    * @param projectSearch Filter on the name of the project
    * @param challengeSearch Filter on the name of the challenge (Survey included)
    * @param challengeTags Filter on the tags of the challenge
    * @param tags A comma separated list of tags to match against
    * @param taskSearch Filter based on the name of the task
    * @param limit The number of tasks to return
    * @return
    */
  def getRandomTasks(projectSearch:String,
                     challengeSearch:String,
                     challengeTags:String,
                     tags: String,
                     taskSearch: String,
                     limit: Int) = Action.async { implicit request =>
    sessionManager.userAwareRequest { implicit user =>
      val params = SearchParameters(
        projectSearch = projectSearch,
        challengeSearch = challengeSearch,
        challengeTags = challengeTags.split(",").toList,
        taskTags = tags.split(",").toList,
        taskSearch = taskSearch
      )
      val result = dal.getRandomTasks(User.userOrMocked(user), params, limit)
      result.foreach(task => actionManager.setAction(user, itemType.convertToItem(task.id), TaskViewed(), ""))
      Ok(Json.toJson(result))
    }
  }

  /**
    * This is the generic function that is leveraged by all the specific functions above. So it
    * sets the task status to the specific status ID's provided by those functions.
    * Must be authenticated to perform operation
    *
    * @param id The id of the task
    * @param status The status id to set the task's status to
    * @return 400 BadRequest if status id is invalid or task with supplied id not found.
    *         If successful then 200 NoContent
    */
  def setTaskStatus(id: Long, status: Int) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      if (!Task.isValidStatus(status)) {
        throw new InvalidException(s"Cannot set task [$id] to invalid status [$status]")
      }
      val task = dal.retrieveById(id) match {
        case Some(t) => t
        case None => throw new NotFoundException(s"Task with $id not found, can not set status.")
      }
      dal.setTaskStatus(task, status, user)
      actionManager.setAction(Some(user), new TaskItem(task.id), TaskStatusSet(status), task.name)
      NoContent
    }
  }
}
