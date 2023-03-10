import io.circe
import json.messages.v2.BasicTypesV2
import json.messages.v2.BasicTypesV2.EnvelopeV2
import server.v2.ServersV2.{DelayedExecutor, MessageIO, ServerImplementor}

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class DeterministicDelay() extends DelayedExecutor {

  val tasks: ListBuffer[Runnable] = ListBuffer[Runnable]()

  override def submit(task: Runnable): Unit = tasks.addOne(task)

  override def schedule(task: Runnable, delay: Long, unit: TimeUnit): Unit = tasks.addOne(task)

  def runAllTasks(): Unit = {
    while (tasks.nonEmpty) runTaskIfPossible()
  }
  
  def runTaskIfPossible(): Boolean = if (tasks.nonEmpty) {
    val t = tasks.remove(0)
    t.run()
    true
  } else false

}


case class BufferIO() extends MessageIO {

  val inputBuffer: ListBuffer[EnvelopeV2] = ListBuffer[EnvelopeV2]()
  val outputBuffer: ListBuffer[EnvelopeV2] = ListBuffer[EnvelopeV2]()

  override def nextMessage(): Either[circe.Error, BasicTypesV2.EnvelopeV2] =
    if (inputBuffer.nonEmpty) Right(inputBuffer.remove(0))
    else Left(circe.ParsingFailure("empty", NullPointerException()))

  override def writeMessage(envelopeV2: BasicTypesV2.EnvelopeV2): Unit = outputBuffer.addOne(envelopeV2)
}

class TestServer(val myId: String, val allNodes: List[String]) extends ServerImplementor {
  def this(node: String) = this(node, List(node))

  def this() = this("anyid")

  val executor: DeterministicDelay = DeterministicDelay()
  val messageIO: BufferIO = BufferIO()

}
