package server

import io.circe.*
import io.circe.parser.*
import json.messages.BasicMessages.*
import json.messages.JSONParser
import json.messages.JSONParser.Envelope
import server.Servers.Node

import java.io.{BufferedReader, InputStreamReader}
import java.util.Date

object Main {

  @volatile
  private var maybeServer: Option[Node] = None

  def handle(json: Json): Unit = {
    for {
      env <- JSONParser.decodeEnvelope.decodeJson(json)
    } yield {
      (maybeServer, env.body) match {
        case (None, InitBody(msgId, myId, allNodes)) => {
          val server = Servers.ThreadConfinedServer(myId, allNodes, env => send(env))
          Handlers2.registerHandlers(server)
          Handlers3e.registerHandlers(server)
          maybeServer = Some(server)
          write(Envelope(env.dest, env.src, InitReply(msgId + 1, msgId)).toJson)
        }
        case (Some(server), _) => server.handleMessage(env)
        case _ => sys.error(s"unknown state: $maybeServer $env")
      }
    }
  }

  def send(env: Envelope): Unit = {
    write(env.toJson)
  }

  def write(json: Json): Unit = {
    this.synchronized {
      val str = json.noSpaces
      System.err.println(s"${new Date()} -> sending $str")
      System.out.println(str)
      System.out.flush()
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
      if (!line.isBlank) {
        System.err.println(s"${new Date()} -> received $line")
        parse(line) match
          case Right(envelope) => handle(envelope)
          case Left(error) => throw error
      }
    }
  }


}
