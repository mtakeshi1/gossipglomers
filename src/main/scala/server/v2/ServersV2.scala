package server.v2

import io.circe.Json
import json.messages.JSONParser.Envelope

object ServersV2 {

  trait MessageHandler {
    def handleMessage(json: Json, server: ServerImplementor): Unit
  }

  trait Server {
    def myId: String
    def allNodes: List[String]
    
    def newId(): Long

    def handleMessage(envelope: Json): Unit
    def registerMessageHandler(m: Json => Boolean, handler: MessageHandler): Unit
  }

  trait ServerImplementor extends Server {
    def sendMessage(msg: () => Envelope): Unit
    def sendMessageLater(msg: () => Envelope, delayMillis: Long): Unit

    def sendMessageDurably(messageId: Long, msg: () => Envelope): Unit
    def sendMessageHandleResponse(messageId: Long, msg: () => Envelope, responseCallback: Json => Unit): Unit

    def maxRetries: Int = Integer.MAX_VALUE
    def retryDelayMillis: Int = 1000
    def broadcastTarget: List[String]
    def isAcked(msgId: Long): Boolean
    def log(message: => String): Unit = synchronized {
      System.err.println(message)
    }
    def delay(task: Runnable, delayyMillis: Long): Unit
  }

}
