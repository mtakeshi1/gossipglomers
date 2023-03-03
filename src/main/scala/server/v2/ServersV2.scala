package server.v2

import io.circe.{HCursor, Json}
import json.messages.Chapter3.Broadcast
import json.messages.JSONParser.{Envelope, encodeEnvelope}
import server.Broadcasts

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap, TimeUnit}
import scala.concurrent.duration.TimeUnit
import scala.collection._

object ServersV2 {

  trait MessageHandler {
    def handleMessage(json: Json, server: ServerImplementor): Unit
  }

  trait Server {
    def myId: String

    def allNodes: List[String]

    def newId(): Long

    def handleMessage(envelope: Json): Unit

    def registerMessageHandler(m: Json => Boolean, handler: MessageHandler): Unit
  }

  trait ServerImplementor extends Server {
    def executor: DelayedExecutor

    def messagePublisher: Envelope => Unit

    private val messageCallbacks: mutable.Map[Long, Json => Unit] = new mutable.HashMap()
    private val messageHandlers: mutable.ListBuffer[PartialFunction[Json, Unit]] = mutable.ListBuffer()

    def maxRetries: Int = Integer.MAX_VALUE

    def retryDelayMillis: Int = 1000

    override def registerMessageHandler(m: Json => Boolean, handler: MessageHandler): Unit = executor.submit(() => {
      val pf: PartialFunction[Json, Unit] = {
        case d if m(d) => handler.handleMessage(d, this)
      }
      messageHandlers.addOne(pf)
    })

    private val idGen = new AtomicLong(allNodes.sorted.indexOf(myId))

    override final def newId(): Long = idGen.addAndGet(allNodes.size)

    final def sendMessage(msg: () => Envelope): Unit = sendMessageLater(msg, 0)

    final def sendMessageLater(msg: () => Envelope, delayMillis: Long): Unit = executor.scheduleMillis(() => messagePublisher(msg()), delayMillis)

    final override def handleMessage(envelope: Json): Unit = executor.submit(() => doHandleMessage(envelope))

    final def sendMessageDurably(messageId: Long, msg: () => Envelope): Unit = sendMessageHandleResponse(messageId, msg, _ => {})

    final def sendMessageHandleResponse(messageId: Long, msg: () => Envelope, responseCallback: Json => Unit, retries: Int = 0): Unit = {
      messageCallbacks.put(messageId, responseCallback)
      sendMessage(msg)
      delay(() => {
        if (!isAcked(messageId) && retries < maxRetries) {
          sendMessageHandleResponse(messageId, msg, responseCallback, retries + 1)
        }
      }, retryDelayMillis)
    }

    protected def doHandleMessage(envelope: Json): Unit = {
      for {
        body      <- envelope.asObject.flatMap(_("body")).flatMap(_.asObject)
        rep       <- body("in_reply_to").flatMap(_.asNumber).flatMap(_.toLong)
        callback <- messageCallbacks.remove(rep)
      } yield callback(envelope)

      messageHandlers.foreach{h => h.apply(envelope)}
    }

    final def broadcastTarget: List[String] = Broadcasts.DoubleRootStrategy.selectNodesToSend(myId, allNodes)

    final def isAcked(msgId: Long): Boolean = !messageCallbacks.isDefinedAt(msgId)

    final def log(message: => String): Unit = synchronized {
      System.err.println(message)
    }

    final def delay(task: Runnable, delayyMillis: Long): Unit = executor.scheduleMillis(task, delayyMillis)
  }

  trait DelayedExecutor {
    def submit(task: Runnable): Unit

    def schedule(task: Runnable, delay: Long, unit: TimeUnit): Unit

    def scheduleMillis(task: Runnable, delayMillis: Long): Unit = schedule(task, delayMillis, TimeUnit.MILLISECONDS)
  }


}
