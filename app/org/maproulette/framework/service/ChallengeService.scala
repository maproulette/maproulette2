/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import org.maproulette.framework.model.Challenge
import org.maproulette.framework.psql.Query
import org.maproulette.framework.psql.filter.BaseParameter
import org.maproulette.framework.repository.ChallengeRepository

/**
  * Service layer for Challenges to handle all the challenge business logic
  *
  * @author mcuthbert
  */
@Singleton
class ChallengeService @Inject() (repository: ChallengeRepository) extends ServiceMixin[Challenge] {

  def retrieve(id: Long): Option[Challenge] =
    this.query(Query.simple(List(BaseParameter(Challenge.FIELD_ID, id)))).headOption

  def query(query: Query): List[Challenge] = this.repository.query(query)
}
