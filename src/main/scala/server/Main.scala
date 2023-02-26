package server

import io.circe.*
import io.circe.parser.*
import json.messages.BasicMessages.*
import json.messages.JSONParser
import json.messages.JSONParser.Envelope

import java.io.{BufferedReader, InputStreamReader}

object Main {

  @volatile
  private var maybeServer: Option[Server] = None

  def handle(json: Json): Unit = {
    for {
      env <- JSONParser.decodeEnvelope.decodeJson(json)
    } yield {
      (maybeServer, env.body) match {
        case (None, InitBody(msgId, myId, otherNodes)) => {
          maybeServer = Some(Server(myId, otherNodes))
          write(Envelope(env.dest, env.src, InitReply(msgId + 1, msgId)).toJson)
        }
        case (Some(server), _) => server.handle(env)
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
      System.err.println("sending " + str)
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
        System.err.println("received " + line)
        parse(line) match
          case Right(envelope) => handle(envelope)
          case Left(error) => throw error
      }
    }
  }


}
