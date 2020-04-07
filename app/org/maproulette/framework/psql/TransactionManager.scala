/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import java.sql.Connection

import play.api.db.Database

/**
  * @author cuthbertm
  */
trait TransactionManager {
  implicit val db: Database

  def withMRConnection[T](block: Connection => T)(implicit conn: Option[Connection] = None): T =
    conn match {
      case Some(c) => block(c)
      case None =>
        this.db.withConnection { implicit c =>
          block(c)
        }
    }

  def withMRTransaction[T](block: Connection => T)(implicit conn: Option[Connection] = None): T =
    conn match {
      case Some(c) => block(c)
      case None =>
        this.db.withTransaction { implicit c =>
          block(c)
        }
    }
}
