package json.messages

import java.util.Optional
import scala.jdk.CollectionConverters.*
import io.circe.*
import io.circe.Decoder.Result
import io.circe.generic.semiauto.*

object JSONParser {

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
      case j: Json => j
      case _ => sys.error("??? " + a)
  }

  trait MessageBody {
    def typeName: String

    def msg_id: Long

    def subFields: List[(String, Any)] = List()

    def toJson: Json = {
      val l = List(("type", Json.fromString(this.typeName)), ("msg_id", Json.fromLong(this.msg_id))) ++ subFields.map((k, v) => (k, anyToJson(v)))
      Json.obj(l: _*)
    }
  }

  trait ReplyBody extends MessageBody {
    def in_reply_to: Long

    override def toJson: Json = {
      val l = List(("type", Json.fromString(this.typeName)), ("msg_id", Json.fromLong(this.msg_id)), ("in_reply_to", Json.fromLong(this.in_reply_to))) ++ subFields.map((k, v) => (k, anyToJson(v)))
      Json.obj(l: _*)
    }
  }

  case class Envelope(src: Option[String], dest: Option[String], body: MessageBody) {
    def this(s: String, d: String, b: MessageBody) = this(Some(s), Some(d), b)
    def toJson: Json = encodeEnvelope.apply(this)
  }

  implicit val encodeEnvelope: Encoder[Envelope] = new Encoder[Envelope]:
    override def apply(a: Envelope): Json =
      Json.obj(("src", a.src.map(Json.fromString).getOrElse(Json.Null)),
        ("dest", a.dest.map(Json.fromString).getOrElse(Json.Null)),
        ("body", a.body.toJson))

  def parseBody(maybeBody: Result[Json]): Result[MessageBody] = {
    maybeBody.flatMap(BasicMessages.decodeBody)
  }

  implicit val decodeEnvelope: Decoder[Envelope] = new Decoder[Envelope]:
    override def apply(c: HCursor): Result[Envelope] = {
      for {
        src <- c.downField("src").as[Option[String]]
        dest <- c.downField("dest").as[Option[String]]
        body <- parseBody(c.downField("body").as[Json])
      } yield {
        Envelope(src, dest, body)
      }
    }

  object Envelope {



    //
    //    def decodeBody(json: Json): MessageBody = {
    //      JsonDecoder.field(json, "type", JsonDecoder.string _) match
    //        case "init" => InitBody.fromJson(json)
    //        case "init_ok" => InitReply.fromJson(json)
    //        case "echo" => Echo.fromJson(json)
    //        case "echo_ok" => EchoReply.fromJson(json)
    //        case _ => ???
    //    }
    //
    //    def fromJson(json: Json): Envelope = {
    //      val src: Option[String] = JsonDecoder.nullableField(json, "src", JsonDecoder.string _).map(Option(_)).orElse(None)
    //      val dst: Option[String] = JsonDecoder.nullableField(json, "dest", JsonDecoder.string _).map(Option(_)).orElse(None)
    //      val body = JsonDecoder.field(json, "body", Envelope.decodeBody _)
    //      Envelope(src, dst, body)
    //    }
  }

  //
  //  def main(args: Array[String]): Unit = {
  //    val input =
  //      """
  //        |{"src": "c1",
  //        | "dest": "n1",
  //        | "body": {"msg_id": 1,
  //        |        "type": "init",
  //        |        "node_id": "n1",
  //        |        "node_ids": ["n1"]}}
  //        |""".stripMargin
  //    println(Envelope.fromJson(Json.readString(input)))
  //
  //  }


}
