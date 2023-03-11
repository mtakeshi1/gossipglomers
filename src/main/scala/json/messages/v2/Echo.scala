package json.messages.v2
import io.circe.*
import io.circe.Decoder.Result
import io.circe.generic.semiauto.*
import io.circe.parser.*
import io.circe.syntax.*

object Echo {
  case class Echo(echo: String, msg_id: Long)
  case class EchoReply(echo: String, msg_id: Long, in_reply_to: Long)

  given echoDecoder: Decoder[Echo]  = deriveDecoder

}
