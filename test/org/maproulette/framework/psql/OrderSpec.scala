/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class OrderSpec extends PlaySpec {
  private val FIELD = "field"

  "Order" should {
    "Not set ordering if on order fields provided" in {
      Order().sql() mustEqual ""
    }

    "Set ordering if at least one order field is provided in ascending order" in {
      Order(List(FIELD), Order.ASC).sql() mustEqual s"ORDER BY $FIELD ASC"
    }

    "Set ordering if at least one order field provided in descending order" in {
      Order(List(FIELD), Order.DESC).sql() mustEqual s"ORDER BY $FIELD DESC"
    }

    "Set ordering for multiple order fields" in {
      Order(List(s"${FIELD}1", s"${FIELD}2")).sql() mustEqual s"ORDER BY ${FIELD}1,${FIELD}2 DESC"
    }
  }
}
