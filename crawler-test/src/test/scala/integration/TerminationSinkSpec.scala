package org.blikk.test.integration

import org.blikk.test._
import org.blikk.crawler._
import scala.concurrent.duration._
import org.blikk.crawler.app._
import akka.stream.scaladsl2._
import akka.stream.scaladsl2.FlowGraphImplicits._
import org.blikk.crawler.processors._

class TerminationSinkSpec extends IntegrationSuite("TerminationSinkSpec") {

  describe("crawler") {

    it("should terminate on termination conditions") {
      implicit val streamContext = createStreamContext()
      import streamContext.{materializer, system}
      import system.dispatcher

      val in = streamContext.flow
      val fLinkExtractor = RequestExtractor.build()
      val fLinkSender = ForeachSink[CrawlItem] { item => 
        log.info("{}", item.toString) 
        probes(1).ref ! item.req.uri.toString
      }
      
      val graph = FlowGraph { implicit b =>
        val bcast = Broadcast[CrawlItem]
        val fTerminationSink = TerminationSink.build(_.numFetched >= 5)
        in ~> bcast ~> fLinkExtractor ~> FrontierSink.build()
        bcast ~> fTerminationSink
        bcast ~> fLinkSender
      }.run()

      streamContext.api ! WrappedHttpRequest.getUrl("http://localhost:9090/crawl/1")
      probes(1).receiveN(5).toSet shouldBe (1 to 5).map { num =>
        s"http://localhost:9090/crawl/${num}"}.toSet
      probes(1).expectNoMsg()
      streamContext.shutdown()
    }

  }

}