package org.maproulette.jobs

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
  * @author cuthbertm
  */
class JobModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[SchedulerActor]("scheduler-actor")
    bindActor[ChallengeSchedulerActor]("challenge-scheduler-actor")
    bind(classOf[Scheduler]).asEagerSingleton()
  }
}
