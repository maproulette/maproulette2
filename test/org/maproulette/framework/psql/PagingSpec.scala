/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import anorm.NamedParameter
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class PagingSpec extends PlaySpec {
  "Paging" should {
    "not be set if limit set to zero" in {
      val paging = Paging()
      paging.sql() mustEqual ""
      paging.parameters() mustEqual List.empty
    }

    "set to limit greater than zero" in {
      val paging = Paging(10000)
      paging.sql() mustEqual "LIMIT {limit} OFFSET {offset}"
      val params = paging.parameters()
      params.size mustEqual 2
      params.head mustEqual NamedParameter("limit", 10000)
      params.tail.head mustEqual NamedParameter("offset", 0)
    }

    "set to limit and offset multiplied by limit" in {
      val paging = Paging(100, 5)
      paging.sql() mustEqual "LIMIT {limit} OFFSET {offset}"
      val params = paging.parameters()
      params.size mustEqual 2
      params.head mustEqual NamedParameter("limit", 100)
      params.tail.head mustEqual NamedParameter("offset", 500)
    }
  }
}
