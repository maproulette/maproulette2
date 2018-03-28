package org.maproulette.models

import org.scalatestplus.play.PlaySpec

/**
  * @author cuthbertm
  */
class ChallengeSpec() extends PlaySpec {
  implicit var challengeID:Long = -1

  "PriorityRule" should {
    "string types should operate correctly" in {
      PriorityRule("equal", "key", "value", "string").doesMatch(Map("key" -> "value")) mustEqual true
      PriorityRule("not_equal", "key", "value", "string").doesMatch(Map("key" -> "value2")) mustEqual true
      PriorityRule("contains", "key", "Value", "string").doesMatch(Map("key" -> "TheValue")) mustEqual true
      PriorityRule("not_contains", "key", "value", "string").doesMatch(Map("key" -> "Nothing")) mustEqual true
      PriorityRule("is_empty", "key", "", "string").doesMatch(Map("key" -> "")) mustEqual true
      PriorityRule("is_not_empty", "key", "", "string").doesMatch(Map("Key" -> "value")) mustEqual true
    }

    "integer types should operate correctly" in {
      PriorityRule("==", "key", "0", "integer").doesMatch(Map("key" -> "0")) mustEqual true
      PriorityRule("!=", "key", "0", "integer").doesMatch(Map("key" -> "1")) mustEqual true
      PriorityRule("<", "key", "0", "integer").doesMatch(Map("key" -> "-1")) mustEqual true
      PriorityRule("<=", "key", "0", "integer").doesMatch(Map("key" -> "0")) mustEqual true
      PriorityRule(">", "key", "0", "integer").doesMatch(Map("key" -> "1")) mustEqual true
      PriorityRule(">=", "key", "0", "integer").doesMatch(Map("Key" -> "0")) mustEqual true
    }

    "double types should operate correctly" in {
      PriorityRule("==", "key", "0", "double").doesMatch(Map("key" -> "0")) mustEqual true
      PriorityRule("!=", "key", "0", "double").doesMatch(Map("key" -> "1")) mustEqual true
      PriorityRule("<", "key", "0", "double").doesMatch(Map("key" -> "-1")) mustEqual true
      PriorityRule("<=", "key", "0", "double").doesMatch(Map("key" -> "0")) mustEqual true
      PriorityRule(">", "key", "0", "double").doesMatch(Map("key" -> "1")) mustEqual true
      PriorityRule(">=", "key", "0", "double").doesMatch(Map("Key" -> "0")) mustEqual true
    }

    "long types should operate correctly" in {
      PriorityRule("==", "key", "0", "long").doesMatch(Map("key" -> "0")) mustEqual true
      PriorityRule("!=", "key", "0", "long").doesMatch(Map("key" -> "1")) mustEqual true
      PriorityRule("<", "key", "0", "long").doesMatch(Map("key" -> "-1")) mustEqual true
      PriorityRule("<=", "key", "0", "long").doesMatch(Map("key" -> "0")) mustEqual true
      PriorityRule(">", "key", "0", "long").doesMatch(Map("key" -> "1")) mustEqual true
      PriorityRule(">=", "key", "0", "long").doesMatch(Map("Key" -> "0")) mustEqual true
    }
  }
}
