/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import anorm.NamedParameter
import org.maproulette.framework.psql.filter.SQLClause

/**
  * @author mcuthbert
  */
case class Grouping(groups: String*) extends SQLClause {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (groups.isEmpty) {
      ""
    } else {
      groups.foreach(SQLUtils.testColumnName)
      s"GROUP BY ${groups.mkString(",")}"
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = List.empty
}
