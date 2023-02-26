package server

import json.messages.BasicMessages.{Echo, EchoReply, Topology, TopologyOk}
import json.messages.Chapter2.{Generate, GenerateReply}
import json.messages.Chapter3.{Broadcast, BroadcastOk, Read, ReadOk}
import json.messages.JSONParser.{Envelope, encodeEnvelope}

import java.util.concurrent.atomic.AtomicLong

case class Server(nodeId: String, allNodes: List[String]) {

  var topology: Option[Map[String, List[String]]] = None

  val myIndex: Int = allNodes.sorted.indexOf(nodeId)

  val incr: Int = allNodes.size

  val idGen: AtomicLong = new AtomicLong(myIndex)

  val otherNodes: List[String] = allNodes.filter( _ != nodeId)

  val received: scala.collection.mutable.Set[Long] = scala.collection.mutable.Set()

  def newId(): Long = idGen.addAndGet(incr)

  def send(envelope: Envelope): Unit = Main.send(envelope)

  def broadcast(envelope: Envelope): Unit = {
    otherNodes.map(n => envelope.copy(dest = Some(n) )).foreach(send)
  }

  def logState(): Unit = {
    System.err.println(s"[$nodeId] nextId: ${idGen.get()} currentState: ${received.toList}")
  }

  def log(msg: String): Unit = System.err.println(msg)

  def handle(envelope: Envelope): Unit = {
    synchronized {
      log(s"handling $envelope")
      envelope.body match {
        case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, newId(), msg_id)))
        case Topology(msg_id, topology) => {
          this.topology = Some(topology)
          Main.send(envelope.replyWithBody(TopologyOk(newId(), msg_id)))
        }
        case Generate(msg_id) => Main.send(envelope.replyWithBody(GenerateReply(newId(), msg_id, newId())))
        case Broadcast(msg_id, message) => {
          if(received.add(message)) {
            otherNodes.foreach(n => send(Envelope(envelope.dest, Some(n), Broadcast(newId(), message))))
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
