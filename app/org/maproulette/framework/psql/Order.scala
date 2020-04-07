/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import anorm.NamedParameter
import org.maproulette.framework.psql.filter.SQLClause

/**
  * Basic class to handle any ordering that needs to be added to the query
  *
  * @author mcuthbert
  */
case class Order(fields: List[String] = List.empty, direction: Int = Order.DESC) extends SQLClause {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (fields.nonEmpty) {
      // test to make sure each field is a valid column name
      fields.foreach(SQLUtils.testColumnName)
      s"ORDER BY ${fields.mkString(",")} ${if (direction == Order.ASC) {
        "ASC"
      } else {
        "DESC"
      }}"
    } else {
      ""
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = List.empty
}

object Order {
  val ASC: Int  = -1
  val DESC: Int = 1

  def simple(field: String, direction: Int = Order.DESC): Order = Order(List(field), direction)
}
