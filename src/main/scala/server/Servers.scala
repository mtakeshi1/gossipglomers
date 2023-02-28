package server

import json.messages.JSONParser.{Envelope, ReplyBody, MessageBody}
import json.messages.Broadcasts
import json.messages.BasicMessages._

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, Executors, ScheduledExecutorService, TimeUnit}
import scala.language.postfixOps

object Servers {

  trait Node {
    def allNodes: List[String]

    def myId: String

    def handleMessage(env: Envelope): Unit
  }

  trait NodeImpl extends Node {
    def sendMessage(env: () => Envelope): Unit

    def sendMessageDurably(env: () => Envelope, messageId: Long): Unit

    def maxRetries: Int = Integer.MAX_VALUE

    def retryDelayMillis: Int = 1000

    def newId(): Long
  }

  trait MessageHandler[A <: MessageBody] {
    def handleMessage(env: Envelope, body: A, node: NodeImpl): Unit
  }

  object EchoHandler extends MessageHandler[Echo] {
    override def handleMessage(env: Envelope, body: Echo, node: NodeImpl): Unit = {
      //case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, newId(), msg_id)))
      val reply = env.replyWithBody(EchoReply(body.echo, node.newId(), body.msg_id))
      node.sendMessage(() => reply)
    }
  }

  case class ThreadConfinedServer(myId: String, allNodes: List[String]) extends NodeImpl {
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1, (r: Runnable) => {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    })
    private val pendingMessages = new ConcurrentHashMap[Long, Any]()
    private val idGenerator = new AtomicLong(allNodes.sorted.indexOf(myId))

    val broadcastStrategy: Broadcasts.BroadcastStragegy = Broadcasts.SingleNodeFanOutStrategy

    override def newId(): Long = idGenerator.addAndGet(allNodes.size)
    def handleMessage(env: Envelope): Unit = scheduler.submit(new Runnable {
      override def run(): Unit = {
        doHandleMessage(env)
      }
    })
    def sendMessage(env: () => Envelope): Unit = scheduler.submit(new Runnable {
      override def run(): Unit = doSendMessage(env())
    })

    def sendMessageDurably(env: () => Envelope, messageId: Long): Unit = scheduler.submit(new Runnable {
      override def run(): Unit = doSendMessageDurably(env, messageId, 0)
    })

    def broadcastTarget: List[String] = broadcastStrategy.selectNodesToSend(myId, allNodes)

    def log(message: => String): Unit = synchronized { System.err.println(message) }

    private def doHandleMessage(env: Envelope): Unit = {
      env.body match
        case e:ReplyBody => pendingMessages.remove(e.in_reply_to)
        case _ => {}
      ???
    }
    private def doSendMessage(env: Envelope): Unit = Main.send(env)
    private def doSendMessageDurably(env: () => Envelope, messageId: Long, retryCount: Int): Unit = {
      doSendMessage(env())
      scheduler.schedule(new Runnable {
        def run(): Unit = {
          if (pendingMessages.containsKey(messageId) && retryCount < maxRetries) {
            doSendMessageDurably(env, messageId, retryCount + 1)
          }
        }
      }, retryDelayMillis, TimeUnit.MILLISECONDS)
    }

  }

}
