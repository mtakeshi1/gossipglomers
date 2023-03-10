import json.messages.v2.BasicTypesV2.{EnvelopeV2, UnparsedMessageBody}
import org.junit.{Assert, Test}
import io.circe.parser
import json.messages.v2.BasicTypesV2
import io.circe.generic.auto.*
import io.circe.syntax.*
import json.messages.v2.Echo
import server.v2.BasicMessageHandlers.EchoHandler

class EchoTest {

  def parseEnvelope(raw: String): EnvelopeV2 = {
    val r = for {
      js <- parser.parse(raw)
      env <- BasicTypesV2.envelopeDecoder.decodeJson(js)
    } yield env
    r match
      case Left(value) => throw value
      case Right(value) => value
  }

  @Test
  def decodeInit(): Unit = {
    val raw = """{"id":0,"src":"c0","dest":"n0","body":{"type":"init","node_id":"n0","node_ids":["n0"],"msg_id":1}}"""
    val env = parseEnvelope(raw)
    BasicTypesV2.initBodyDecoder.decodeJson(env.body.toJson) match
      case Left(value) => throw value
      case Right(value) => {}
//    for {
//      js <- parser.parse(raw)
//      env <- BasicTypesV2.envelopeDecoder.decodeJson(json)
//    }
  }

  @Test
  def testBasicEcho(): Unit = {
    val server = TestServer()
    server.registerMessageHandler(EchoHandler)
    val rawJS = """{
                  |  "src": "c1",
                  |  "dest": "n1",
                  |  "body": {
                  |    "type": "echo",
                  |    "msg_id": 1,
                  |    "echo": "Please echo 35"
                  |  }
                  |}""".stripMargin
    (for {
      json <- parser.parse(rawJS)
      env <- BasicTypesV2.envelopeDecoder.decodeJson(json)
    } yield {
      server.handleMessage(env)
    }).swap.foreach{throw _}
    server.executor.runAllTasks()
    Assert.assertEquals(1, server.messageIO.outputBuffer.size)
    val json = server.messageIO.outputBuffer.remove(0).body.toJson
    json.as[Echo.EchoReply] match
      case Left(value) => throw value
      case Right(value) => {
        Assert.assertEquals(1L, value.in_reply_to)
        Assert.assertEquals("Please echo 35", value.echo)
      }

  }



}
