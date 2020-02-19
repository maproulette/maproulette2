// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.data

/**
  * A class primarily of case classes that is used in place of java's enums. This is better for pattern
  * matching. Enum's in Scala are really only useful in the simpliest of cases.
  *
  * @author cuthbertm
  */
/**
  * This is the sealed base class for an Action Type, {@link Actions}
  *
  * @param id The id of the action { @see Actions}
  * @param level The level at which the action will be stored in the database. The level is set in the
  *              application config. And any action at that level and below will be written to the
  *              database, anything above will be ignored.
  */
class ActionType(id: Int, level: Int) {
  def getId: Int = id

  def getLevel: Int = level
}

/**
  * This is the sealed base class for the type of item for the action, {@link Actions}
  *
  * @param id The id of the action { @see Actions}
  */
class ItemType(id: Int) {
  val typeId = id

  def convertToItem(itemId: Long): Item with ItemType = {
    this match {
      case p: ProjectType           => new ProjectItem(itemId)
      case c: ChallengeType         => new ChallengeItem(itemId)
      case t: TaskType              => new TaskItem(itemId)
      case ta: TagType              => new TagItem(itemId)
      case u: UserType              => new UserItem(itemId)
      case s: SurveyType            => new SurveyItem(itemId)
      case vc: VirtualChallengeType => new VirtualChallengeItem(itemId)
      case b: BundleType            => new BundleItem(itemId)
    }
  }
}

trait Item {
  def itemId: Long
}

case class ProjectType() extends ItemType(Actions.ITEM_TYPE_PROJECT)

case class ChallengeType() extends ItemType(Actions.ITEM_TYPE_CHALLENGE)

case class SurveyType() extends ItemType(Actions.ITEM_TYPE_SURVEY)

case class TaskType() extends ItemType(Actions.ITEM_TYPE_TASK)

case class TagType() extends ItemType(Actions.ITEM_TYPE_TAG)

case class UserType() extends ItemType(Actions.ITEM_TYPE_USER)

case class GroupType() extends ItemType(Actions.ITEM_TYPE_GROUP)

case class VirtualChallengeType() extends ItemType(Actions.ITEM_TYPE_VIRTUAL_CHALLENGE)

case class BundleType() extends ItemType(Actions.ITEM_TYPE_BUNDLE)

class ProjectItem(override val itemId: Long) extends ProjectType with Item

class ChallengeItem(override val itemId: Long) extends ChallengeType with Item

class TaskItem(override val itemId: Long) extends TaskType with Item

class TagItem(override val itemId: Long) extends TagType with Item

class UserItem(override val itemId: Long) extends UserType with Item

class SurveyItem(override val itemId: Long) extends SurveyType with Item

class VirtualChallengeItem(override val itemId: Long) extends VirtualChallengeType with Item

class BundleItem(override val itemId: Long) extends BundleType with Item

case class Updated() extends ActionType(Actions.ACTION_TYPE_UPDATED, Actions.ACTION_LEVEL_2)

case class Created() extends ActionType(Actions.ACTION_TYPE_CREATED, Actions.ACTION_LEVEL_2)

case class Deleted() extends ActionType(Actions.ACTION_TYPE_DELETED, Actions.ACTION_LEVEL_2)

case class TaskViewed() extends ActionType(Actions.ACTION_TYPE_TASK_VIEWED, Actions.ACTION_LEVEL_3)

case class TaskStatusSet(status: Int)
    extends ActionType(Actions.ACTION_TYPE_TASK_STATUS_SET, Actions.ACTION_LEVEL_1)

case class TaskReviewStatusSet(status: Int)
    extends ActionType(Actions.ACTION_TYPE_TASK_REVIEW_STATUS_SET, Actions.ACTION_LEVEL_1)

case class TagAdded() extends ActionType(Actions.ACTION_TYPE_TAG_ADDED, Actions.ACTION_LEVEL_2)

case class TagRemoved() extends ActionType(Actions.ACTION_TYPE_TAG_REMOVED, Actions.ACTION_LEVEL_2)

case class QuestionAnswered(answerId: Long)
    extends ActionType(Actions.ACTION_TYPE_QUESTION_ANSWERED, Actions.ACTION_LEVEL_1)

object Actions {
  val ACTION_LEVEL_1 = 1
  val ACTION_LEVEL_2 = 2
  val ACTION_LEVEL_3 = 3

  val ITEM_TYPE_PROJECT                = 0
  val ITEM_TYPE_PROJECT_NAME           = "Project"
  val ITEM_TYPE_CHALLENGE              = 1
  val ITEM_TYPE_CHALLENGE_NAME         = "Challenge"
  val ITEM_TYPE_TASK                   = 2
  val ITEM_TYPE_TASK_NAME              = "Task"
  val ITEM_TYPE_TAG                    = 3
  val ITEM_TYPE_TAG_NAME               = "Tag"
  val ITEM_TYPE_SURVEY                 = 4
  val ITEM_TYPE_SURVEY_NAME            = "Survey"
  val ITEM_TYPE_USER                   = 5
  val ITEM_TYPE_USER_NAME              = "User"
  val ITEM_TYPE_GROUP                  = 6
  val ITEM_TYPE_GROUP_NAME             = "Group"
  val ITEM_TYPE_VIRTUAL_CHALLENGE      = 7
  val ITEM_TYPE_VIRTUAL_CHALLENGE_NAME = "VirtualChallenge"
  val ITEM_TYPE_BUNDLE                 = 8
  val ITEM_TYPE_BUNDLE_NAME            = "Bundle"
  val itemIDMap = Map(
    ITEM_TYPE_PROJECT           -> (ITEM_TYPE_PROJECT_NAME, ProjectType()),
    ITEM_TYPE_CHALLENGE         -> (ITEM_TYPE_CHALLENGE_NAME, ChallengeType()),
    ITEM_TYPE_TASK              -> (ITEM_TYPE_TASK_NAME, TaskType()),
    ITEM_TYPE_TAG               -> (ITEM_TYPE_TAG_NAME, TagType()),
    ITEM_TYPE_SURVEY            -> (ITEM_TYPE_SURVEY_NAME, SurveyType()),
    ITEM_TYPE_USER              -> (ITEM_TYPE_USER_NAME, UserType()),
    ITEM_TYPE_GROUP             -> (ITEM_TYPE_GROUP_NAME, GroupType()),
    ITEM_TYPE_VIRTUAL_CHALLENGE -> (ITEM_TYPE_VIRTUAL_CHALLENGE_NAME, VirtualChallengeType()),
    ITEM_TYPE_BUNDLE            -> (ITEM_TYPE_BUNDLE_NAME, BundleType())
  )

  val ACTION_TYPE_UPDATED                     = 0
  val ACTION_TYPE_UPDATED_NAME                = "Updated"
  val ACTION_TYPE_CREATED                     = 1
  val ACTION_TYPE_CREATED_NAME                = "Created"
  val ACTION_TYPE_DELETED                     = 2
  val ACTION_TYPE_DELETED_NAME                = "Deleted"
  val ACTION_TYPE_TASK_VIEWED                 = 3
  val ACTION_TYPE_TASK_VIEWED_NAME            = "Task_Viewed"
  val ACTION_TYPE_TASK_STATUS_SET             = 4
  val ACTION_TYPE_TASK_STATUS_SET_NAME        = "Task_Status_Set"
  val ACTION_TYPE_TAG_ADDED                   = 5
  val ACTION_TYPE_TAG_ADDED_NAME              = "Tag_Added"
  val ACTION_TYPE_TAG_REMOVED                 = 6
  val ACTION_TYPE_TAG_REMOVED_NAME            = "Tag_Removed"
  val ACTION_TYPE_QUESTION_ANSWERED           = 7
  val ACTION_TYPE_QUESTION_ANSWERED_NAME      = "Question_Answered"
  val ACTION_TYPE_TASK_REVIEW_STATUS_SET      = 8
  val ACTION_TYPE_TASK_REVIEW_STATUS_SET_NAME = "Task_Review_Status_Set"

  val actionIDMap = Map(
    ACTION_TYPE_UPDATED                -> ACTION_TYPE_UPDATED_NAME,
    ACTION_TYPE_CREATED                -> ACTION_TYPE_CREATED_NAME,
    ACTION_TYPE_DELETED                -> ACTION_TYPE_DELETED_NAME,
    ACTION_TYPE_TASK_VIEWED            -> ACTION_TYPE_TASK_VIEWED_NAME,
    ACTION_TYPE_TASK_STATUS_SET        -> ACTION_TYPE_TASK_STATUS_SET_NAME,
    ACTION_TYPE_TAG_ADDED              -> ACTION_TYPE_TAG_ADDED_NAME,
    ACTION_TYPE_TAG_REMOVED            -> ACTION_TYPE_TAG_REMOVED_NAME,
    ACTION_TYPE_QUESTION_ANSWERED      -> ACTION_TYPE_QUESTION_ANSWERED_NAME,
    ACTION_TYPE_TASK_REVIEW_STATUS_SET -> ACTION_TYPE_TASK_REVIEW_STATUS_SET_NAME
  )

  /**
    * Validates whether the provided id is actually an action type id
    *
    * @param actionType The id to test
    * @return true if valid action type id
    */
  def validActionType(actionType: Int): Boolean = actionIDMap.contains(actionType)

  /**
    * Validates the provided action type name
    *
    * @param actionType The action type name to validate
    * @return true if valid action type
    */
  def validActionTypeName(actionType: String): Boolean = getActionID(actionType) match {
    case Some(_) => true
    case None    => false
  }

  /**
    * Based on a string will return the action id that it matches, None otherwise
    *
    * @param action The string to match against
    * @return Option[Int] if found, None otherwise.
    */
  def getActionID(action: String): Option[Int] =
    actionIDMap.find(_._2.equalsIgnoreCase(action)) match {
      case Some(a) => Some(a._1)
      case None    => None
    }

  /**
    * Validates whether the provided id is actually an item type id
    *
    * @param itemType The id to test
    * @return true if valid item type id
    */
  def validItemType(itemType: Int): Boolean = actionIDMap.contains(itemType)

  /**
    * Validates the provided item name
    *
    * @param itemType The item type name to test
    * @return true if a valid item type
    */
  def validItemTypeName(itemType: String): Boolean = getTypeID(itemType) match {
    case Some(_) => true
    case None    => false
  }

  /**
    * Based on a string will return the item type id that the string matches, None otherwise
    *
    * @param itemType The string to match against
    * @return Option[Int] if found, None otherwise
    */
  def getTypeID(itemType: String): Option[Int] =
    itemIDMap.find(_._2._1.equalsIgnoreCase(itemType)) match {
      case Some(it) => Some(it._1)
      case None     => None
    }

  /**
    * Based on an id will return the Item type name it matches, None otherwise
    *
    * @param itemType The id to find
    * @return Option[String] if found, None otherwise
    */
  def getTypeName(itemType: Int): Option[String] = itemIDMap.get(itemType) match {
    case Some(it) => Some(it._1)
    case None     => None
  }

  /**
    * Gets the ItemType based on the Item Type Id
    *
    * @param itemType The item type id
    * @return The ItemType matching the supplied item type id
    */
  def getItemType(itemType: Int): Option[ItemType] = itemIDMap.get(itemType) match {
    case Some(it) => Some(it._2)
    case None     => None
  }

  /**
    * Gets the ItemType based on the Item Type name
    *
    * @param itemType The item type name
    * @return The ItemType matching the supplied item type name
    */
  def getItemType(itemType: String): Option[ItemType] =
    itemIDMap.find(_._2._1.equalsIgnoreCase(itemType)) match {
      case Some(a) => Some(a._2._2)
      case None    => None
    }

  /**
    * Based on an id will return the action name that the id matches, None otherwise
    *
    * @param action The id to match against
    * @return Option[String] if found, None otherwise.
    */
  def getActionName(action: Int): Option[String] = actionIDMap.get(action)
}
