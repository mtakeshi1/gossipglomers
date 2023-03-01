package server

import json.messages.JSONParser.{Envelope, MessageBody, ReplyBody}
import json.messages.Broadcasts
import json.messages.BasicMessages.*
import json.messages.Chapter2.{Generate, GenerateReply}

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, Executors, ScheduledExecutorService, TimeUnit}
import scala.language.postfixOps

object Servers {

  trait Node {
    def allNodes: List[String]

    def myId: String

    def handleMessage(env: Envelope): Unit

    def registerMessageHandler(messageType: String, messageHandler: MessageHandler[_]): Unit

  }

  trait NodeImpl extends Node {
    def sendMessage(env: () => Envelope): Unit

    def sendMessageDurably(env: () => Envelope, messageId: Long): Unit

    def maxRetries: Int = Integer.MAX_VALUE

    def retryDelayMillis: Int = 1000

    def newId(): Long
    def broadcastTarget: List[String]
    def isAcked(msgId: Long):Boolean
    def log(message: => String): Unit = synchronized {
      System.err.println(message)
    }
    def delay(task: Runnable, delayyMillis: Long): Unit

  }

  trait MessageHandler[A <: MessageBody] {
    def handleMessage(env: Envelope, body: A, node: NodeImpl): Unit
  }

  trait HandlerRegister {
    def registerHandlers(node: NodeImpl): Unit
  }
  
  case class ThreadConfinedServer(myId: String, allNodes: List[String], messagePublisher: Envelope => Unit) extends NodeImpl {
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1, (r: Runnable) => {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    })
    private val pendingMessages = new ConcurrentHashMap[Long, Any]()
    private val idGenerator = new AtomicLong(allNodes.sorted.indexOf(myId))
    private val handlers = new ConcurrentHashMap[String, List[MessageHandler[_]]]()
    val broadcastStrategy: Broadcasts.BroadcastStragegy = Broadcasts.SingleNodeFanOutStrategy

    override def newId(): Long = idGenerator.addAndGet(allNodes.size)

    def registerMessageHandler(messageType: String, messageHandler: MessageHandler[_]): Unit = scheduler.submit(new Runnable() {
      override def run(): Unit = {
        handlers.put(messageType, messageHandler :: handlers.getOrDefault(messageType, List()))
      }
    })
    def delay(task: Runnable, delayyMillis: Long): Unit = scheduler.schedule(task, delayyMillis, TimeUnit.MILLISECONDS)
    def isAcked(msgId: Long):Boolean = !pendingMessages.containsKey(msgId)

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

    private def doHandleMessage(env: Envelope): Unit = {
      val removed = env.body match
        case e:ReplyBody => pendingMessages.remove(e.in_reply_to) != null
        case _ => false

      val toinvoke = handlers.get(env.body.typeName)
      if(toinvoke == null && !env.body.isInstanceOf[ReplyBody]) log(s"cannot handle message of type: ${env.body.typeName}")
      else toinvoke.foreach(h => h.asInstanceOf[MessageHandler[MessageBody]].handleMessage(env, env.body.asInstanceOf, this))
    }
    private def doSendMessage(env: Envelope): Unit = messagePublisher(env)
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
