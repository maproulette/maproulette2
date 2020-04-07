/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.psql

import org.maproulette.framework.psql.filter.{BaseParameter, Filter, FilterGroup, FilterParameter}
import org.scalatestplus.play.PlaySpec

/**
  * @author mcuthbert
  */
class FilterSpec extends PlaySpec {
  val KEY    = "KEY"
  val VALUE  = "VALUE"
  val KEY2   = "KEY2"
  val VALUE2 = "VALUE2"

  "FilterGroup" should {
    "Create a group of AND filters parameters" in {
      val group =
        FilterGroup(AND(), BaseParameter(KEY, VALUE), BaseParameter(KEY2, VALUE2))
      group.sql() mustEqual "KEY = {KEY} AND KEY2 = {KEY2}"
      val params = group.parameters()
      params.size mustEqual 2
      params.head mustEqual SQLUtils.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual SQLUtils.buildNamedParameter(KEY2, VALUE2)
    }

    "Create a group of OR filter parameters" in {
      val group =
        FilterGroup(OR(), BaseParameter(KEY, VALUE), BaseParameter(KEY2, VALUE2))
      group.sql() mustEqual "KEY = {KEY} OR KEY2 = {KEY2}"
      val params = group.parameters()
      params.size mustEqual 2
      params.head mustEqual SQLUtils.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual SQLUtils.buildNamedParameter(KEY2, VALUE2)
    }
  }

  "Filter" should {
    "generate standard sql for AND parameters" in {
      val filter = Filter.simple(
        List(BaseParameter(KEY, VALUE), BaseParameter(KEY2, VALUE2)),
        AND()
      )
      filter.sql() mustEqual "KEY = {KEY} AND KEY2 = {KEY2}"
      val params = filter.parameters()
      params.size mustEqual 2
      params.head mustEqual SQLUtils.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual SQLUtils.buildNamedParameter(KEY2, VALUE2)
    }

    "generate standard sql for OR parameters" in {
      val filter = Filter.simple(
        List(BaseParameter(KEY, VALUE), BaseParameter(KEY2, VALUE2)),
        OR()
      )
      filter.sql() mustEqual "KEY = {KEY} OR KEY2 = {KEY2}"
      val params = filter.parameters()
      params.size mustEqual 2
      params.head mustEqual SQLUtils.buildNamedParameter(KEY, VALUE)
      params.tail.head mustEqual SQLUtils.buildNamedParameter(KEY2, VALUE2)
    }

    "generate filter groups for AND parameters" in {
      val filter = this.genericFilter(AND(), AND(), AND())
      filter.sql() mustEqual "(KEY = {KEY} AND KEY_1 = {KEY_1}) AND (KEY2 = {KEY2} AND KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
      params.head mustEqual SQLUtils.buildNamedParameter(KEY, VALUE)
      params(1) mustEqual SQLUtils.buildNamedParameter(s"${KEY}_1", VALUE)
      params(2) mustEqual SQLUtils.buildNamedParameter(KEY2, VALUE2)
      params(3) mustEqual SQLUtils.buildNamedParameter(s"${KEY2}_2", VALUE2)
    }

    "generate filter groups for OR parameters" in {
      val filter = this.genericFilter(OR(), OR(), OR())
      filter
        .sql() mustEqual "(KEY = {KEY} OR KEY_1 = {KEY_1}) OR (KEY2 = {KEY2} OR KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }

    "generate filter groups for AND/OR/AND parameters" in {
      val filter = this.genericFilter(OR(), AND(), AND())
      filter.sql() mustEqual "(KEY = {KEY} AND KEY_1 = {KEY_1}) OR (KEY2 = {KEY2} AND KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }

    "generate filter groups for OR/AND/OR parameters" in {
      val filter = this.genericFilter(AND(), OR(), OR())
      filter
        .sql() mustEqual "(KEY = {KEY} OR KEY_1 = {KEY_1}) AND (KEY2 = {KEY2} OR KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }

    "generate filter groups for OR/OR/AND parameters" in {
      val filter = this.genericFilter(OR(), OR(), AND())
      filter
        .sql() mustEqual "(KEY = {KEY} OR KEY_1 = {KEY_1}) OR (KEY2 = {KEY2} AND KEY2_2 = {KEY2_2})"
      val params = filter.parameters()
      params.size mustEqual 4
    }
  }

  private def genericFilter(key: SQLKey, group1Key: SQLKey, group2Key: SQLKey): Filter = {
    Filter(
      key,
      FilterGroup(
        group1Key,
        BaseParameter(KEY, VALUE),
        BaseParameter(s"${KEY}_1", VALUE)
      ),
      FilterGroup(
        group2Key,
        BaseParameter(KEY2, VALUE2),
        BaseParameter(s"${KEY2}_2", VALUE2)
      )
    )
  }
}
