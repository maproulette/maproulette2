/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.metrics

import java.util.concurrent.TimeUnit

import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.Logger

/**
  * @author cuthbertm
  */
object Metrics {
  def timer[T](name: String, suppress: Boolean = false)(block: () => T): T = {
    val start   = DateTime.now()
    val result  = block()
    val end     = DateTime.now()
    val diff    = end.minus(start.getMillis).getMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) - TimeUnit.MINUTES.toSeconds(
      TimeUnit.MILLISECONDS.toMinutes(diff)
    )
    val milliseconds = TimeUnit.MILLISECONDS.toMillis(diff) - TimeUnit.SECONDS.toMillis(
      TimeUnit.MILLISECONDS.toSeconds(diff)
    )
    if (!suppress) {
      LoggerFactory.getLogger(this.getClass).info(s"$name took $minutes:$seconds.$milliseconds")
    }
    result
  }
}
