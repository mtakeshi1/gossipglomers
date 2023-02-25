package json.messages

import dev.mccue.json.{Json, JsonDecoder, JsonObject}
import json.messages.BasicMessages.*

import java.util.Optional
import scala.jdk.CollectionConverters.*

object JSONParser {


  case class Envelope(src: Option[String], dest: Option[String], body: MessageBody) {
    def toJson: Json = {
      val builder = Json.objectBuilder()
      if(src.isEmpty) builder.putNull("src") else src.foreach(builder.put("src", _))
      if(dest.isEmpty) builder.putNull("dest") else dest.foreach(builder.put("dest", _))
      builder.put("body", body.toJson)
      builder.toJson
    }
  }


  object Envelope {

    def decodeBody(json: Json): MessageBody = {
      JsonDecoder.field(json, "type", JsonDecoder.string _) match
        case "init" => InitBody.fromJson(json)
        case "init_ok" => InitReply.fromJson(json)
        case "echo" => Echo.fromJson(json)
        case "echo_ok" => EchoReply.fromJson(json)
        case _ => ???
    }

    def fromJson(json: Json): Envelope = {
      val src: Option[String] = JsonDecoder.nullableField(json, "src", JsonDecoder.string _).map(Option(_)).orElse(None)
      val dst: Option[String] = JsonDecoder.nullableField(json, "dest", JsonDecoder.string _).map(Option(_)).orElse(None)
      val body = JsonDecoder.field(json, "body", Envelope.decodeBody _)
      Envelope(src, dst, body)
    }
  }


  def main(args: Array[String]): Unit = {
    val input =
      """
        |{"src": "c1",
        | "dest": "n1",
        | "body": {"msg_id": 1,
        |        "type": "init",
        |        "node_id": "n1",
        |        "node_ids": ["n1"]}}
        |""".stripMargin
    println(Envelope.fromJson(Json.readString(input)))

  }


}
