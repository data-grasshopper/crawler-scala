package org.blikk.crawler

import org.blikk.crawler.channels.OutputputChannelPipeline
import org.blikk.crawler._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.{ask, pipe}
import akka.util.{Timeout}
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import com.typesafe.config.ConfigFactory

object HostWorker {
  def props(service: ActorRef, statsCollector: ActorRef) = Props(classOf[HostWorker], service, statsCollector)
}

class HostWorker(service: ActorRef, statsCollector: ActorRef) extends Actor with HttpFetcher with ActorLogging {

  implicit val askTimeout = Timeout(5 seconds)

  /* Keeps track of all the processors a response goes through */
  val processors = scala.collection.mutable.ArrayBuffer.empty[ResponseProcessor]
  /* Handles the output */
  val outputChannels = new OutputputChannelPipeline(service)

  val workerBehavior : Receive = {
    case FetchRequest(req: WrappedHttpRequest, jobId) =>
      log.debug("requesting url=\"{}\"", req.req.uri)
      context.system.eventStream.publish(JobEvent(jobId, req))
      dispatchHttpRequest(req, jobId, self)
    case msg @ FetchResponse(res, req, jobId) =>
      log.debug("processing response for url=\"{}\"", req.req.uri)
      context.system.eventStream.publish(JobEvent(jobId, msg))
      processResponse(jobId, res, req)
    case AddProcessor(proc, _) =>
      log.debug("adding processor={}", proc.name)
      processors += proc
  }

  def receive = workerBehavior

  def processResponse(jobId: String, res: WrappedHttpResponse, req: WrappedHttpRequest) : Unit = {
    // Ask for the job config and job statistics
    val jobConfigFuture = service ? GetJob(jobId)
    val jobStatsFuture =  statsCollector ? GetJobEventCounts(jobId)
    val initialProcessorInputF = for {
      jobConf <- jobConfigFuture.mapTo[JobConfiguration]
      jobStats <- jobStatsFuture.mapTo[JobStats].map(_.eventCounts)
    } yield ResponseProcessorInput(res, req, jobConf, jobStats, Map.empty)

    initialProcessorInputF onComplete {
      case Success(initialInput) =>
        val result = initialInput.jobConf.processors.foldLeft(initialInput) { (pin, proc) =>
          pin.copy(context = pin.context ++ proc.process(pin))
        }
        log.debug("final context: {}", result.context.toString)
        outputChannels.process(result)
      case Failure(err) =>
        log.error(err.toString)
    }
  }

}