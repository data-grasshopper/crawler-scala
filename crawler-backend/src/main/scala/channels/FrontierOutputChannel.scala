package org.blikk.crawler.channels

import akka.actor._
import org.blikk.crawler.{Logging, RouteFetchRequest, AddToFrontier, JobConfiguration}

class FrontierOutputChannel(implicit system: ActorSystem)
  extends OutputChannel[FrontierChannelInput] with Logging {

  def serviceActor = system.actorSelection("/user/crawlService")

  def pipe(input: FrontierChannelInput, jobConf: JobConfiguration, jobStats: Map[String, Int]) : Unit = {
    // Send each request to the local service master
    input.newRequests.foreach { req =>
      log.debug(s"Adding URL to frontier: ${req.req.uri.toString}")
      serviceActor ! RouteFetchRequest(
        AddToFrontier(req.req, jobConf.jobId, req.scheduledTime, req.ignoreDeduplication))
    }

  }

}