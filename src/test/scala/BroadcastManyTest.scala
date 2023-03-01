import json.messages.Chapter3.{Broadcast, BroadcastMany, BroadcastManyOk}
import json.messages.JSONParser.Envelope
import org.junit.{Assert, Test}
import server.{Handlers2, Handlers3e}
import server.Servers.ThreadConfinedServer

import java.util.concurrent.{LinkedBlockingQueue, SynchronousQueue, TimeUnit}

class BroadcastManyTest {

  def setup(): (LinkedBlockingQueue[Envelope], ThreadConfinedServer) = {
    val queue = new LinkedBlockingQueue[Envelope]()
    val server = ThreadConfinedServer("n3", List("n0","n1","n2","n3"), env => queue.put(env))
    Handlers2.registerHandlers(server)
    Handlers3e.registerHandlers(server)
    (queue, server)
  }

//  @Test
  def testMany(): Unit = {
    val (queue, server) = setup()

    server.handleMessage(Envelope(Option("c11"), Option("n4"), Broadcast(1, 0)))

    println(queue.take())

    queue.take().body match
      case BroadcastMany(msg_id, messages) => {
        Assert.assertTrue(messages.contains(0L))
        server.handleMessage(Envelope(Option("c11"), Option("n4"), BroadcastManyOk(msg_id+1, msg_id)))
      }

    server.handleMessage(Envelope(Option("n0"), Option("n4"), BroadcastMany(1, List(1))))
    println(queue.take())


    server.handleMessage(Envelope(Option("c11"), Option("n4"), Broadcast(1, 2)))
    println(queue.take())
    queue.poll(5, TimeUnit.SECONDS).body match
      case BroadcastMany(msg_id, messages) => {
        Assert.assertTrue(messages.contains(2L))
        server.handleMessage(Envelope(Option("c11"), Option("n4"), BroadcastManyOk(msg_id + 1, msg_id)))
      }

  }


}
