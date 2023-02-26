package json.messages

import scala.annotation.tailrec

object Broadcasts {

  trait BroadcastStragegy {
    def selectNodesToSend(myId: String, allNodes: List[String]): List[String]
  }

  object DefaultStrategy extends BroadcastStragegy {
    override def selectNodesToSend(myId: String, allNodes: List[String]): List[String] = allNodes.filter(_ != myId)
  }

  object BinaryHeapStrategy extends BroadcastStragegy {
    override def selectNodesToSend(myId: String, allNodes: List[String]): List[String] = {
      val s = allNodes.sorted
      val i = s.indexOf(myId)
      val targets = List(i / 2, (i+1) * 2 - 1, (i+1) * 2)
      targets.filter(a => a >= 0 && a < allNodes.size && a != i).map(s.apply)
    }
  }

  case class NTreeStrategy(n: Int, allNodes: List[String]) extends BroadcastStragegy {
    private val sorted = allNodes.sorted

    @tailrec
    final def adjust(n: Int): Int = if (n < 0) adjust(n + allNodes.size) else if (n >= allNodes.size) adjust(n % allNodes.size) else n


    def subtree(from: String): List[String] = {
      val i = allNodes.indexOf(from)
      (0 until n).map(j =>  (i + 1) * n + j - (n -1)).map(adjust).distinct.map(sorted.apply).toList
    }

    val map: Map[String, List[String]] = sorted.map{n => (n, subtree(n))}.toMap


    override def selectNodesToSend(myId: String, allNodes: List[String]): List[String] = map(myId)
  }

  object RingStrategy extends BroadcastStragegy {
    override def selectNodesToSend(myId: String, allNodes: List[String]): List[String] = {
      @tailrec
      def adjust(n: Int): Int = if(n < 0) adjust(n + allNodes.size) else if(n >= allNodes.size) adjust(n % allNodes.size) else n
      val s = allNodes.sorted
      val i = s.indexOf(myId)

      val left = s(adjust(i-1))
      val right = s(adjust(i+1))
      List(left, right)
    }
  }

  object StarStrategy extends BroadcastStragegy {
    override def selectNodesToSend(myId: String, allNodes: List[String]): List[String] = {
      @tailrec
      def adjust(n: Int): Int = if (n < 0) adjust(n + allNodes.size) else if (n >= allNodes.size) adjust(n % allNodes.size) else n

      val s = allNodes.sorted
      val i = s.indexOf(myId)

      val left = s(adjust(i - 1))
      val right = s(adjust(i + 1))
      val mid = s(adjust(i + allNodes.size / 2))
      List(left, right)
    }
  }

  @main
  def main(): Unit = {
    val all = List("n0","n1","n2","n3","n4","n5","n6","n7","n8","n9","n10","n11","n12","n13","n14","n15","n16","n17","n18","n19").sorted
    println(all.zipWithIndex)
    val strat = NTreeStrategy(3, all)
    all.foreach(n => println(n + " -> " + strat.selectNodesToSend(n, all)))

  }
}
