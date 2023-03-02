package json.messages

import json.messages.JSONParser.{Envelope, MessageBody, ReplyBody}
import server.v2.ServersV2._

object Services {

  trait Service {
    def key: String

    def sendRequest(env: Envelope, responseHandler: Envelope => Unit): Unit

  }

  trait KVService extends Service {
    def read(key: String, server: ServerImplementor, responseHandler: Long => Unit): Unit = {
      val msgId = server.newId()
      val req = Envelope(Option(server.myId), Option(key), ReadRPC(msgId, key))
      server.sendMessageHandleResponse(msgId, ()=>req, response => {
        // look at decodeEnvelope on JSONParser and call responseHandler
      })
    }
    def write(from: String, key: String, value: Long, responseHandler: Long => Unit): Unit
    def cas(from: String, key: String, oldValue: Long, newValue: Long, responseHandler: Boolean => Unit): Unit
  }

  case class ReadRPC(msg_id: Long, key: String) extends MessageBody {
    override def typeName: String = "read"
  }

  case class ReadRPCOk(msg_id: Long, in_reply_to: Long, value: Long) extends ReplyBody {
    override def typeName: String = "read"
    override def subFields: List[(String, Any)] = List(("value", value))
  }


}
