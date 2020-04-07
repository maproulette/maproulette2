/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework

import org.maproulette.framework.util.TestDatabase
import org.scalatest.{BeforeAndAfterAll, Suite, Suites}

/**
  * @author mcuthbert
  */
class FrameworkMasterSuite extends Suites with BeforeAndAfterAll with TestDatabase {
  private val suites = IndexedSeq(
    new ChallengeSpec,
    new CommentSpec,
    new GroupSpec,
    new ProjectSpec,
    new UserMetricsSpec,
    new UserSavedObjectsSpec,
    new UserSpec,
    new VirtualProjectSpec
  )
  private val tagFilters: Seq[String] = Seq()
  override val nestedSuites: IndexedSeq[Suite] =
    suites.filter(
      tagFilters.isEmpty || _.tags.flatMap(_._2).exists(tag => tagFilters.contains(tag.toLowerCase))
    )

  override protected def beforeAll(): Unit = this.applyEvolutions()

  override protected def afterAll(): Unit = this.cleanupEvolutions()
}
