package json.messages

import io.circe.Decoder.Result
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{Decoder, DecodingFailure, Json}
import json.messages.JSONParser.{BodyDecoder, MessageBody, ReplyBody}

object Chapter3 extends BodyDecoder {
  def decodeBody(body: Json): Result[MessageBody] =
    (body \\ "type").collectFirst { case f => f.asString }.flatten match
      case Some("broadcast") => body.as[Broadcast]
      case Some("broadcast_ok") => body.as[BroadcastOk]
      case Some("read") => body.as[Read]
      case Some("read_ok") => body.as[ReadOk]
      case a => Left(DecodingFailure("unknown type: " + a, List()))


  case class Broadcast(msg_id: Long, message: Long) extends MessageBody {
    override def typeName: String = "broadcast"

    override def subFields: List[(String, Any)] = List(("message", message))
  }

  case class BroadcastOk(msg_id: Long, in_reply_to: Long) extends ReplyBody {
    override def typeName: String = "broadcast_ok"
  }

  //  implicit val broadcastDecoder: Decoder[Broadcast] = deriveDecoder
  //  implicit val broadcastOkDecoder: Decoder[BroadcastOk] = deriveDecoder

  case class Read(msg_id: Long) extends MessageBody {
    override def typeName: String = "read"
  }

  case class ReadOk(msg_id: Long, in_reply_to: Long, messages: List[Long]) extends ReplyBody {
    override def typeName: String = "read_ok"

    override def subFields: List[(String, Any)] = List(("messages", messages))
  }


}
