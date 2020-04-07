/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import org.maproulette.exception.InvalidException
import org.maproulette.framework.psql.filter.Operator
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class FilterOperatorSpec extends PlaySpec {
  private val KEY                   = "key"
  private implicit val parameterKey = "TestKey"

  "FilterOperator" should {
    "format EQ operator correctly" in {
      Operator.format(KEY, Operator.EQ) mustEqual s"$KEY = {$parameterKey$KEY}"
    }

    "format NOT EQ operator correctly" in {
      Operator.format(KEY, Operator.EQ, true) mustEqual s"NOT $KEY = {$parameterKey$KEY}"
    }

    "format GT operator correctly" in {
      Operator.format(KEY, Operator.GT) mustEqual s"$KEY > {$parameterKey$KEY}"
    }

    "format NOT GT operator correctly" in {
      Operator.format(KEY, Operator.GT, true) mustEqual s"NOT $KEY > {$parameterKey$KEY}"
    }

    "format GTE operator correctly" in {
      Operator.format(KEY, Operator.GTE) mustEqual s"$KEY >= {$parameterKey$KEY}"
    }

    "format NOT GTE operator correctly" in {
      Operator.format(KEY, Operator.GTE, true) mustEqual s"NOT $KEY >= {$parameterKey$KEY}"
    }

    "format LT operator correctly" in {
      Operator.format(KEY, Operator.LT) mustEqual s"$KEY < {$parameterKey$KEY}"
    }

    "format NOT LT operator correctly" in {
      Operator.format(KEY, Operator.LT, true) mustEqual s"NOT $KEY < {$parameterKey$KEY}"
    }

    "format LTE operator correctly" in {
      Operator.format(KEY, Operator.LTE) mustEqual s"$KEY <= {$parameterKey$KEY}"
    }

    "format NOT LTE operator correctly" in {
      Operator.format(KEY, Operator.LTE, negate = true) mustEqual s"NOT $KEY <= {$parameterKey$KEY}"
    }

    "format IN operator correctly" in {
      Operator.format(KEY, Operator.IN) mustEqual s"$KEY IN ({$parameterKey$KEY})"
    }

    "format NOT IN operator correctly" in {
      Operator.format(KEY, Operator.IN, negate = true) mustEqual s"NOT $KEY IN ({$parameterKey$KEY})"
    }

    "format LIKE operator correctly" in {
      Operator.format(KEY, Operator.LIKE) mustEqual s"$KEY LIKE {$parameterKey$KEY}"
    }

    "format NOT LIKE operator correctly" in {
      Operator.format(KEY, Operator.LIKE, negate = true) mustEqual s"NOT $KEY LIKE {$parameterKey$KEY}"
    }

    "format ILIKE operator correctly" in {
      Operator.format(KEY, Operator.ILIKE) mustEqual s"$KEY ILIKE {$parameterKey$KEY}"
    }

    "format NOT ILIKE operator correctly" in {
      Operator.format(KEY, Operator.ILIKE, negate = true) mustEqual s"NOT $KEY ILIKE {$parameterKey$KEY}"
    }

    "format SIMILAR TO operator correctly" in {
      Operator.format(KEY, Operator.SIMILAR_TO) mustEqual s"$KEY SIMILAR TO {$parameterKey$KEY}"
    }

    "format NOT SIMILAR TO operator correctly" in {
      Operator.format(KEY, Operator.SIMILAR_TO, negate = true) mustEqual s"NOT $KEY SIMILAR TO {$parameterKey$KEY}"
    }

    "format EXISTS operator correctly" in {
      Operator.format(KEY, Operator.EXISTS) mustEqual s"EXISTS ({$parameterKey$KEY})"
    }

    "format NOT EXISTS operator correctly" in {
      Operator.format(KEY, Operator.EXISTS, negate = true) mustEqual s"NOT EXISTS ({$parameterKey$KEY})"
    }

    "format rightValue correctly if set" in {
      Operator.format(KEY, Operator.EQ, value = Some("test.key")) mustEqual s"$KEY = test.key"
    }

    "between should throw invalidException" in {
      intercept[InvalidException] {
        Operator.format(KEY, Operator.BETWEEN)
      }
    }
  }
}
