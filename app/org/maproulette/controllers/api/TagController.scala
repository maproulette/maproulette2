// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.controllers.CRUDController
import org.maproulette.data.{ActionManager, TagType}
import org.maproulette.models.Tag
import org.maproulette.models.dal.TagDAL
import org.maproulette.session.{SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc._

/**
  * The Tag controller handles all operations for the Challenge objects.
  * This includes CRUD operations and searching/listing.
  * See CRUDController for more details on CRUD object operations
  *
  * @author cuthbertm
  */
class TagController @Inject()(override val sessionManager: SessionManager,
                              override val actionManager: ActionManager,
                              override val dal: TagDAL,
                              components: ControllerComponents,
                              override val bodyParsers: PlayBodyParsers)
  extends AbstractController(components) with CRUDController[Tag] {

  // json reads for automatically reading Tags from a posted json body
  override implicit val tReads: Reads[Tag] = Tag.tagReads
  // json writes for automatically writing Tags to a json body response
  override implicit val tWrites: Writes[Tag] = Tag.tagWrites
  // The type of object that this controller deals with.
  override implicit val itemType = TagType()

  /**
    * Gets the tags based on a prefix. So if you are looking for all tags that begin with
    * "road_", then set the prefix to "road_"
    *
    * @param prefix The prefix for the tags
    * @param limit  The limit on how many tags to be returned
    * @param offset This is used for page offsets, so if you limit 10 tags and have offset 0, then
    *               changing to offset 1 will return the next set of 10 tags.
    * @return
    */
  def getTags(prefix: String, tagType: String, limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dal.findTags(prefix, tagType, limit, offset)))
    }
  }

  /**
    * Function is primarily called from CRUDController, which is used to handle the actual creation
    * of the tags. The function it overrides does it in a very generic way, so this function is
    * specifically written so that it will update the tags correctly. Specifically tags have to be
    * matched on ids and names, instead of just ids.
    *
    * @param requestBody This is the posted request body in json format.
    * @param arr         The list of Tag objects supplied in the json array from the request body
    * @param user        The id of the user that is executing the request
    * @param update      If an item is found then update it, if parameter set to true, otherwise we skip.
    */
  override def internalBatchUpload(requestBody: JsValue, arr: List[JsValue], user: User, update: Boolean): Unit = {
    val tagList = arr.flatMap(element => (element \ "id").asOpt[Long] match {
      case Some(itemID) if update => element.validate[Tag].fold(
        errors => None,
        value => Some(value)
      )
      case None => Utils.insertJsonID(element).validate[Tag].fold(
        errors => None,
        value => Some(value)
      )
    })
    this.dal.updateTagList(tagList, user)
  }
}
