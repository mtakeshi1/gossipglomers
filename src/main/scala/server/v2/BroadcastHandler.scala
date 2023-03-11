package server.v2

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import json.messages.v2.BasicTypesV2
import json.messages.v2.BasicTypesV2.{Body, EnvelopeV2}
import server.v2.ServersV2
import server.v2.ServersV2.MessageHandler

class BroadcastHandler extends MessageHandler {

  private val set = scala.collection.mutable.Set[Long]()

  case class Broadcast(msg_id: Long, message: Long, `type`: String = "broadcast") extends Body {
    override def typeName: String = "broadcast"

    override def toJson: Json = this.asJson
  }

  override def isDefinedFor(envelopeV2: BasicTypesV2.EnvelopeV2): Boolean = Set("topology", "broadcast", "read").contains(envelopeV2.body.typeName)

  override def handleMessage(env: BasicTypesV2.EnvelopeV2, server: ServersV2.ServerImplementor): Option[BasicTypesV2.EnvelopeV2] = env.body.typeName match
    case "topology" => Some(env.generateReply(server.newId(), env.body.msg_id))
    case "read" => Some(env.generateReply(server.newId(), env.body.msg_id, additionalFields = Map("messages" -> set.toList)))
    case "broadcast" => {
      env.body.toJson.as[Broadcast].toOption.filter { bc => set.add(bc.message) }.foreach{ bc =>
        server.broadcastTarget.filter(_ != env.from).foreach { node =>
          server.sendMessageDurably(EnvelopeV2(server.myId, node, Broadcast(server.newId(), bc.message)))
        }
      }
      Some(env.generateReply(server.newId(), env.body.msg_id))
    }
    case _ => None
}
