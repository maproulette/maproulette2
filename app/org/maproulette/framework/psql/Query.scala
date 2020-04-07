/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import anorm._
import org.maproulette.framework.psql.filter.{Filter, FilterGroup, Parameter, SQLClause}
import org.slf4j.{Logger, LoggerFactory}

/**
  * Class that handles a basic PsqlQuery options that can be used to modify the base queries in the
  * Repository. This allows a user to make minor modifications without having to actually write SQL
  * queries.
  *
  * @author mcuthbert
  */
object Query {
  val logger: Logger      = LoggerFactory.getLogger(Query.getClass)
  val PRIMARY_QUERY_KEY   = ""
  val SECONDARY_QUERY_KEY = "secondary"

  //val config:Config
  def devMode(): Boolean = false //config.isDebugMode || config.isDevMode

  def simple(
      parameters: List[Parameter[_]],
      base: String = "",
      key: SQLKey = AND(),
      paging: Paging = Paging(),
      order: Order = Order(),
      grouping: Grouping = Grouping()
  ): Query =
    Query(Filter(key, FilterGroup(key, parameters: _*)), base, paging, order, grouping)
}

case class Query(
    filter: Filter,
    base: String = "",
    paging: Paging = Paging(),
    order: Order = Order(),
    grouping: Grouping = Grouping()
) extends SQLClause {
  def build(
      baseQuery: String = ""
  )(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): SimpleSql[Row] = {
    val parameters = this.parameters()
    val sql        = this.sqlWithBaseQuery(baseQuery)
    if (parameters.nonEmpty) {
      SQL(sql).on(parameters: _*)
    } else {
      SQL(sql).asSimple[Row]()
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = filter.parameters() ++ paging.parameters()

  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String =
    this.sqlWithBaseQuery()

  def sqlWithBaseQuery(
      baseQuery: String = ""
  )(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    val filterQuery = filter.sql() match {
      case x if x.nonEmpty => s"WHERE $x"
      case x               => x
    }
    val pagingQuery = paging.sql()
    val start = base match {
      case "" => baseQuery
      case _  => base
    }

    val query =
      s"$start${this.format(filterQuery)}${this.format(grouping.sql())}${this.format(order.sql())}${this
        .format(pagingQuery)}"
    if (Query.devMode()) {
      Query.logger.debug(query)
    }
    query
  }
}

trait Base extends SQLClause
