package json.messages

import io.circe.Decoder.Result
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure, Json}
import json.messages.JSONParser.{BodyDecoder, MessageBody, ReplyBody}

object Chapter2 extends BodyDecoder {

  def decodeBody(body: Json): Result[MessageBody] = {
    (body \\ "type").collectFirst { case f => f.asString }.flatten match
      case Some("generate") => generateDecoder.decodeJson(body)
      case Some("generate_ok") => generateOkDecoder.decodeJson(body)
      case a => Left(DecodingFailure("unknown type: " + a, List()))
  }


  case class Generate(msg_id: Long) extends MessageBody {
    override def typeName: String = "generate"
  }

  case class GenerateReply(msg_id: Long, in_reply_to: Long, id: Long) extends ReplyBody {
    override def typeName: String = "generate_ok"

    override def subFields: List[(String, Any)] = List(("id", id))
  }

  implicit val generateDecoder: Decoder[Generate] = deriveDecoder
  implicit val generateOkDecoder: Decoder[GenerateReply] = deriveDecoder

}
