// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.data.{Actions, ChallengeType, ItemType}
import org.maproulette.exception.InvalidException
import org.maproulette.models.utils.{ChallengeReads, ChallengeWrites}
import play.api.libs.json._

// Answer cass class for Surveys
case class Answer(id: Long = -1, answer: String)

case class PriorityRule(operator: String, key: String, value: String, valueType: String) {
  def doesMatch(properties: Map[String, String]): Boolean = {
    properties.find(pair => StringUtils.equalsIgnoreCase(pair._1, key)) match {
      case Some(v) =>
        valueType match {
          case "string" =>
            operator match {
              case "equal" => StringUtils.equals(v._2, value)
              case "not_equal" => !StringUtils.equals(v._2, value)
              case "contains" => StringUtils.contains(v._2, value)
              case "not_contains" => !StringUtils.contains(v._2, value)
              case "is_empty" => StringUtils.isEmpty(v._2)
              case "is_not_empty" => StringUtils.isNotEmpty(v._2)
              case _ => throw new InvalidException(s"Operator $operator not supported")
            }
          case "integer" =>
            operator match {
              case "==" => v._2.toInt == value.toInt
              case "!=" => v._2.toInt != value.toInt
              case "<" => v._2.toInt < value.toInt
              case "<=" => v._2.toInt <= value.toInt
              case ">" => v._2.toInt > value.toInt
              case ">=" => v._2.toInt >= value.toInt
              case _ => throw new InvalidException(s"Operator $operator not supported")
            }
          case "double" =>
            operator match {
              case "==" => v._2.toDouble == value.toDouble
              case "!=" => v._2.toDouble != value.toDouble
              case "<" => v._2.toDouble < value.toDouble
              case "<=" => v._2.toDouble <= value.toDouble
              case ">" => v._2.toDouble > value.toDouble
              case ">=" => v._2.toDouble >= value.toDouble
              case _ => throw new InvalidException(s"Operator $operator not supported")
            }
          case "long" =>
            operator match {
              case "==" => v._2.toLong == value.toLong
              case "!=" => v._2.toLong != value.toLong
              case "<" => v._2.toLong < value.toLong
              case "<=" => v._2.toLong <= value.toLong
              case ">" => v._2.toLong > value.toLong
              case ">=" => v._2.toLong >= value.toLong
              case _ => throw new InvalidException(s"Operator $operator not supported")
            }
          case x => throw new InvalidException(s"Type $x not supported by Priority Rules")
        }
      case None => false
    }
  }
}

case class ChallengeGeneral(owner: Long,
                            parent: Long,
                            instruction: String,
                            difficulty: Int = Challenge.DIFFICULTY_NORMAL,
                            blurb: Option[String] = None,
                            enabled: Boolean = false,
                            challengeType: Int = Actions.ITEM_TYPE_CHALLENGE,
                            featured: Boolean = false,
                            popularity: Option[Int] = None,
                            checkinComment: String = "",
                            checkinSource: String = "",
                            virtualParents: Option[Array[Long]] = None) extends DefaultWrites

case class ChallengeCreation(overpassQL: Option[String] = None, remoteGeoJson: Option[String] = None) extends DefaultWrites

case class ChallengePriority(defaultPriority: Int = Challenge.PRIORITY_HIGH,
                             highPriorityRule: Option[String] = None,
                             mediumPriorityRule: Option[String] = None,
                             lowPriorityRule: Option[String] = None) extends DefaultWrites

case class ChallengeExtra(defaultZoom: Int = Challenge.DEFAULT_ZOOM,
                          minZoom: Int = Challenge.MIN_ZOOM,
                          maxZoom: Int = Challenge.MAX_ZOOM,
                          defaultBasemap: Option[Int] = None,
                          defaultBasemapId: Option[String] = None,
                          customBasemap: Option[String] = None,
                          updateTasks: Boolean = false) extends DefaultWrites

case class ChallengeListing(id: Long,
                            parent: Long,
                            name: String,
                            enabled: Boolean,
                            virtualParents: Option[Array[Long]] = None)

/**
  * The ChallengeFormFix case class is built so that we can nest the form objects as there is a limit
  * on the number of elements allowed in the form mapping.
  */
case class Challenge(override val id: Long,
                     override val name: String,
                     override val created: DateTime,
                     override val modified: DateTime,
                     override val description: Option[String] = None,
                     deleted: Boolean = false,
                     infoLink: Option[String] = None,
                     general: ChallengeGeneral,
                     creation: ChallengeCreation,
                     priority: ChallengePriority,
                     extra: ChallengeExtra,
                     status: Option[Int] = Some(0),
                     statusMessage: Option[String] = None,
                     lastTaskRefresh: Option[DateTime] = None,
                     location: Option[String] = None,
                     bounding: Option[String] = None) extends BaseObject[Long] with DefaultWrites {

  override val itemType: ItemType = ChallengeType()

  def isHighPriority(properties: Map[String, String]): Boolean = this.matchesRule(priority.highPriorityRule, properties)

  def isMediumPriority(properties: Map[String, String]): Boolean = this.matchesRule(priority.mediumPriorityRule, properties)

  def isLowRulePriority(properties: Map[String, String]): Boolean = this.matchesRule(priority.lowPriorityRule, properties)

  private def matchesRule(rule: Option[String], properties: Map[String, String]): Boolean = {
    rule match {
      case Some(r) =>
        val ruleJSON = Json.parse(r)
        val cnf = (ruleJSON \ "condition").asOpt[String] match {
          case Some("OR") => false
          case _ => true
        }
        implicit val reads = Writes
        val rules = (ruleJSON \ "rules").as[List[JsValue]].map(jsValue => {
          val keyValue = (jsValue \ "value").as[String].split("\\.", 2)
          val valueType = (jsValue \ "type").as[String]
          PriorityRule((jsValue \ "operator").as[String], keyValue(0), keyValue(1), valueType)
        })
        val matched = rules.filter(_.doesMatch(properties))
        if (cnf && matched.size == rules.size) {
          true
        } else if (!cnf && matched.nonEmpty) {
          true
        } else {
          false
        }
      case None => false
    }
  }
}

object Challenge {
  implicit val answerWrites: Writes[Answer] = Json.writes[Answer]
  implicit val answerReads: Reads[Answer] = Json.reads[Answer]

  val writes = new Object with ChallengeWrites
  val reads = new Object with ChallengeReads

  val DIFFICULTY_EASY = 1
  val DIFFICULTY_NORMAL = 2
  val DIFFICULTY_EXPERT = 3

  val PRIORITY_HIGH = 0
  val PRIORITY_HIGH_NAME = "High"
  val PRIORITY_MEDIUM = 1
  val PRIORITY_MEDIUM_NAME = "Medium"
  val PRIORITY_LOW = 2
  val PRIORITY_LOW_NAME = "Low"
  val priorityMap = Map(
    PRIORITY_HIGH -> PRIORITY_HIGH_NAME,
    PRIORITY_MEDIUM -> PRIORITY_MEDIUM_NAME,
    PRIORITY_LOW -> PRIORITY_LOW_NAME,
  )

  val DEFAULT_ZOOM = 13
  val MIN_ZOOM = 1
  val MAX_ZOOM = 19

  val KEY_ANSWER = "answers"
  val KEY_PARENT = "parent"

  val defaultAnswerValid = Answer(-1, "Valid")
  val defaultAnswerInvalid = Answer(-2, "Invalid")

  val STATUS_NA = 0
  val STATUS_BUILDING = 1
  val STATUS_FAILED = 2
  val STATUS_READY = 3
  val STATUS_PARTIALLY_LOADED = 4
  val STATUS_FINISHED = 5

  /**
    * This will check to make sure that the json rule is fully valid. The simple check just makes sure
    * that every rule value can be split by "." into two values.
    *
    * @param rule
    * @return
    */
  def isValidRule(rule: Option[String]): Boolean = {
    rule match {
      case Some(r) if StringUtils.isNotEmpty(r) && !StringUtils.equalsIgnoreCase(r, "{}") =>
        val ruleJSON = Json.parse(r)
        val rules = (ruleJSON \ "rules").as[List[JsValue]].map(jsValue => {
          val keyValue = (jsValue \ "value").as[String].split("\\.", 2)
          keyValue.size == 2
        })
        !rules.contains(false)
      case _ => false
    }
  }

  def emptyChallenge(ownerId: Long, parentId: Long): Challenge = Challenge(
    -1, "", DateTime.now(), DateTime.now(), None, false, None, ChallengeGeneral(-1, -1, ""),
    ChallengeCreation(), ChallengePriority(), ChallengeExtra()
  )
}
