/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import java.sql.SQLException

import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class GroupingSpec extends PlaySpec {
  "Grouping" should {
    "not generate sql if no strings provided" in {
      Grouping().sql() mustEqual ""
    }

    "generate the correct group by value" in {
      Grouping("test").sql() mustEqual "GROUP BY test"
    }

    "generate multiple groups correctly" in {
      Grouping("test1", "test2").sql() mustEqual "GROUP BY test1,test2"
    }

    "fail if provide invalid column name" in {
      intercept[SQLException] {
        Grouping("$%invalud.name").sql()
      }
    }
  }
}
