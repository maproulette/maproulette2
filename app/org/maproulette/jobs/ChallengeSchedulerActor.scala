package org.maproulette.jobs

import javax.inject.{Inject, Singleton}

import akka.actor.Actor
import play.api.Logger
import play.api.db.Database

/**
  * @author cuthbertm
  */
@Singleton
class ChallengeSchedulerActor @Inject() (db:Database) extends Actor {
  override def receive: Receive = {
    case "runChallengeSchedules" => runChallengeSchedules()
  }

  def runChallengeSchedules() : Unit = {
    Logger.debug("Running the challenge schedules job now...")
  }
}
