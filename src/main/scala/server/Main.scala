package server

import json.messages.BasicMessages.*
import json.messages.JSONParser
import json.messages.JSONParser.Envelope

import java.io.{BufferedReader, InputStreamReader}

object Main {

  import dev.mccue.json.Json

  var node = ""
  var knownNodes: List[String] = List()

  def handle(json: Json): Unit = {
    val env = JSONParser.Envelope.fromJson(json)
    env.body match
      case InitBody(id, nodeId, nodeIds) => {
        this.node = nodeId
        this.knownNodes = nodeIds
        write(Envelope(Option(this.node), env.src, InitReply(id + 1, id)).toJson)
      }
      case Echo(echo) => write(Envelope(Option(this.node), env.src, EchoReply(echo)).toJson)
      case _ => ???
  }


  def write(json: Json): Unit = {
    this.synchronized {
      System.out.println(Json.writeString(json))
      System.out.flush()
    }
  }

  def main(args: Array[String]): Unit = {

    val reader = new BufferedReader(new InputStreamReader(System.in))
    while (true) {
      val line = reader.readLine()
      if (line == null) {
        return
      }
      val json = Json.readString(line)
      handle(json)
    }
  }


}
