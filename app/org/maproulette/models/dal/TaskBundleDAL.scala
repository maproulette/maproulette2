/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.models.dal

import java.sql.Connection

import anorm._
import javax.inject.{Inject, Singleton}
import org.maproulette.data.TaskType
import org.maproulette.exception.InvalidException
import org.maproulette.framework.model.User
import org.maproulette.framework.psql.TransactionManager
import org.maproulette.models.dal.mixin.Locking
import org.maproulette.models.utils.DALHelper
import org.maproulette.models.{Task, TaskBundle}
import org.maproulette.permissions.Permission
import org.slf4j.LoggerFactory
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class TaskBundleDAL @Inject() (
    val db: Database,
    permission: Permission,
    taskDAL: TaskDAL,
    challengeDAL: ChallengeDAL
) extends DALHelper
    with TransactionManager
    with Locking[Task] {
  protected val logger = LoggerFactory.getLogger(this.getClass)

  /**
    * Creates a new task bundle with the given tasks, assigning ownership of
    * the bundle to the given user
    *
    * @param user    The user who is to own the bundle
    * @param name    The name of the task bundle
    * @param taskIds The tasks to be added to the bundle
    */
  def createTaskBundle(user: User, name: String, taskIds: List[Long])(
      implicit c: Connection = null
  ): TaskBundle = {
    this.withMRTransaction { implicit c =>
      val lockedTasks = this.withListLocking(user, Some(TaskType())) { () =>
        this.taskDAL.retrieveListById(-1, 0)(taskIds)
      }

      if (lockedTasks.length < 1) {
        throw new InvalidException("Must be at least one task to bundle.")
      }

      val challengeId = lockedTasks.head.parent
      // Verify tasks
      // 1. Must belong to same challenge
      // 2. suggested Fix tasks not allowed
      for (task <- lockedTasks) {
        if (task.suggestedFix.isDefined) {
          throw new InvalidException("Suggested Fix tasks cannot be bundled.")
        }
        if (task.parent != challengeId) {
          throw new InvalidException("All tasks in the bundle must be part of the same challenge.")
        }
        if (task.bundleId.isDefined) {
          throw new InvalidException(
            "Task " + task.id + " already assigned to bundle: " +
              task.bundleId.getOrElse("") + "."
          )
        }
      }

      val rowId =
        SQL"""INSERT INTO bundles (owner_id, name) VALUES (${user.id}, ${name})""".executeInsert()
      rowId match {
        case Some(bundleId) =>
          val sqlQuery =
            s"""INSERT INTO task_bundles (task_id, bundle_id) VALUES ({taskId}, $bundleId)"""
          val parameters = lockedTasks.map(task => {
            Seq[NamedParameter]("taskId" -> task.id)
          })
          BatchSql(sqlQuery, parameters.head, parameters.tail: _*).execute()
          TaskBundle(bundleId, user.id, lockedTasks.map(task => {
            task.id
          }), Some(lockedTasks))
        case None =>
          throw new Exception("Bundle creation failed")
      }
    }
  }

  /**
    * Removes tasks from a bundle.
    *
    * @param bundleId The id of the bundle
    */
  def unbundleTasks(user: User, bundleId: Long, taskIds: List[Long])(
      implicit c: Connection = null
  ): TaskBundle = {
    this.withMRConnection { implicit c =>
      val bundle = this.getTaskBundle(user, bundleId)
      if (!user.isSuperUser && bundle.ownerId != user.id) {
        throw new IllegalAccessException(
          "Only a super user or the original user can delete this bundle."
        )
      }

      // Unset any bundle_id on individual tasks (this is set when task is completed)
      SQL(s"""UPDATE tasks SET bundle_id = NULL
              WHERE bundle_id = {bundleId}
              AND (is_bundle_primary != true OR is_bundle_primary is NULL)
              AND id IN ({inList})""")
        .on(
          Symbol("bundleId") -> bundleId,
          Symbol("inList")   -> ToParameterValue.apply[List[Long]].apply(taskIds)
        )
        .executeUpdate()

      // Remove task from bundle join table.
      val tasks = this.getTaskBundle(user, bundleId).tasks match {
        case Some(t) =>
          for (task <- t) {
            if (!task.isBundlePrimary.getOrElse(false)) {
              taskIds.find(id => id == task.id) match {
                case Some(_) =>
                  SQL(s"""DELETE FROM task_bundles
                          WHERE bundle_id = ${bundleId} AND task_id = ${task.id}""").executeUpdate()

                  try {
                    this.unlockItem(user, task)
                  } catch {
                    case e: Exception => this.logger.warn(e.getMessage)
                  }
                case None => // do nothing
              }
            }
          }
        case None => // No tasks in bundle.
      }

      this.getTaskBundle(user, bundleId)
    }
  }

  /**
    * Deletes a task bundle.
    *
    * @param bundleId The id of the bundle
    */
  def deleteTaskBundle(user: User, bundleId: Long, primaryTaskId: Option[Long] = None)(
      implicit c: Connection = null
  ): Unit = {
    this.withMRConnection { implicit c =>
      val bundle = this.getTaskBundle(user, bundleId)
      if (!user.isSuperUser && bundle.ownerId != user.id) {
        val challengeId = bundle.tasks.getOrElse(List()).head.parent
        val challenge   = this.challengeDAL.retrieveById(challengeId)
        this.permission.hasObjectWriteAccess(challenge.get, user)
      }

      SQL(
        "UPDATE tasks SET bundle_id = NULL, is_bundle_primary = NULL WHERE bundle_id = {bundleId}"
      ).on(Symbol("bundleId") -> bundleId).executeUpdate()

      if (primaryTaskId != None) {
        // unlock tasks (everything but the primary task id)
        val tasks = this.getTaskBundle(user, bundleId).tasks match {
          case Some(t) =>
            for (task <- t) {
              if (task.id != primaryTaskId.getOrElse(0)) {
                try {
                  this.unlockItem(user, task)
                } catch {
                  case e: Exception => this.logger.warn(e.getMessage)
                }
              }
            }
          case None => // no tasks in bundle
        }
      }

      SQL("DELETE FROM bundles WHERE id = {bundleId}")
        .on(Symbol("bundleId") -> bundleId)
        .executeUpdate()
    }
  }

  /**
    * Fetches a list of tasks associated with the given bundle id.
    *
    * @param bundleId The id of the bundle
    */
  def getTaskBundle(user: User, bundleId: Long)(implicit c: Connection = null): TaskBundle = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.taskDAL.retrieveColumnsWithReview} FROM ${this.taskDAL.tableName}
            INNER JOIN task_bundles tb on tasks.id = tb.task_id
            LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
            WHERE tb.bundle_id = {bundleId}"""
      val tasks = SQL(query).on(Symbol("bundleId") -> bundleId).as(this.taskDAL.parser.*)
      TaskBundle(bundleId, user.id, tasks.map(task => {
        task.id
      }), Some(tasks))
    }
  }
}
