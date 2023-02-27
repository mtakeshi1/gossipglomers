package server

import json.messages.BasicMessages.{Echo, EchoReply, Topology, TopologyOk, topologyOkDecoder}
import json.messages.Broadcasts
import json.messages.Chapter2.{Generate, GenerateReply}
import json.messages.Chapter3.{Broadcast, BroadcastOk, Read, ReadOk}
import json.messages.JSONParser.{Envelope, ReplyBody, encodeEnvelope}

import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap, Executors, ScheduledExecutorService, ThreadFactory, TimeUnit}
import java.util.concurrent.atomic.AtomicLong

case class Server(nodeId: String, allNodes: List[String]) {

  var topology: Option[Map[String, List[String]]] = None

  val myIndex: Int = allNodes.sorted.indexOf(nodeId)

  val incr: Int = allNodes.size

  val idGen: AtomicLong = new AtomicLong(myIndex)

  def otherNodes: List[String] = {
//    topology.flatMap(_.get(nodeId)).getOrElse(broadcastStrategy.selectNodesToSend(nodeId, allNodes))
    broadcastStrategy.selectNodesToSend(nodeId, allNodes)
  }

  def closeNodes: List[String] = {
    topology.map(_.getOrElse(nodeId, List())).getOrElse(otherNodes)
  }

  val received: scala.collection.mutable.Set[Long] = scala.collection.mutable.Set()

  val maxRetries: Int = 999999

  val pendingAcks: ConcurrentMap[Long, (Envelope, Int)] = new ConcurrentHashMap()
  val acksReceived: ConcurrentMap[Long, Long] = new ConcurrentHashMap()

  val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1, (r: Runnable) => {
    val t = new Thread(r)
    t.setDaemon(true)
    t
  })

  val broadcastStrategy: Broadcasts.BroadcastStragegy = Broadcasts.SingleNodeFanOutStrategy

  log(s"broadcast from $nodeId would yield: $otherNodes")

  def newId(): Long = idGen.addAndGet(incr)

  def send(envelope: Envelope): Unit = Main.send(envelope)

  def checkAck(envelope: Envelope, retryCount: Int = 0): Unit = {
    synchronized {
      if (!acksReceived.containsKey(envelope.body.msg_id) && retryCount < maxRetries) {
        send(envelope)
        val delay = Math.min(1 + retryCount, 5)
        scheduler.schedule(new Runnable {
          override def run(): Unit = checkAck(envelope, retryCount + 1)
        }, delay, TimeUnit.SECONDS)
      }
    }
  }

  def sendWithRetry(envelopeFactory: () => Envelope): Unit = {
    send(envelopeFactory())
    scheduler.schedule(new Runnable {override def run(): Unit = checkAck(envelopeFactory())}, 1, TimeUnit.SECONDS)
  }

  def broadcast(envelope: Envelope): Unit = {
    broadcastStrategy.selectNodesToSend(nodeId, allNodes).map(n => envelope.copy(dest = Some(n) )).foreach(send)
  }

  def logState(): Unit = {
    System.err.println(s"[$nodeId] nextId: ${idGen.get()} currentState: ${received.toList} acks: ${acksReceived.keySet()}")
  }

  def log(msg: String): Unit = System.err.println(msg)

  def handle(envelope: Envelope): Unit = {
    synchronized {
      envelope.body match
        case body: ReplyBody => acksReceived.putIfAbsent(body.in_reply_to, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1))
        case _ => {}

      envelope.body match {
        case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, newId(), msg_id)))
        case Topology(msg_id, topology) => {
          this.topology = Some(topology)
          Main.send(envelope.replyWithBody(TopologyOk(newId(), msg_id)))
        }
        case Generate(msg_id) => Main.send(envelope.replyWithBody(GenerateReply(newId(), msg_id, newId())))
        case Broadcast(msg_id, message) => {
          if(received.add(message)) {
//            otherNodes.filter{n => !envelope.src.contains(n)}.foreach(n => send(Envelope(envelope.dest, Some(n), Broadcast(newId(), message))))
            val next = newId()
            otherNodes.filter{n => !envelope.src.contains(n)}.foreach(n => sendWithRetry(() => Envelope(envelope.dest, Some(n), Broadcast(next, message))))
          }
          send(envelope.replyWithBody(BroadcastOk(newId(), msg_id)))
        }
        case _:BroadcastOk => {}
        case Read(msg_id) => send(envelope.replyWithBody(ReadOk(newId(), msg_id, received.toList)))
        case _ => sys.error("unknown message: " + envelope.body)
      }
      logState()
    }
  }

}
