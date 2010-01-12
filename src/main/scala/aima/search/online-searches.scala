package aima.search

trait OnlineDFS[P,A] {

  val problem: OnlineSearchProblem[P,A]

  import scala.collection.mutable.{Map,Stack}

  //map of (from,to) -> action
  private val Result = Map[(P,P),A]()
  private val Unexplored = Map[P,Stack[A]]()
  private val Unbacktracked = Map[P,Stack[P]]()

  private var s: Option[P] = None //previous state
  private var a: Option[A] = None //previous action

  def search(sPrime: P): Option[A] = {
    if (problem.goalTest(sPrime)) a = None
    else {
      if (!Unexplored.contains(sPrime)) Unexplored += (sPrime -> problem.actions(sPrime))

      //s and a are previous percept and action respectively
      (s,a) match {
        case (Some(x),Some(y)) => {
          //If we've already seen it, don't put it again on Unbacktracked
          if(!Result.contains(x,sPrime)) {
            Result += ((x,sPrime) -> y)
            Unbacktracked.getOrElseUpdate(sPrime, new Stack[P]()).push(x)
          }
        }
        case _ => ;
      }

      Unexplored.get(sPrime) match {
        case Some(x) if x.isEmpty => {
          Unbacktracked.get(sPrime) match {
            case None => a = None
            case Some(y) if y.isEmpty => a = None
            case Some(y) => a = Some(action(sPrime,y.pop))
          }
        }
        case Some(x) => a = Some(x.pop)
        case None => throw new IllegalStateException("key should exist")
      }
    }

    s = Some(sPrime)
    a
  }

  // Returns the action that takes agent from "from" to "to"
  // provided such an action exists and has been explored
  private def action(from: P, to: P): A =
    Result.get((from,to)) match {
      case Some(a) => a
      case None => throw new IllegalStateException(from + "to" + to + "has not been explored yet or no such direct path exists.")
    }
    
  private def reset {
    s = None
    a = None
    Result.clear
    Unexplored.clear
    Unbacktracked.clear
  }
}   
  
abstract class OnlineSearchProblem[P,A] {
  
  import scala.collection.mutable.Stack

  def actions(s: P): Stack[A]
  def goalTest(s: P): Boolean
  //heuristic function, estimated cost to reach
  //goal from state "s"
  def h(s: P): Double
  //cost to reach "to" from "from" in one step by
  //taking "action"
  def stepCost(from: P, action: A, to: P): Double
}

trait LRTAStar[P,A] {

  val problem: OnlineSearchProblem[P,A]

  import scala.collection.mutable.{Map}
  //H, a table of cost estimates indexed by state
  private val H = Map[P,Double]()
  //Result, a table indexed with (action,state) -> state
  private val Result = Map[(A,P),P]()

  //previous state and action
  private var s:Option[P] = None
  private var a:Option[A] = None

  def search(sPrime: P): Option[A] = {

    if(problem.goalTest(sPrime)) a = None
    else {
      //if sPrime is a new state(not in H)
      if (!H.contains(sPrime))
        H += (sPrime -> problem.h(sPrime))

      (s,a) match {
        case (Some(prevS),Some(prevA)) => {
          //unless s is null
          Result += ((prevA,prevS) -> sPrime)
          H += (prevS -> 
                problem.actions(prevS).map((p) => LRTAStarCost(prevS,p,Result.get((p,prevS)),H)).
                  foldLeft(Math.MAX_DOUBLE)((p,q) => if(p < q) p else q)) }
        case _ => ;
      }

      val as = problem.actions(sPrime)
      a = Some(as.foldLeft(as.pop)(
                  (p,q) => if(LRTAStarCost(sPrime,p,Result.get((p,sPrime)),H) < LRTAStarCost(sPrime,q,Result.get((q,sPrime)),H)) p else q))
    }
    s = Some(sPrime)
    a
  }

  private def LRTAStarCost(s: P,a: A,sPrime: Option[P],H: Map[P,Double]): Double =
    sPrime match {
      case Some(x) if H.contains(x) => problem.stepCost(s,a,x) + H.getOrElse(x,0.0)
      case _ => problem.h(s)
    }

  def reset {
    s = None
    a = None
    H.clear
    Result.clear
  } 
}

import aima.basic.{Environment,Agent}

//Agent and Environment
abstract class MapAgent[S] extends Agent[In[S],Go[S]] {
  var currentState:In[S]
}

class MapEnvironment[T <: MapAgent[S],S] extends Environment[T,In[S],Go[S]] {

  override def executeAction(agent: T, action: Option[Go[S]]) {
    action match {
      case Some(Go(s)) => agent currentState_= In(s)
      case None => agent.die()
    }
  }

  override def getPerceptSeenBy(agent: T) = agent.currentState
}


//OnlineDFS + Map Problem
class OnlineDFSMapAgent[S](prob: OnlineSearchMapProblem[S],initState: In[S]) extends MapAgent[S] with OnlineDFS[In[S],Go[S]] {
  var currentState = initState
  val problem = prob
  override def agentProgram(percept: In[S]) = search(percept)
}

class LRTAStarMapAgent[S](prob: OnlineSearchMapProblem[S],initState: In[S]) extends MapAgent[S] with LRTAStar[In[S],Go[S]] {
  var currentState = initState
  val problem = prob
  override def agentProgram(percept: In[S]) = search(percept)
}

class OnlineSearchMapProblem[S](locationMap: LocationMap[S], goalState: In[S]) 
extends OnlineSearchProblem[In[S],Go[S]] {

  import scala.collection.mutable.Stack

  def actions(s: In[S]) = {
    val In(p) = s
    val result = new Stack[Go[S]]()
    locationMap.getLocationsReachableFrom(p).foreach((a:S) => result.push(Go(a)))
    result
  }

  def goalTest(s: In[S]) = s == goalState

  def h(s: In[S]) =
    (s,goalState) match {
      case (In(x),In(y)) => locationMap.straightLineDistance(x,y) }

  def stepCost(from: In[S], action: Go[S], to: In[S]) = 
    (from,to) match {
      case (In(x),In(y)) => locationMap.distance(x,y) }
}
