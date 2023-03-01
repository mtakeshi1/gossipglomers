package server

import json.messages.BasicMessages.{Echo, EchoReply, Topology, TopologyOk}
import json.messages.Chapter2.{Generate, GenerateReply}
import json.messages.JSONParser.Envelope
import server.Servers.{MessageHandler, NodeImpl}

object Handlers2 {


  object EchoHandler extends MessageHandler[Echo] {
    override def handleMessage(env: Envelope, body: Echo, node: NodeImpl): Unit = {
      //case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, newId(), msg_id)))
      val reply = env.replyWithBody(EchoReply(body.echo, node.newId(), body.msg_id))
      node.sendMessage(() => reply)
    }
  }

  object TopologyHandler extends MessageHandler[Topology] {
    override def handleMessage(env: Envelope, body: Topology, node: NodeImpl): Unit = {
      //case Echo(echo, msg_id) => Main.send(Envelope(envelope.dest, envelope.src, EchoReply(echo, newId(), msg_id)))
      val reply = env.replyWithBody(TopologyOk(node.newId(), body.msg_id))
      node.sendMessage(() => reply)
    }
  }


  object GenerateHandler extends MessageHandler[Generate] {
    override def handleMessage(env: Envelope, body: Generate, node: NodeImpl): Unit = node.sendMessage(() => env.replyWithBody(GenerateReply(node.newId(), body.msg_id, node.newId())))
  }

}
