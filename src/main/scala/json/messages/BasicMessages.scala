package json.messages

import dev.mccue.json.{Json, JsonDecoder}
import scala.jdk.CollectionConverters.*

case object BasicMessages {
  def toJsonArray(l: List[String]) = {
    val builder = Json.arrayBuilder()
    l.foreach(builder.add)
    builder
  }

  trait MessageBody {
    def typeName: String

    def toJson: Json
  }

  object EmptyBody extends MessageBody {
    override def typeName: String = "nothing"

    override def toJson: Json = ???
  }

  case class InitBody(id: Long, nodeId: String, nodeIds: List[String]) extends MessageBody {
    override def typeName: String = "init"

    override def toJson: Json = Json.objectBuilder()
      .put("msg_id", id)
      .put("type", typeName)
      .put("node_id", nodeId)
      .put("node_ids", toJsonArray(nodeIds))
      .toJson
  }

  object InitBody {
    def fromJson(json: Json): MessageBody = {
      InitBody(
        JsonDecoder.field(json, "msg_id", JsonDecoder.long_ _),
        JsonDecoder.field(json, "node_id", JsonDecoder.string _),
        JsonDecoder.field(json, "node_ids", JsonDecoder.array(JsonDecoder.string _)).asScala.toList
      )
    }
  }

  case class InitReply(id: Long, inReplyTo: Long) extends MessageBody {
    override def typeName: String = "init_ok"

    /*
     body: {msg_id: 123
            in_reply_to: 1
            type: "init_ok"}}
    */
    override def toJson: Json = Json.objectBuilder()
      .put("msg_id", id)
      .put("type", typeName)
      .put("in_reply_to", inReplyTo)
      .toJson
  }

  object InitReply {
    def fromJson(json: Json): InitReply = {
      InitReply (
        JsonDecoder.field(json, "msg_id", JsonDecoder.long_ _),
        JsonDecoder.field(json, "in_reply_to", JsonDecoder.long_ _)
      )
    }
  }

  case class Echo(echo: String) extends MessageBody {
    override def typeName: String = "echo"
    override def toJson: Json = Json.objectBuilder().put("echo", echo).put("type", typeName).toJson
  }
  object Echo {
    def fromJson(json: Json): MessageBody = Echo(JsonDecoder.field(json, "echo", JsonDecoder.string _))
  }

  case class EchoReply (echo: String) extends MessageBody{
    override def typeName: String = "echo_ok"
    override def toJson: Json = Json.objectBuilder().put("echo", echo).put("type", typeName).toJson
  }

  object EchoReply {
    def fromJson(json: Json): MessageBody = EchoReply(JsonDecoder.field(json, "echo", JsonDecoder.string _))
  }


}
