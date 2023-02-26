package server

import json.messages.BasicMessages.{Echo, EchoReply}
import json.messages.JSONParser.Envelope

case class Server(nodeId:String, otherNodes: List[String]) {

  def handle(envelope: Envelope): Unit = {
    envelope.body match {
      case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, msg_id+1, msg_id)))
      case _ => sys.error("unknown message: " + envelope.body)
    }
  }

}
