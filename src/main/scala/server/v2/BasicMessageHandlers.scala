package server.v2

import io.circe.{Decoder, DecodingFailure, Json, JsonObject}
import io.circe.generic.semiauto._
import json.messages.v2.BasicTypesV2
import json.messages.v2.Echo.Echo
import json.messages.v2.Echo.echoDecoder
import server.v2.ServersV2.{MessageHandler, TypedMessageHandler}

object BasicMessageHandlers {

  object EchoHandler extends TypedMessageHandler[Echo] {
    override def decoder: Decoder[Echo] = echoDecoder

    override def decodeFailed(error: DecodingFailure): Unit = {
      throw error
    }

    override protected def handleBody(env: BasicTypesV2.EnvelopeV2, server: ServersV2.ServerImplementor, body: Echo): Option[BasicTypesV2.EnvelopeV2] = {
      val v = env.generateReply(server.newId(), body.msg_id, additionalFields = Map("echo" -> body.echo))
      Some(v)
    }

    override def isDefinedFor(envelopeV2: BasicTypesV2.EnvelopeV2): Boolean = envelopeV2.body.typeName == "echo"
  }

  case class Generate(msg_id: Long) {}

  case class GenerateReply(msg_id: Long, in_reply_to: Long, id: Long)

  object IdGeneratorHandler extends TypedMessageHandler[Generate] {
    override def isDefinedFor(envelopeV2: BasicTypesV2.EnvelopeV2): Boolean = envelopeV2.body.typeName == "generate"

    override def decoder: Decoder[Generate] = deriveDecoder

    override protected def handleBody(env: BasicTypesV2.EnvelopeV2, server: ServersV2.ServerImplementor, body: Generate): Option[BasicTypesV2.EnvelopeV2] = {
      Some(env.generateReply(messageId = server.newId(), inReplyTo = body.msg_id, additionalFields = Map("id" -> server.newId())))
    }
  }

}
