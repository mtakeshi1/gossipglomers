package server.v2

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import json.messages.Chapter3.Broadcast
import json.messages.JSONParser.encodeEnvelope
import json.messages.v2.BasicTypesV2
import json.messages.v2.BasicTypesV2.{EnvelopeV2, UnparsedReplyMessageBody}
import server.Broadcasts

import java.io.{BufferedReader, EOFException, InputStreamReader}
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap, ExecutorService, Executors, ThreadFactory, TimeUnit}
import scala.collection.*
import scala.concurrent.duration.TimeUnit

object ServersV2 {

  trait MessageHandler {
    def isDefinedFor(envelopeV2: EnvelopeV2): Boolean

    def handleMessage(json: EnvelopeV2, server: ServerImplementor): Option[EnvelopeV2]
  }

  trait TypedMessageHandler[A] extends MessageHandler {
    def decoder: Decoder[A]

    def decodeFailed(error: DecodingFailure): Unit = {}

    override final def handleMessage(env: EnvelopeV2, server: ServerImplementor): Option[EnvelopeV2] = {
      decoder.decodeJson(env.body.toJson) match
        case Left(value) => {
          decodeFailed(value); None
        }
        case Right(value) => handleBody(env, server, value)
    }

    protected def handleBody(env: EnvelopeV2, server: ServerImplementor, body: A): Option[EnvelopeV2] = {
      handleBodyNoResponse(env, server, body)
      None
    }

    protected def handleBodyNoResponse(env: EnvelopeV2, server: ServerImplementor, body: A): Option[EnvelopeV2] = ???
  }

  trait Server {
    def myId: String

    def allNodes: List[String]

    def newId(): Long

    def handleMessage(envelope: EnvelopeV2): Unit

    def registerMessageHandler(handler: MessageHandler): Unit

    def listenForMessages(): Unit = {

    }
  }

  trait ServerImplementor extends Server {
    def executor: DelayedExecutor

    def messageIO: MessageIO

    def messagePublisher: EnvelopeV2 => Unit = e => messageIO.writeMessage(e)

    private val messageCallbacks: mutable.Map[Long, EnvelopeV2 => Unit] = new mutable.HashMap()
    private val messageHandlers: mutable.ListBuffer[MessageHandler] = mutable.ListBuffer()
    private val idGen = new AtomicLong(allNodes.sorted.indexOf(myId))

    def maxRetries: Int = Integer.MAX_VALUE

    def retryDelayMillis: Int = 1000

    override def registerMessageHandler(handler: MessageHandler): Unit = executor.submit(() => {
      messageHandlers.addOne(handler)
    })

    override final def newId(): Long = idGen.addAndGet(allNodes.size)

    final def sendMessage(msg: EnvelopeV2): Unit = sendMessageLater(msg, 0)

    final def sendMessageLater(msg: EnvelopeV2, delayMillis: Long): Unit = executor.scheduleMillis(() => messagePublisher(msg), delayMillis)

    final def handleMessage(envelope: EnvelopeV2): Unit = executor.submit(() => doHandleMessage(envelope))

    final def sendMessageDurably(msg: EnvelopeV2): Unit = sendMessageHandleResponse(msg, _ => {})

    final def sendMessageHandleResponse(msg: EnvelopeV2, responseCallback: EnvelopeV2 => Unit, retries: Int = 0): Unit = {
      val messageId = msg.body.msg_id
      messageCallbacks.put(messageId, responseCallback)
      sendMessage(msg)
      delay(() => {
        if (!isAcked(messageId) && retries < maxRetries) {
          sendMessageHandleResponse(msg, responseCallback, retries + 1)
        }
      }, retryDelayMillis)
    }

    protected final def doHandleMessage(envelope: EnvelopeV2): Unit = {
      envelope.body match
        case UnparsedReplyMessageBody(typeName, msg_id, in_reply_to, body) => {
          messageCallbacks.remove(in_reply_to).foreach(_.apply(envelope))
        }
        case _ => {}
      messageHandlers.filter(_.isDefinedFor(envelope)).flatMap { h =>
        try {
          h.handleMessage(envelope, this)
        } catch {
          case e:Exception => {
            e.printStackTrace()
            throw e
          }
        }
      }.foreach(sendMessage)
    }

    final def broadcastTarget: List[String] = Broadcasts.DoubleRootStrategy.selectNodesToSend(myId, allNodes)

    final def isAcked(msgId: Long): Boolean = !messageCallbacks.isDefinedAt(msgId)

    final def log(message: => String): Unit = synchronized {
      System.err.println(message)
    }

    final def delay(task: Runnable, delayyMillis: Long): Unit = executor.scheduleMillis(task, delayyMillis)

    def serverLoop(): Unit = {
      while (true) {
        messageIO.nextMessage() match
          case Left(value) => throw value
          case Right(value) => handleMessage(value)
      }
    }
  }

  trait DelayedExecutor {
    def submit(task: Runnable): Unit

    def schedule(task: Runnable, delay: Long, unit: TimeUnit): Unit

    def scheduleMillis(task: Runnable, delayMillis: Long): Unit = schedule(task, delayMillis, TimeUnit.MILLISECONDS)
  }

  object DelayedExecutor {
    def apply(): DelayedExecutor = {
      val ex = Executors.newScheduledThreadPool(1, (r: Runnable) => {
        val t = new Thread(r)
        t.setDaemon(true)
        t
      })
      new DelayedExecutor:
        override def submit(task: Runnable): Unit = ex.submit(task)

        override def schedule(task: Runnable, delay: Long, unit: TimeUnit): Unit = ex.schedule(task, delay, unit)
    }
  }

  trait MessageIO {

    def log(msg: String): Unit = System.err.println(msg)

    def nextMessage(): Either[io.circe.Error, EnvelopeV2]

    def writeMessage(envelopeV2: EnvelopeV2): Unit
  }

  object ConsoleIO extends MessageIO {
    private val reader = new BufferedReader(new InputStreamReader(System.in))

    override def nextMessage(): Either[io.circe.Error, EnvelopeV2] = {
      import BasicTypesV2.given
      import io.circe.parser

      val line = reader.readLine()
      if (line == null) throw new EOFException()
      log(s"<<<: $line")
      for {
        json <- parser.parse(line)
        env <- BasicTypesV2.envelopeDecoder.decodeJson(json)
      } yield {
        env
      }
    }

    override def writeMessage(envelopeV2: EnvelopeV2): Unit = synchronized {
      val string = envelopeV2.toJsonString
      log(s">>>: $string")
      println(string)
    }
  }

  case class DelayedServer(myId: String, allNodes: List[String], executor: DelayedExecutor, messageIO: MessageIO) extends ServerImplementor {
  }

  def bootstrapServer(messageHandlers: MessageHandler*): Unit = {
    val executor = DelayedExecutor()
    val io = ConsoleIO
    val server = for {
      msg <- io.nextMessage()
      init <- BasicTypesV2.initBodyDecoder.decodeJson(msg.body.toJson)
    } yield {
      val server = DelayedServer(init.node_id, init.node_ids, executor, io)
      messageHandlers.foreach(server.registerMessageHandler)
      io.writeMessage(msg.generateReply(server.newId(), init.msg_id))
      server.serverLoop()
    }
    server match
      case Left(value) => throw value
      case Right(value) => {}
  }


}
