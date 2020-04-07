/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.graphql.schemas

import org.joda.time.DateTime
import org.maproulette.framework.model._
import org.maproulette.models.{MapillaryImage, Task, TaskBundle, TaskReviewFields}
import play.api.libs.oauth.RequestToken
import sangria.ast.{ListValue, StringValue}
import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.schema.{ObjectType, ScalarType}

/**
  * @author mcuthbert
  */
trait MRSchemaTypes {
  implicit val graphQLDateTime: ScalarType[DateTime] = ScalarType[DateTime](
    "DateTime",
    coerceOutput = (dt, _) => dt.toString,
    coerceInput = {
      case StringValue(dt, _, _, _, _) => Right(DateTime.parse(dt))
      case _                           => Left(DateTimeCoerceViolation)
    },
    coerceUserInput = {
      case s: String => Right(DateTime.parse(s))
      case _         => Left(DateTimeCoerceViolation)
    }
  )

  // Project Types
  implicit val ProjectType: ObjectType[Unit, Project] =
    deriveObjectType[Unit, Project](ObjectTypeName("Project"))
  // Group Types
  implicit val GroupType: ObjectType[Unit, Group] =
    deriveObjectType[Unit, Group](ObjectTypeName("Group"))
  // User Types
  implicit val RequestTokenType: ObjectType[Unit, RequestToken] =
    deriveObjectType[Unit, RequestToken](ObjectTypeName("RequestToken"))
  implicit val UserSettingsType: ObjectType[Unit, UserSettings] =
    deriveObjectType[Unit, UserSettings](ObjectTypeName("UserSettings"))
  implicit val LocationType: ObjectType[Unit, Location] =
    deriveObjectType[Unit, Location](ObjectTypeName("Location"))
  implicit val OSMProfileType: ObjectType[Unit, OSMProfile] =
    deriveObjectType[Unit, OSMProfile](ObjectTypeName("OSMProfile"))
  implicit val UserType: ObjectType[Unit, User] =
    deriveObjectType[Unit, User](ObjectTypeName("User"))
  implicit val ProjectManagerType: ObjectType[Unit, ProjectManager] =
    deriveObjectType[Unit, ProjectManager](ObjectTypeName("ProjectManager"))
  // Challenge Types
  implicit val ChallengeType: ObjectType[Unit, Challenge] =
    deriveObjectType[Unit, Challenge](ObjectTypeName("Challenge"))
  implicit val ChallengeGeneralType: ObjectType[Unit, ChallengeGeneral] =
    deriveObjectType[Unit, ChallengeGeneral](ObjectTypeName("ChallengeGeneral"))
  implicit val ChallengeCreationType: ObjectType[Unit, ChallengeCreation] =
    deriveObjectType[Unit, ChallengeCreation](ObjectTypeName("ChallengeCreation"))
  implicit val ChallengePriorityType: ObjectType[Unit, ChallengePriority] =
    deriveObjectType[Unit, ChallengePriority](ObjectTypeName("ChallengePriority"))
  implicit val ChallengeExtraType: ObjectType[Unit, ChallengeExtra] =
    deriveObjectType[Unit, ChallengeExtra](ObjectTypeName("ChallengeExtra"))
  // Comment Types
  implicit val CommentType: ObjectType[Unit, Comment] =
    deriveObjectType[Unit, Comment](ObjectTypeName("Comment"))
  // Task Types
  implicit val TaskType: ObjectType[Unit, Task] =
    deriveObjectType[Unit, Task](ObjectTypeName("Task"))
  implicit val TaskReviewFieldsType: ObjectType[Unit, TaskReviewFields] =
    deriveObjectType[Unit, TaskReviewFields](ObjectTypeName("TaskReviewFields"))
  implicit val MapillaryImageType: ObjectType[Unit, MapillaryImage] =
    deriveObjectType[Unit, MapillaryImage](ObjectTypeName("MapillaryImage"))
  // Task Bundle Types
  implicit val TaskBundleType: ObjectType[Unit, TaskBundle] =
    deriveObjectType[Unit, TaskBundle](ObjectTypeName("TaskBundle"))
}
