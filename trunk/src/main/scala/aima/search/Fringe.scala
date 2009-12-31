package aima.search

//abstract Fringe
abstract class Fringe[A] {
  
  def isEmpty: Boolean
  def insert(elm: A): Fringe[A]
  def removeFirst: Option[A]
  
  def insertAll(s: List[A]) = {
    def loop(s: List[A]): Fringe[A] = 
      s match {
        case Nil => this
        case x :: rest => insert(x); loop(rest)
      }
    loop(s)
  }
}

class LifoFringe[A] extends Fringe[A] {
  private var lifoQ = List[A]()

  def isEmpty = lifoQ.isEmpty

  def insert(elm:A) = {
    lifoQ = elm :: lifoQ
    this
  }

  def removeFirst: Option[A] =
    lifoQ match {
      case Nil => None
      case x :: rest => lifoQ = rest; Some(x)
    }
      
}

class FifoFringe[A] extends Fringe[A] {

  import scala.collection.immutable.Queue

  private var fifoQ: Queue[A] = Queue.Empty

  def isEmpty = fifoQ.isEmpty

  def insert(elm:A) = {
    fifoQ = fifoQ.enqueue(elm)
    this
  }

  def removeFirst : Option[A] =
    if (fifoQ.isEmpty) None
    else {
      fifoQ.dequeue match {
        case (first,rest) => fifoQ = rest; Some(first)
      }
    }
}

class PriorityQueueFringe[A](orderer: (A)=>Ordered[A]) extends Fringe[A] {
  
  import scala.collection.mutable.PriorityQueue

  private val pq = new PriorityQueue[A]()(orderer)

  def isEmpty = pq.isEmpty

  def insert(elm: A) = {
    pq += elm
    this
  }

  def removeFirst : Option[A] =
    if (pq.isEmpty) None else Some(pq.dequeue)
}
