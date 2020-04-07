/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.session

import java.net.URLDecoder

import org.maproulette.utils.Utils
import play.api.mvc.{AnyContent, Request, AnyContentAsFormUrlEncoded}
import play.api.data.format.Formats
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import org.maproulette.exception.InvalidException

/**
  * This holds the search parameters that are used to define task sets for retrieving random tasks
  * from the database
  *
  * TODO add spatial filters as well
  *
  * @author cuthbertm
  */
case class SearchLocation(left: Double, bottom: Double, right: Double, top: Double)

case class SearchChallengeParameters(
    challengeIds: Option[List[Long]] = None,
    challengeTags: Option[List[String]] = None,
    challengeTagConjunction: Option[Boolean] = None,
    challengeSearch: Option[String] = None,
    challengeEnabled: Option[Boolean] = None,
    challengeDifficulty: Option[Int] = None,
    challengeStatus: Option[List[Int]] = None,
    requiresLocal: Int = SearchParameters.CHALLENGE_REQUIRES_LOCAL_EXCLUDE
)

case class SearchParameters(
    projectIds: Option[List[Long]] = None,
    projectSearch: Option[String] = None,
    projectEnabled: Option[Boolean] = None,
    challengeParams: SearchChallengeParameters = SearchChallengeParameters(),
    taskTags: Option[List[String]] = None,
    taskTagConjunction: Option[Boolean] = None,
    taskSearch: Option[String] = None,
    taskStatus: Option[List[Int]] = None,
    taskId: Option[Long] = None,
    taskReviewStatus: Option[List[Int]] = None,
    taskProperties: Option[Map[String, String]] = None,
    taskPropertySearchType: Option[String] = None,
    taskPropertySearch: Option[TaskPropertySearch] = None,
    taskPriorities: Option[List[Int]] = None,
    priority: Option[Int] = None,
    location: Option[SearchLocation] = None,
    bounding: Option[SearchLocation] = None,
    boundingGeometries: Option[List[JsObject]] = None,
    fuzzySearch: Option[Int] = None,
    mapper: Option[String] = None,
    owner: Option[String] = None,
    reviewer: Option[String] = None
) {
  def getProjectIds: Option[List[Long]] = projectIds match {
    case Some(v) => Some(v.filter(_ != -1))
    case None    => None
  }

  def getChallengeIds: Option[List[Long]] = challengeParams.challengeIds match {
    case Some(v) => Some(v.filter(_ != -1))
    case None    => None
  }

  def getPriority: Option[Int] = priority match {
    case Some(v) if v == -1 => None
    case _                  => priority
  }

  def getChallengeDifficulty: Option[Int] = challengeParams.challengeDifficulty match {
    case Some(v) if v == -1 => None
    case _                  => challengeParams.challengeDifficulty
  }

  def hasTaskTags: Boolean = taskTags.getOrElse(List.empty).exists(tt => tt.nonEmpty)

  def hasChallengeTags: Boolean =
    challengeParams.challengeTags.getOrElse(List.empty).exists(ct => ct.nonEmpty)

  def enabledProject: Boolean = projectEnabled.getOrElse(true)

  def enabledChallenge: Boolean = challengeParams.challengeEnabled.getOrElse(true)
}

object SearchParameters {
  val TASK_PROP_SEARCH_TYPE_EQUALS       = "equals"
  val TASK_PROP_SEARCH_TYPE_NOT_EQUAL    = "not_equal"
  val TASK_PROP_SEARCH_TYPE_CONTAINS     = "contains"
  val TASK_PROP_SEARCH_TYPE_EXISTS       = "exists"
  val TASK_PROP_SEARCH_TYPE_MISSING      = "missing"
  val TASK_PROP_SEARCH_TYPE_LESS_THAN    = "less_than"
  val TASK_PROP_SEARCH_TYPE_GREATER_THAN = "greater_than"

  val TASK_PROP_VALUE_TYPE_STRING = "string"
  val TASK_PROP_VALUE_TYPE_NUMBER = "number"

  val TASK_PROP_OPERATION_TYPE_AND = "and"
  val TASK_PROP_OPERATION_TYPE_OR  = "or"

  val CHALLENGE_REQUIRES_LOCAL_EXCLUDE = 0
  val CHALLENGE_REQUIRES_LOCAL_INCLUDE = 1
  val CHALLENGE_REQUIRES_LOCAL_ONLY    = 2

  implicit val locationWrites = Json.writes[SearchLocation]
  implicit val locationReads  = Json.reads[SearchLocation]
  implicit val taskPropertySearchWrites: Writes[TaskPropertySearch] =
    Json.writes[TaskPropertySearch]
  implicit val taskPropertySearchReads: Reads[TaskPropertySearch] = Json.reads[TaskPropertySearch]

  implicit val challengeParamsWrites: Writes[SearchChallengeParameters] =
    Json.writes[SearchChallengeParameters]
  implicit val challengeParamsReads: Reads[SearchChallengeParameters] =
    Json.reads[SearchChallengeParameters]
  implicit val paramsWrites: Writes[SearchParameters] = Json.writes[SearchParameters]
  implicit val paramsReads: Reads[SearchParameters]   = Json.reads[SearchParameters]

  implicit object SearchParametersFormat extends Format[SearchParameters] {
    override def writes(o: SearchParameters): JsValue = {

      var original = Json.toJson(o)(Json.writes[SearchParameters])
      // Move challenge param fields up to top level
      var updated = o.challengeParams.challengeIds match {
        case Some(c) => Utils.insertIntoJson(original, "challengeIds", c, true)
        case None    => original
      }
      updated = o.challengeParams.challengeTags match {
        case Some(c) => Utils.insertIntoJson(updated, "challengeTags", c, true)
        case None    => updated
      }
      updated = o.challengeParams.challengeTagConjunction match {
        case Some(c) => Utils.insertIntoJson(updated, "challengeTagConjunction", c, true)
        case None    => updated
      }
      updated = o.challengeParams.challengeSearch match {
        case Some(r) => Utils.insertIntoJson(updated, "challengeSearch", r, true)
        case None    => updated
      }
      updated = o.challengeParams.challengeEnabled match {
        case Some(r) => Utils.insertIntoJson(updated, "challengeEnabled", r, true)
        case None    => updated
      }
      updated = o.challengeParams.challengeDifficulty match {
        case Some(r) => Utils.insertIntoJson(updated, "challengeDifficulty", r, true)
        case None    => updated
      }
      updated = o.challengeParams.challengeStatus match {
        case Some(r) => Utils.insertIntoJson(updated, "challengeStatus", r, true)
        case None    => updated
      }
      updated =
        Utils.insertIntoJson(updated, "requiresLocal", o.challengeParams.requiresLocal, true)

      updated = updated.as[JsObject] - "challengeParams"
      updated
    }

    override def reads(json: JsValue): JsResult[SearchParameters] = {
      implicit val challengeParamsReads: Reads[SearchChallengeParameters] =
        Json.reads[SearchChallengeParameters]

      var challengeParams = Map[String, JsValue]()
      (json \ "challengeIds").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeIds" -> v)
        case None    => // do nothing
      }

      (json \ "challengeTags").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeTags" -> v)
        case None    => // do nothing
      }

      (json \ "challengeTagConjunction").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeTagConjunction" -> v)
        case None    => // do nothing
      }

      (json \ "challengeSearch").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeSearch" -> v)
        case None    => // do nothing
      }

      (json \ "challengeEnabled").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeEnabled" -> v)
        case None    => // do nothing
      }

      (json \ "challengeDifficulty").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeDifficulty" -> v)
        case None    => // do nothing
      }

      (json \ "challengeStatus").toOption match {
        case Some(v) => challengeParams = challengeParams + ("challengeStatus" -> v)
        case None    => // do nothing
      }

      (json \ "requiresLocal").toOption match {
        case Some(v) => challengeParams = challengeParams + ("requiresLocal" -> v)
        case None    => // do nothing
      }

      val jsonWithChallengeParams =
        Utils.insertIntoJson(json, "challengeParams", challengeParams, false)
      Json.fromJson[SearchParameters](jsonWithChallengeParams)(Json.reads[SearchParameters])
    }
  }

  /**
    * Retrieves the search cookie from the cookie list and creates a search parameter object
    * to send along with the request. It will also check the query string and if any parameters
    * are found it will override the values in the cookie.
    *
    * @param block   The block of code to be executed after the cookie has been retrieved
    * @param request The request that the cookie came in on
    * @tparam T The response type from the block of code
    * @return The response from the block of code
    */
  // format: off
  def withSearch[T](block: SearchParameters => T)(implicit request: Request[AnyContent]): T = {
    val params = request.cookies.get("search") match {
      case Some(c) => convert(c.value)
      case None => SearchParameters()
    }

    val projectIds = request.getQueryString("pid") match {
      case Some(q) => Utils.toLongList(q)
      case None => params.projectIds
    }

    val challengeIds = request.getQueryString("cid") match {
      case Some(q) => Utils.toLongList(q)
      case None => params.challengeParams.challengeIds
    }

    var taskPropertySearch:Option[TaskPropertySearch] = None

    // If we are submitting the taskPropertySearch JSON as POST form data
    if (request.method == "POST") {
      request.body match {
        case AnyContentAsFormUrlEncoded(formData) =>
          val tpsData = formData("taskPropertySearch")
          if (tpsData.length > 0 && tpsData.head != "{}") {
            taskPropertySearch =
              Json.fromJson[TaskPropertySearch](Json.parse(tpsData.head)) match {
                case JsSuccess(tps, _) => {
                  Some(tps)
                }
                case e: JsError =>
                  throw new InvalidException(s"Unable to create TaskPropertySearch from JSON: ${JsError toJson e}")
              }
          }
        case _ => None
      }
    }
    // Otherwise if submitted as PUT data.
    else {
      taskPropertySearch = (request.body.asJson) match {
        case Some(v) =>
          (v \ "taskPropertySearch").toOption match {
            case Some(result) => {
              Json.fromJson[TaskPropertySearch](result) match {
                case JsSuccess(tps, _) => {
                  Some(tps)
                }
                case e: JsError =>
                  throw new InvalidException(s"Unable to create TaskPropertySearch from JSON: ${JsError toJson e}")
              }
            }
            case None => None
          }
        case None => None
      }
    }

    block(SearchParameters(
      //projectID
      projectIds,
      //projectSearch
      this.getStringParameter(request.getQueryString("ps"), params.projectSearch),
      //projectEnabled
      this.getBooleanParameter(request.getQueryString("pe"), params.projectEnabled),
      new SearchChallengeParameters(
        //challengeID
        challengeIds,
        //challengeTags
        request.getQueryString("ct") match {
          case Some(v) => Some(v.split(",").toList)
          case None => params.challengeParams.challengeTags
        },
        //challengeTagConjunction
        this.getBooleanParameter(request.getQueryString("ctc"), Some(params.challengeParams.challengeTagConjunction.getOrElse(false))),
        //challengeSearch
        this.getStringParameter(request.getQueryString("cs"), params.challengeParams.challengeSearch),
        //challengeEnabled
        this.getBooleanParameter(request.getQueryString("ce"), params.challengeParams.challengeEnabled),
        //challengeDifficulty
        this.getIntParameter(request.getQueryString("cd"), params.challengeParams.challengeDifficulty),
        //challengeStatus
        request.getQueryString("cStatus") match {
          case Some(v) => Utils.toIntList(v)
          case None => params.challengeParams.challengeStatus
        },
        //requiresLocal
        this.getIntParameter(request.getQueryString("cLocal"),
          Some(params.challengeParams.requiresLocal)).getOrElse(SearchParameters.CHALLENGE_REQUIRES_LOCAL_EXCLUDE)
      ),
      //taskTags
      request.getQueryString("tt") match {
        case Some(v) => Some(v.split(",").toList)
        case None => params.taskTags
      },
      //taskTagConjunction
      this.getBooleanParameter(request.getQueryString("ttc"), Some(params.taskTagConjunction.getOrElse(false))),
      //taskSearch
      this.getStringParameter(request.getQueryString("ts"), params.taskSearch),
      //taskStatus
      request.getQueryString("tStatus") match {
        case Some(v) => Utils.toIntList(v)
        case None => params.taskStatus
      },
      //taskIds
      this.getLongParameter(request.getQueryString("tid"), params.taskId),
      //taskReviewStatus
      request.getQueryString("trStatus") match {
        case Some(v) => Utils.toIntList(v)
        case None => params.taskReviewStatus
      },
      //taskProperties (base 64 encoded key:value comma separated)
      request.getQueryString("tProps") match {
        case Some(v) => Utils.toMap(v)
        case None => params.taskProperties
      },
      //taskPropertySearchType
      this.getStringParameter(request.getQueryString("tPropsSearchType"),
                              params.taskPropertySearchType),
      //taskPropertySearch
      taskPropertySearch,
      //taskPriorities
      request.getQueryString("priorities") match {
        case Some(v) => Utils.toIntList(v)
        case None => params.taskPriorities
      },
      //taskPriority
      this.getIntParameter(request.getQueryString("tp"), params.priority),
      //taskBoundingBox for tasks found in bounding Box
      request.getQueryString("tbb") match {
        case Some(v) if v.nonEmpty =>
          v.split(",") match {
            case x if x.size == 4 => Some(SearchLocation(x(0).toDouble, x(1).toDouble, x(2).toDouble, x(3).toDouble))
            case _ => None
          }
        case _ => params.location
      },
      //boundingBox for Challenges bounds contained in bounding box
      request.getQueryString("bb") match {
        case Some(v) if v.nonEmpty =>
          v.split(",") match {
            case x if x.size == 4 => Some(SearchLocation(x(0).toDouble, x(1).toDouble, x(2).toDouble, x(3).toDouble))
            case _ => None
          }
        case _ => None
      },
      // boundingGeometries (not supported on URL)
      None,
      //FuzzySearch
      this.getIntParameter(request.getQueryString("fuzzy"), params.fuzzySearch),
      //CompletedBy
      this.getStringParameter(request.getQueryString("m"), params.mapper),
      //Owner
      this.getStringParameter(request.getQueryString("o"), params.owner),
      //Reviewer
      this.getStringParameter(request.getQueryString("r"), params.reviewer)
    ))
  }
  // format: off

  /**
    * Will attempt to convert the cookie to SearchParameters, if it fails it simply initializes an
    * empty SearchParameters
    *
    * @param value
    * @return
    */
  def convert(value: String): SearchParameters = {
    try {
      Utils.omitEmpty(Json.parse(URLDecoder.decode(value, "UTF-8")).as[JsObject], false, false).as[SearchParameters]
    } catch {
      case e: Exception =>
        SearchParameters()
    }
  }

  private def getBooleanParameter(value: Option[String], default: Option[Boolean]): Option[Boolean] = value match {
    case Some(v) => Some(v.toBoolean)
    case None => default
  }

  private def getIntParameter(value: Option[String], default: Option[Int]): Option[Int] = value match {
    case Some(v) => Some(v.toInt)
    case None => default
  }

  private def getStringParameter(value: Option[String], default: Option[String]): Option[String] = value match {
    case Some(v) => Some(v)
    case None => default
  }

  private def getLongParameter(value: Option[String], default: Option[Long]): Option[Long] = value match {
    case Some(v) => Some(v.toLong)
    case None => default
  }
}
