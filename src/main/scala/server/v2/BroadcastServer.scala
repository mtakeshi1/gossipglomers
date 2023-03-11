package server.v2

import server.v2.BasicMessageHandlers.{EchoHandler, IdGeneratorHandler}

object BroadcastServer {

  def main(args: Array[String]): Unit = {
    ServersV2.bootstrapServer(BroadcastHandler())
  }

}
