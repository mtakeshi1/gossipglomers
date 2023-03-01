package server

import json.messages.BasicMessages.{Echo, EchoReply}
import json.messages.Chapter3.{Broadcast, BroadcastOk, Read, ReadOk}
import json.messages.JSONParser.Envelope
import server.Servers.{HandlerRegister, MessageHandler, NodeImpl}

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

object Handlers3 extends HandlerRegister {

  private val receivedMessages = Collections.newSetFromMap[Long](new ConcurrentHashMap())

  object BroadcastHandler extends MessageHandler[Broadcast] {
    override def handleMessage(env: Envelope, body: Broadcast, node: NodeImpl): Unit = {
      if (receivedMessages.add(body.message)) {
        node.broadcastTarget.filter { n => !env.src.contains(n) }.foreach { n =>
          val next = node.newId()
          node.sendMessageDurably(() => Envelope(env.dest, Some(n), Broadcast(next, body.message)), next)
        }
      }
      val id = node.newId()
      node.sendMessageDurably(() => env.replyWithBody(BroadcastOk(id, body.msg_id)), id)
    }
  }

  object ReadHandler extends MessageHandler[Read] {
    override def handleMessage(env: Envelope, body: Read, node: NodeImpl): Unit = {
      val id = node.newId()
      node.sendMessage(() => env.replyWithBody(ReadOk(id, body.msg_id, receivedMessages.asScala.toList)))
    }
  }

  override def registerHandlers(server: NodeImpl): Unit =
    server.registerMessageHandler("broadcast", BroadcastHandler)
    server.registerMessageHandler("read", ReadHandler)

}
