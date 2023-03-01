package server

import json.messages.Chapter3.{Broadcast, BroadcastMany, BroadcastManyOk, BroadcastOk, Read, ReadOk}
import json.messages.JSONParser.Envelope
import server.Handlers3.BroadcastHandler
import server.Servers.{HandlerRegister, MessageHandler, NodeImpl}

import scala.jdk.CollectionConverters.*
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.collection.mutable.{HashSet, Set}

object Handlers3e extends HandlerRegister {

  private val receivedMessages = Collections.newSetFromMap[Long](new ConcurrentHashMap())

  private val pendingMessages = new ConcurrentHashMap[String, mutable.Set[Long]]()
  private val lastSent = new ConcurrentHashMap[Long, (String, mutable.Set[Long])]()

  val delayMillis = 500

  def sendDelayedBroadcast(target: String, messageId: Long, server: NodeImpl): Unit = {
    def localSendMessage(): Unit = {
      val toSend = pendingMessages.get(target)
      val copy = toSend.toList
      lastSent.put(messageId, (target, mutable.HashSet(copy:_*)))
      server.log(s"sending $toSend to node: $target")
      server.sendMessage(() => Envelope(Some(server.myId), Some(target), BroadcastMany(messageId, copy)))
      server.delay(new Runnable() {
        override def run(): Unit = sendDelayedBroadcast(target, messageId, server)
      }, delayMillis)
    }

    if (lastSent.containsKey(messageId)) {
      if (server.isAcked(messageId)) {
        val (node, sent) = lastSent.get(messageId)
        val pending = pendingMessages.get(node)
        server.log(s"node $target acked values: $sent - pending: $pending")
        pending.subtractAll(sent)
        server.log(s"node $target acked values: $sent - remaining: $pending")
        if (pending.isEmpty) {
          pendingMessages.remove(node)
        }
      } else {
        localSendMessage()
      }
    } else if (pendingMessages.getOrDefault(target, mutable.HashSet()).nonEmpty) {
      localSendMessage()
    }
  }

  object BroadcastHandler extends MessageHandler[Broadcast] {
    override def handleMessage(env: Envelope, body: Broadcast, node: NodeImpl): Unit = {
      newBroadcastMessagesReceived(env.src, node, List(body.message))
      val id = node.newId()
      node.sendMessageDurably(() => env.replyWithBody(BroadcastOk(id, body.msg_id)), id)
    }
  }

  object BroadcastManyHandler extends MessageHandler[BroadcastMany] {
    override def handleMessage(env: Envelope, body: BroadcastMany, node: NodeImpl): Unit = {
      newBroadcastMessagesReceived(env.src, node, body.messages)
      val id = node.newId()
      node.sendMessageDurably(() => env.replyWithBody(BroadcastManyOk(id, body.msg_id)), id)
    }
  }

  private def newBroadcastMessagesReceived(src: Option[String], node: NodeImpl, allMessages: List[Long]): Unit = {
    if (receivedMessages.addAll(allMessages.asJavaCollection)) {
      node.broadcastTarget.filter { n => !src.contains(n) }.foreach { n => {
        val pending = pendingMessages.computeIfAbsent(n, _ => new mutable.HashSet[Long]())
        val sizePre = pending.size
        pending.addAll(allMessages)
        if (pending.size != sizePre) {
          val messageId = node.newId()
          node.delay(new Runnable() {
            override def run(): Unit = sendDelayedBroadcast(n, messageId, node)
          }, delayMillis)
        }
        //sendDelayedBroadcast(n, node.newId(), node)
      }
      }
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
    server.registerMessageHandler("broadcast_many", BroadcastManyHandler)
    server.registerMessageHandler("read", ReadHandler)
}
