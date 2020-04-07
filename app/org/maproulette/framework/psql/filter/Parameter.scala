/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql.filter

import anorm.{NamedParameter, ToParameterValue}
import org.joda.time.DateTime
import org.maproulette.framework.psql.filter.Operator.Operator
import org.maproulette.framework.psql.{Query, SQLUtils}

/**
  * Filter Parameters are generally be used for filter select queries using some basic filtering. It
  * also can be used for updating objects, as the SET clause using a very similar structure to the
  * EQ operator.
  *
  * @author mcuthbert
  */
trait Parameter[T] extends SQLClause {
  val key: String
  val value: T
  val operator: Operator        = Operator.EQ
  val negate: Boolean           = false
  val useValueDirectly: Boolean = false

  def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (value.isInstanceOf[Iterable[_]] && value.asInstanceOf[Iterable[_]].isEmpty) {
      ""
    } else if (operator == Operator.CUSTOM) {
      SQLUtils.testColumnName(key)
      val negation = if (negate) {
        "NOT "
      } else {
        ""
      }
      s"$negation$key$value"
    } else {
      val directValue = if (useValueDirectly) {
        // if we are using the value directly, then we must at least try to convert to a string
        Some(value.toString)
      } else {
        None
      }
      Operator.format(key, operator, negate, directValue)
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = {
    if ((value.isInstanceOf[Iterable[_]] && value
          .asInstanceOf[Iterable[_]]
          .isEmpty) || operator == Operator.CUSTOM || operator == Operator.NULL || useValueDirectly) {
      List.empty
    } else {
      List(SQLUtils.buildNamedParameter(s"$parameterKey$key", value))
    }
  }
}

/**
  * Conditional Filter that will only apply the filter if a condition is met
  *
  * @param key The column to filter on
  * @param value The value to use in the column filter
  * @param operator The operator to use for the equation
  * @param negate Whether to negate the operation
  * @param useValueDirectly use the value directly in the operation, instead of using a NamedParameter
  * @tparam T
  */
case class BaseParameter[T](
    override val key: String,
    override val value: T,
    override val operator: Operator = Operator.EQ,
    override val negate: Boolean = false,
    override val useValueDirectly: Boolean = false
) extends Parameter[T]

case class ConditionalFilterParameter[T](
    filter: Parameter[T],
    includeOnlyIfTrue: Boolean = false
) extends Parameter[T] {
  override val key: String = filter.key
  override val value: T    = filter.value

  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String =
    if (includeOnlyIfTrue) {
      filter.sql()
    } else {
      ""
    }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    if (includeOnlyIfTrue) {
      filter.parameters()
    } else {
      List.empty
    }
}

case class DateParameter(
    override val key: String,
    override val value: DateTime,
    value2: DateTime,
    override val operator: Operator = Operator.EQ,
    override val negate: Boolean = false
) extends Parameter[DateTime] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (operator == Operator.BETWEEN) {
      SQLUtils.testColumnName(key)
      val negation = if (negate) {
        "NOT "
      } else {
        ""
      }
      s"$key::DATE ${negation}BETWEEN {$parameterKey${key}_date1} AND {$parameterKey${key}_date2}"
    } else {
      super.sql()
    }
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = {
    if (operator == Operator.BETWEEN) {
      List(
        Symbol(s"$parameterKey${key}_date1") -> ToParameterValue
          .apply[DateTime](p = SQLUtils.toStatement)
          .apply(value),
        Symbol(s"$parameterKey${key}_date2") -> ToParameterValue
          .apply[DateTime](p = SQLUtils.toStatement)
          .apply(value2)
      )
    } else {
      super.parameters()
    }
  }
}

/**
  * Allows you to do something like 'column' IN ( subQuery ), need to add support for exists later
  *
  * @param key The column that you are checking against
  * @param value The sub query to check against, the subQuery needs a base query included
  */
case class SubQueryFilter(
    override val key: String,
    override val value: Query,
    override val negate: Boolean = false,
    override val operator: Operator = Operator.IN
) extends Parameter[Query] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    val filterValue = if (operator == Operator.IN || operator == Operator.EXISTS) {
      Some(value.sql()(this.getParameterKey))
    } else {
      Some(s"(${value.sql()(this.getParameterKey)})")
    }
    Operator.format(key, operator, negate, filterValue)
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = value.parameters()(this.getParameterKey)

  // This may work for a single subquery, but there may be issues if you have more than one subquery
  // as it will always choose "SECONDARY" as it parameterKey which might not make the keys unique
  // for the parameter
  def getParameterKey(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    if (parameterKey.equals(Query.PRIMARY_QUERY_KEY)) {
      Query.SECONDARY_QUERY_KEY
    } else {
      parameterKey
    }

  }
}

/**
  * Custom filter that will simply use the sql included in the filter
  *
  * @param key
  */
case class CustomParameter(override val key: String, override val value: String = "")
    extends Parameter[String] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = key

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] = List.empty
}

case class FuzzySearchParameter(
    override val key: String,
    override val value: String,
    levenshsteinScore: Int = FilterParameter.DEFAULT_LEVENSHSTEIN_SCORE,
    metaphoneSize: Int = FilterParameter.DEFAULT_METAPHONE_SIZE
) extends Parameter[String] {
  override def sql()(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): String = {
    SQLUtils.testColumnName(key)
    val score = if (levenshsteinScore > 0) {
      levenshsteinScore
    } else {
      FilterParameter.DEFAULT_LEVENSHSTEIN_SCORE
    }
    s"""($key <> '' AND
      (LEVENSHTEIN(LOWER($key), LOWER({$parameterKey$key})) < $score OR
      METAPHONE(LOWER($key), 4) = METAPHONE(LOWER({$parameterKey$key}), $metaphoneSize) OR
      SOUNDEX(LOWER($key)) = SOUNDEX(LOWER({$parameterKey$key})))
      )"""
  }

  override def parameters()(
      implicit parameterKey: String = Query.PRIMARY_QUERY_KEY
  ): List[NamedParameter] =
    List(Symbol(s"$parameterKey${key}") -> value)
}

object FilterParameter {
  val DEFAULT_LEVENSHSTEIN_SCORE = 3
  val DEFAULT_METAPHONE_SIZE     = 4

  // Helper method to build a Conditional Filter
  def conditional[T](
      key: String,
      value: T,
      operator: Operator = Operator.EQ,
      negate: Boolean = false,
      useValueDirectly: Boolean = false,
      includeOnlyIfTrue: Boolean = false
  )(implicit parameterKey: String = Query.PRIMARY_QUERY_KEY): ConditionalFilterParameter[T] =
    ConditionalFilterParameter(
      BaseParameter(key, value, operator, negate, useValueDirectly),
      includeOnlyIfTrue
    )
}
