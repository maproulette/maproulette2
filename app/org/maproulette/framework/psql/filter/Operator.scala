/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql.filter

import org.maproulette.exception.InvalidException
import org.maproulette.framework.psql.SQLUtils

/**
  * @author mcuthbert
  */
object Operator extends Enumeration {
  type Operator = Value
  val EQ, GT, GTE, LT, LTE, IN, LIKE, ILIKE, CUSTOM, BETWEEN, NULL, SIMILAR_TO, EXISTS = Value

  def format(
      key: String,
      operator: Operator,
      negate: Boolean = false,
      value: Option[String] = None
  )(implicit parameterKey: String): String = {
    SQLUtils.testColumnName(key)
    val negation = if (negate) {
      "NOT "
    } else {
      ""
    }
    val rightValue = value match {
      case Some(v) => v
      case None    => s"{$parameterKey$key}"
    }
    operator match {
      case EQ         => s"$negation$key = $rightValue"
      case GT         => s"$negation$key > $rightValue"
      case GTE        => s"$negation$key >= $rightValue"
      case LT         => s"$negation$key < $rightValue"
      case LTE        => s"$negation$key <= $rightValue"
      case IN         => s"$negation$key IN ($rightValue)"
      case LIKE       => s"$negation$key LIKE $rightValue"
      case ILIKE      => s"$negation$key ILIKE $rightValue"
      case NULL       => s"$key IS ${negation}NULL"
      case SIMILAR_TO => s"$negation$key SIMILAR TO $rightValue"
      case EXISTS     => s"${negation}EXISTS ($rightValue)"
      case BETWEEN =>
        throw new InvalidException("Between operator not supported by standard filter")
    }
  }
}
