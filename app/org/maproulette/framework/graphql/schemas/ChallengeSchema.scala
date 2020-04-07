/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.graphql.schemas

import javax.inject.Inject
import org.maproulette.framework.graphql.UserContext
import org.maproulette.framework.model._
import org.maproulette.framework.service.ChallengeService
import sangria.schema._

/**
  * @author mcuthbert
  */
class ChallengeSchema @Inject() (override val service: ChallengeService)
    extends MRSchema[Challenge]
    with MRSchemaTypes {
  val queries: List[Field[UserContext, Unit]] = List(
    Field(
      name = "challenge",
      description = Some("Retrieves a specific challenge based on an ID"),
      fieldType = OptionType(ChallengeType),
      arguments = MRSchema.idArg :: Nil,
      resolve = context => this.service.retrieve(context.arg(MRSchema.idArg))
    )
  )

  val mutations: List[Field[UserContext, Unit]] = List()
}

object ChallengeSchema {
  val challengeIdArg: Argument[Long] = Argument("challengeId", LongType, "The challenge id")
  val challengeIdsArg =
    Argument("challengeIds", ListInputType(LongType), "A list of challenge id's")
}
