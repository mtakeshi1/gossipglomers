package json.messages.v2

import io.circe.Decoder.Result
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}

object BasicTypesV2 {

  def anyToJson(a: Any): Json = {
    a match
      case null => Json.Null
      case s: String => Json.fromString(s)
      case s: Int => Json.fromInt(s)
      case s: Long => Json.fromLong(s)
      case s: Boolean => Json.fromBoolean(s)
      case None => Json.Null
      case Some(s) => anyToJson(s)
      case l: List[_] => Json.arr(l.map(anyToJson): _*)
      case s: Set[_]=> Json.arr(s.toList.map(anyToJson): _*)
      case j: Json => j
      case m: Map[_, _] => Json.fromJsonObject(JsonObject(m.map((k, v) => (k.toString, anyToJson(v))).toList: _*))
      case _ => sys.error("??? " + a)
  }

  trait Body {
    def typeName: String

    def msg_id: Long

    def toJson: Json
  }


  trait MessageReply extends Body {
    def in_reply_to: Long
  }

  case class EnvelopeV2(from: String, to: String, body: Body) {
    def generateReply(messageId: Long, inReplyTo: Long, messageType: String = body.typeName + "_ok", additionalFields: Map[String, Any] = Map()): EnvelopeV2 = {
      val completed = additionalFields ++ List(
        "type" -> messageType,
        "msg_id" -> messageId,
        "in_reply_to" -> inReplyTo
      )
      EnvelopeV2(to, from, UnparsedReplyMessageBody(typeName = messageType, msg_id = messageId, in_reply_to = inReplyTo, body = anyToJson(completed)))
    }

    def toJsonString: String = envelopeEncoder.apply(this).noSpaces
  }

  given envelopeEncoder: Encoder[EnvelopeV2] = (a: EnvelopeV2) =>
    Json.obj(("src", Json.fromString(a.from)),
      ("dest", Json.fromString(a.to)),
      ("body", a.body.toJson))

  given envelopeDecoder: Decoder[EnvelopeV2] = (c: HCursor) => {
    for {
      src         <- c.downField("src").as[String]
      dest        <- c.downField("dest").as[String]
      body        <- c.downField("body").as[Json]
      bodyObject  <- body.as[JsonObject]
      body_type   <- bodyObject("type").map(_.as[String]).getOrElse(Left(DecodingFailure(s"cannot find type on: ${body.noSpaces}", List())))
      msg_id      <- bodyObject("msg_id").map(_.as[Long]).getOrElse(Left(DecodingFailure(s"cannot find msg_id on: ${body.noSpaces}", List())))
      reply       = bodyObject("in_reply_to").map(_.as[Long]).flatMap(_.toOption)
    } yield {
      val innerBody = reply.map { irt => UnparsedReplyMessageBody(body_type, msg_id, irt, body) }
        .getOrElse(UnparsedMessageBody(body_type, msg_id, body))
      EnvelopeV2(src, dest, innerBody)
    }
  }

  case class UnparsedMessageBody(typeName: String, msg_id: Long, body: Json) extends Body {
    override def toJson: Json = body
  }

  case class UnparsedReplyMessageBody(typeName: String, msg_id: Long, in_reply_to: Long, body: Json) extends MessageReply {
    override def toJson: Json = body
  }

  case class InitBody(msg_id: Long, node_id: String, node_ids: List[String])

  given initBodyDecoder: Decoder[InitBody] = deriveDecoder

}
