package org.maproulette.models.dal.mixin

import java.sql.Connection

import anorm._
import org.maproulette.data.ItemType
import org.maproulette.exception.LockedException
import org.maproulette.models.BaseObject
import org.maproulette.models.utils.{DALHelper, TransactionManager}
import org.maproulette.session.User

/**
  * @author mcuthbert
  */
trait Locking[T <: BaseObject[_]] extends TransactionManager {
  this: DALHelper =>

  /**
    * Unlocks an item in the database
    *
    * @param user The user requesting to unlock the item
    * @param item The item being unlocked
    * @param c    A sql connection that is implicitly passed in from the calling function, this is an
    *             implicit function because this will always be called from within the code and never
    *             directly from an API call
    * @return true if successful
    */
  def unlockItem(user: User, item: T)(implicit c: Option[Connection] = None): Int =
    this.withMRTransaction { implicit c =>
      val checkQuery =
        s"""SELECT user_id FROM locked WHERE item_id = {itemId} AND item_type = ${item.itemType.typeId} FOR UPDATE"""
      SQL(checkQuery)
        .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
        .as(SqlParser.long("user_id").singleOpt) match {
        case Some(id) =>
          if (id == user.id) {
            val query =
              s"""DELETE FROM locked WHERE user_id = ${user.id} AND item_id = {itemId} AND item_type = ${item.itemType.typeId}"""
            SQL(query)
              .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
              .executeUpdate()
          } else {
            throw new LockedException(
              s"Item [${item.id}] currently locked by different user. [${user.id}"
            )
          }
        case None =>
          throw new LockedException(s"Item [${item.id}] trying to unlock does not exist.")
      }
    }

  /**
    * Refreshes an existing lock on an item in the database, extending its allowed duration
    *
    * @param user The user requesting to refresh the lock (and who must also own it)
    * @param item The locked item
    * @param c    A sql connection that is implicitly passed in from the calling function, this is an
    *             implicit function because this will always be called from within the code and never
    *             directly from an API call
    * @return true if successful
    */
  def refreshItemLock(user: User, item: T)(implicit c: Option[Connection] = None): Int =
    this.withMRTransaction { implicit c =>
      val checkQuery =
        s"""SELECT user_id FROM locked WHERE item_id = {itemId} AND item_type = ${item.itemType.typeId} FOR UPDATE"""
      SQL(checkQuery)
        .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
        .as(SqlParser.long("user_id").singleOpt) match {
        case Some(id) =>
          if (id == user.id) {
            val query =
              s"""UPDATE locked set locked_time=NOW() WHERE user_id = ${user.id} AND item_id = {itemId} AND item_type = ${item.itemType.typeId}"""
            SQL(query)
              .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
              .executeUpdate()
          } else {
            throw new LockedException(
              s"Item [${item.id}] currently locked by different user. [${user.id}]"
            )
          }
        case None => throw new LockedException(s"Lock on item [${item.id}] does not exist.")
      }
    }

  /**
    * Method to lock all items returned in the lambda block. It will first all unlock all items
    * that have been locked by the user.
    *
    * @param user     The user making the request
    * @param itemType The type of item that will be locked
    * @param block    The block of code to execute inbetween unlocking and locking items
    * @param c        The connection
    * @return List of objects
    */
  def withListLocking(user: User, itemType: Option[ItemType] = None)(
      block: () => List[T]
  )(implicit c: Option[Connection] = None): List[T] = {
    this.withMRTransaction { implicit c =>
      // if a user is requesting a task, then we can unlock all other tasks for that user, as only a single
      // task can be locked at a time
      this.unlockAllItems(user, itemType)
      val results = block()
      // once we have the tasks, we need to lock each one, if any fail to lock we just remove
      // them from the list. A guest user will not lock any tasks, but when logged in will be
      // required to refetch the current task, and if it is locked, then will have to get another
      // task
      if (!user.guest) {
        val resultList = results.filter(lockItem(user, _) > 0)
        if (resultList.isEmpty) {
          List[T]()
        }
        resultList
      } else {
        results
      }
    }
  }

  /**
    * Method to lock a single optional item returned in a lambda block. It will first unlock all items
    * that have been locked by the user
    *
    * @param user     The user making the request
    * @param itemType The type of item that will be locked
    * @param block    The block of code to execute inbetween unlocking and locking items
    * @param c        The connection
    * @return Option object
    */
  def withSingleLocking(user: User, itemType: Option[ItemType] = None)(
      block: () => Option[T]
  )(implicit c: Option[Connection] = None): Option[T] = {
    this.withMRTransaction { implicit c =>
      // if a user is requesting a task, then we can unlock all other tasks for that user, as only a single
      // task can be locked at a time
      this.unlockAllItems(user, itemType)
      val result = block()
      if (!user.guest) {
        result match {
          case Some(r) => lockItem(user, r)
          case None    => // ignore
        }
      }
      result
    }
  }

  /**
    * Locks an item in the database.
    *
    * @param user The user requesting the lock
    * @param item The item wanting to be locked
    * @param c    A sql connection that is implicitly passed in from the calling function, this is an
    *             implicit function because this will always be called from within the code and never
    *             directly from an API call
    * @return true if successful
    */
  def lockItem(user: User, item: T)(implicit c: Option[Connection] = None): Int =
    this.withMRTransaction { implicit c =>
      // first check to see if the item is already locked
      val checkQuery =
        s"""SELECT user_id FROM locked WHERE item_id = {itemId} AND item_type = ${item.itemType.typeId} FOR UPDATE"""
      SQL(checkQuery)
        .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
        .as(SqlParser.long("user_id").singleOpt) match {
        case Some(id) =>
          if (id == user.id) {
            val query =
              s"UPDATE locked SET locked_time = NOW() WHERE user_id = ${user.id} AND item_id = {itemId} AND item_type = ${item.itemType.typeId}"
            SQL(query)
              .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
              .executeUpdate()
          } else {
            0
            //throw new LockedException(s"Could not acquire lock on object [${item.id}, already locked by user [$id]")
          }
        case None =>
          val query =
            s"INSERT INTO locked (item_type, item_id, user_id) VALUES (${item.itemType.typeId}, {itemId}, ${user.id})"
          SQL(query)
            .on(Symbol("itemId") -> ParameterValue.toParameterValue(item.id)(p = keyToStatement))
            .executeUpdate()
      }
    }

  /**
    * Unlocks all the items that are associated with the current user
    *
    * @param user The user
    * @param c    an implicit connection, this function should generally be executed in conjunction
    *             with other requests
    * @return Number of locks removed
    */
  def unlockAllItems(user: User, itemType: Option[ItemType] = None)(
      implicit c: Option[Connection] = None
  ): Int =
    this.withMRTransaction { implicit c =>
      itemType match {
        case Some(it) =>
          SQL"""DELETE FROM locked WHERE user_id = ${user.id} AND item_type = ${it.typeId}"""
            .executeUpdate()
        case None =>
          SQL"""DELETE FROM locked WHERE user_id = ${user.id}""".executeUpdate()
      }
    }
}
