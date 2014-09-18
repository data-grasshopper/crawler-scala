package org.blikk.crawler

import akka.actor._
import akka.stream.actor._

/** 
  * Receives CrawlItems and produces a stream.
  * This actor only exists so that we can generate a Crawl stream with `FlowFrom`.
  * It does not contain any domain logic.
  */
class ResponsePublisher extends Actor with ActorLogging 
  with ActorPublisher[FetchResponse] {

  override def preStart(){
    log.info("starting")
  }

  def receive = {
    case msg : FetchResponse =>  processItem(msg)
    case msg : ActorPublisherMessage => // Nothing to do
    case msg => log.warning("unhandled message: {}", msg) 
  }

  def processItem(msg: FetchResponse) {
    if (isActive && totalDemand > 0) {
      onNext(msg)
    } else {
      // TODO: This is ugly, should probably keep a buffer
      log.warning("demand is too low, requeuing message")
      self ! msg
    }
  }

}