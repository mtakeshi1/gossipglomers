package json.messages

import io.circe.Decoder.Result
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.{Decoder, DecodingFailure, Json}
import json.messages.JSONParser.{BodyDecoder, MessageBody, ReplyBody}


object Chapter4 extends BodyDecoder {
  override def decodeBody(body: Json): Result[MessageBody] =
    (body \\ "type").collectFirst { case f => f.asString }.flatten match
      case a => Left(DecodingFailure("unknown type: " + a, List()))

  case class Add(msg_id: Long, delta: Long) extends MessageBody {
    override def typeName: String = "add"
    override def subFields: List[(String, Any)] = List(("delta", delta))
  }

  case class AddOk(msg_id: Long, in_reply_to: Long) extends ReplyBody {
    override def typeName: String = "add_ok"
  }

  case class Read(msg_id: Long) extends MessageBody {
    override def typeName: String = "read"
  }

  case class ReadOk(msg_id: Long, in_reply_to: Long, value: Long) extends ReplyBody {
    override def typeName: String = "read_ok"

    override def subFields: List[(String, Any)] = List(("value", value))
  }




}
