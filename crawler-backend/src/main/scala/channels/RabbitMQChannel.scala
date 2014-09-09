package org.blikk.crawler.channels

import org.blikk.crawler.Logging
import com.typesafe.config._
import scala.collection.JavaConversions._
import com.rabbitmq.client._

class RabbitMQChannel extends OutputChannel[RabbitMQChannelInput] with Logging {
  
  def pipe(input: RabbitMQChannelInput) : Unit = {
    val connectionString = input.connectionString

    val factory = new ConnectionFactory()
    factory.setUri(connectionString)
    val conn = factory.newConnection()
    val channel = conn.createChannel()

    using(conn) { conn =>
      using(channel) { channel =>
        insertData(input.queue, input.messages, channel)
      }
    }
  }

  def insertData(queue: String, messages: List[String], channel: Channel) : Unit = {
    // Declare the queue
    channel.queueDeclare(queue, true, false, false, null)
    // Insert the messages
    messages.foreach { msg =>
      channel.basicPublish("", queue, null, msg.getBytes)
    }
  }

}