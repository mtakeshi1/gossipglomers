package json.messages

import io.circe.*
import io.circe.Decoder.Result
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*
import json.messages.JSONParser.{BodyDecoder, MessageBody, ReplyBody}

import scala.jdk.CollectionConverters.*

case object BasicMessages extends BodyDecoder {

  val types: List[String] = List("init", "init_ok", "echo", "echo_ok")

  def canDecode(typeName: String): Boolean = types.contains(typeName)

  def decodeBody(body: Json): Result[MessageBody] = {
    (body \\ "type").collectFirst { case f => f.asString }.flatten match
      case Some("init") => initDecoder.decodeJson(body)
      case Some("init_ok") => initOkDecoder.decodeJson(body)
      case Some("echo") => echoDecoder.decodeJson(body)
      case Some("echo_ok") => echoOkDecoder.decodeJson(body)
      case Some("topology") => topologyDecoder.decodeJson(body)
      case Some("topology_ok") => topologyOkDecoder.decodeJson(body)
      case a => Left(DecodingFailure("unknown type: " + a, List()))
  }

  //received {"id":0,"src":"c0","dest":"n4","body":{"type":"init","node_id":"n4","node_ids":["n1","n2","n3","n4","n5"],"msg_id":1}}


  implicit val initDecoder: Decoder[InitBody] = deriveDecoder
  implicit val initOkDecoder: Decoder[InitReply] = deriveDecoder
  implicit val echoDecoder: Decoder[Echo] = deriveDecoder
  implicit val echoOkDecoder: Decoder[EchoReply] = deriveDecoder

  implicit val topologyDecoder: Decoder[Topology] = deriveDecoder
  implicit val topologyOkDecoder: Decoder[TopologyOk] = deriveDecoder

  case class InitBody(msg_id: Long, node_id: String, node_ids: List[String]) extends MessageBody {
    override def typeName: String = "init"
  }

  case class InitReply(msg_id: Long, in_reply_to: Long) extends ReplyBody {
    override def typeName: String = "init_ok"
  }

  case class Echo(echo: String, msg_id: Long) extends MessageBody {
    override def typeName: String = "echo"
  }

  case class EchoReply(echo: String, msg_id: Long, in_reply_to: Long) extends ReplyBody {
    override def typeName: String = "echo_ok"

    override def subFields: List[(String, Any)] = List(("echo", echo))
  }

  case class Topology(msg_id: Long, topology: Map[String, List[String]]) extends MessageBody {
    override def typeName: String = "topology"
  }

  case class TopologyOk(msg_id: Long, in_reply_to: Long) extends ReplyBody {
    override def typeName: String = "topology_ok"
  }

}
