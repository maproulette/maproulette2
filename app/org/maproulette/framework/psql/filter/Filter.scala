/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql.filter

import anorm.NamedParameter
import org.maproulette.framework.psql.{AND, Query, SQLKey}

/**
  * This class handles any filters that you want to add onto a query. Repositories only handle one
  * table, joins are special and would require special code and logic to handle correctly, however
  * joins are actively discouraged due to the complexity and the potential for large performance
  * costs.
  *
  * @author mcuthbert
  */
trait SQLClause {
  def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String
  def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = List.empty

  // Simple function that makes sure I don't have a bunch of empty spaces at the end of the query.
  // It doesn't really matter, but makes it easier to look at.
  protected def format(value: String): String =
    if (value.isEmpty) {
      ""
    } else {
      s" $value"
    }
}

case class FilterGroup(key: SQLKey, params: Parameter[_]*) extends SQLClause {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String =
    params
      .flatMap(param =>
        param.sql() match {
          case ""  => None
          case sql => Some(sql)
        }
      )
      .mkString(s" ${key.getSQLKey()} ")

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    params.flatMap(param => param.parameters()).toList
}

case class Filter(key: SQLKey, groups: FilterGroup*) extends SQLClause {
  def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    val groupSeparator = if (groups.size == 1) {
      ("", "")
    } else {
      ("(", ")")
    }
    groups
      .flatMap(group =>
        group.sql() match {
          case ""  => None
          case sql => Some(s"${groupSeparator._1}$sql${groupSeparator._2}")
        }
      )
      .mkString(s" ${key.getSQLKey()} ")
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    groups.flatMap(param => param.parameters()).toList
}

object Filter {
  def simple(parameters: List[Parameter[_]], key: SQLKey = AND()): Filter =
    Filter(key, FilterGroup(key, parameters: _*))
}
