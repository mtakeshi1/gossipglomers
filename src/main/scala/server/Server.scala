package server

import json.messages.BasicMessages.{Echo, EchoReply, Topology, TopologyOk}
import json.messages.Chapter2.{Generate, GenerateReply}
import json.messages.JSONParser.Envelope

import java.util.concurrent.atomic.AtomicLong

case class Server(nodeId:String, otherNodes: List[String]) {

  var topology: Option[Map[String, List[String]]] = None

  val myIndex: Int = otherNodes.sorted.indexOf(nodeId)

  val incr: Int = otherNodes.size

  val idGen= new AtomicLong(myIndex)

  def newId(): Long = idGen.addAndGet(incr)

  def handle(envelope: Envelope): Unit = {
    envelope.body match {
      case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, newId(), msg_id)))
      case Topology(msg_id, topology) => { this.topology = Some(topology) ; Main.send(envelope.replyWithBody(TopologyOk(newId(), msg_id))) }
      case Generate(msg_id) => Main.send(envelope.replyWithBody(GenerateReply(newId(),msg_id, newId())))
      case _ => sys.error("unknown message: " + envelope.body)
    }
  }

}
