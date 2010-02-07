package aima.logic.propositional

import aima.commons.Utils
import scala.collection.immutable.{Set}

/** TT-ENTAILS, described in Fig 7.10
 *
 * @author Himanshu Gupta
 */
object TTEntails {
  def apply(KB: Conjunction,alpha: Sentence): Boolean = {
    val symbols = KB.symbols ++ alpha.symbols
    ttCheckAll(KB,alpha,symbols.toList,Map[PropositionSymbol,Boolean]())
  }

  private def ttCheckAll(KB: Sentence,alpha: Sentence,
                         symbols: List[PropositionSymbol],model: Map[PropositionSymbol,Boolean]): Boolean = {
    symbols match {
      case Nil =>
        KB.isTrue(model) match {
          case Some(true) =>
            alpha.isTrue(model) match {
              case Some(x) => x
              case None => throw new IllegalStateException("Model " + model + " does not contain all symbols.")
            }
          case Some(false) => true //when KB is false, always return true
          case None => 
            throw new IllegalStateException("Model " + model + " does not contain all symbols.")
        }
      case first :: rest =>
        (ttCheckAll(KB,alpha,rest,model + (first -> true)) 
         && 
         ttCheckAll(KB,alpha,rest,model + (first -> false)))
    }
  }
}

/** PL-RESOLUTION, described in Fig 7.12
 *
 * @author Himanshu Gupta
 */
object PLResolution {
  def apply(KB: Conjunction, alpha: Sentence): Boolean = {
    val clauses = SentenceToCNF(Sentence.addToKB(KB, new Negation(alpha))).clauses

    def loop(clauses: Set[Clause]):Boolean =
      loopIn(Utils.pairs(clauses.toList),Set.empty) match {
        case None => true //Empty clause found, return true
        case Some(newSet) =>
          if (newSet.subsetOf(clauses)) false
          else loop(newSet ++ clauses)
      }

    def loopIn(pairs: List[(Clause,Clause)], newSet: Set[Clause]): Option[Set[Clause]] =
      pairs match {
        case (c1,c2) :: rest =>
          val resolvents = plResolve(c1,c2)
        if(resolvents.exists(_.isEmpty)) None //Empty clause found
        else loopIn(rest,newSet ++ resolvents)
        case Nil => Some(newSet)
      }

    loop(clauses)
  }

  private def plResolve(c1: Clause, c2: Clause): Set[Clause] = {
    
    def loop(ls: List[Literal], result: Set[Clause]): Set[Clause] =
      ls match {
        case (l:PositiveLiteral) :: rest =>
          if(c2.literals.exists(_ == NegativeLiteral(l.symbol)))
            loop(rest,result + new Clause(((c1.literals - l) ++ (c2.literals - NegativeLiteral(l.symbol))).toList:_*))
          else
            loop(rest,result)
        case (l:NegativeLiteral) :: rest =>
          if(c2.literals.exists(_ == PositiveLiteral(l.symbol)))
            loop(rest,result + new Clause(((c1.literals - l) ++ (c2.literals - PositiveLiteral(l.symbol))).toList:_*))
          else
            loop(rest,result)
        case Nil => result
      }

    val resolvents = loop(c1.literals.toList,Set.empty)

    //check if a list of literals contain Positive as well as Negative literal
    //for the same symbol
    def isDiscardable(ls: List[Literal]) =
      Utils.pairs(ls).exists( pair =>
                            pair match {
                              case (PositiveLiteral(x),NegativeLiteral(y)) if x == y  => true
                              case (NegativeLiteral(x),PositiveLiteral(y)) if x == y => true
                              case _ => false })
    //discard all such resolvents and return the rest
    resolvents.filter((c:Clause) => !isDiscardable(c.literals.toList))
  }
}

/** PL-FC-ENTAILS?, described in Fig 7.15
 *
 * @author Himanshu Gupta
 */
object PLFCEntails {

  def apply(KB: Set[DefiniteClause],q: PropositionSymbol, knownTrueSymbols: List[PropositionSymbol]): Boolean = {
    
    val count = scala.collection.mutable.Map((KB.map(c => c -> c.premise.size)).toList:_*)
    val inferred = scala.collection.mutable.Map((KB.flatMap(c =>c.premise + c.conclusion).map((_ -> false))).toList:_*)
    val agenda = new scala.collection.mutable.Queue[PropositionSymbol]()
    agenda ++= knownTrueSymbols

    def loop: Boolean = {
      if(agenda.isEmpty) false
      else {
        val p = agenda.dequeue
        if(p == q) true
        else {
          if(!inferred(p)) {
            inferred += (p -> true)
            KB.foreach(c => {
              if(c.premise.contains(p))
                count += (c -> (count(c)-1))
              if(count(c) == 0)
                agenda.enqueue(c.conclusion)
            })
          }
          loop
        }
      }
    }

    loop
  }
}

/** DPLL-SATISFIABLE?, described in Fig 7.17
 *
 * @author Himanshu Gupta
 */
object DPLLSatisfiable {
  def apply(s: Sentence): Boolean =
    DPLL(SentenceToCNF(s).clauses,s.symbols,Map[PropositionSymbol,Boolean]())

  private def DPLL(clauses: Set[Clause],symbols: Set[PropositionSymbol],
                   model: Map[PropositionSymbol,Boolean]): Boolean = {
    if (clauses.forall(_.isTrue(model) == Some(true))) return true
    if(clauses.exists(_.isTrue(model) == Some(false))) return false

    FindPureSymbol(symbols,clauses,model) match {
      case Some((p,value)) => DPLL(clauses, symbols - p, model + (p -> value))
      case None =>
        FindUnitClause(clauses,model) match {
          case Some((p,value)) => DPLL(clauses,symbols - p, model + (p -> value))
          case None =>
            val p = symbols.toList(0)
            val rest = symbols - p
            DPLL(clauses,rest,model + (p -> true)) || DPLL(clauses,rest,model + (p -> false))
        }
    }
  }

  private def FindPureSymbol(symbols: Set[PropositionSymbol], clauses: Set[Clause], model: Map[PropositionSymbol,Boolean]): Option[(PropositionSymbol,Boolean)] = {

    //returns true, if given symbol appears as a Pure PositiveLiteral in given set of clauses
    def isPurePositiveLiteral(p: PropositionSymbol, clauses: Set[Clause], model: Map[PropositionSymbol,Boolean]) =
      clauses.forall(c =>
        (c.literals.contains(PositiveLiteral(p)),c.literals.contains(NegativeLiteral(p))) match {
          case (_,false) => true
          case (_,true) => c.isTrue(model) == Some(true)
        })

    //Returns true, if given symbol appears as a Pure NegativeLiteral in given set of clauses
    def isPureNegativeLiteral(p: PropositionSymbol, clauses: Set[Clause], model: Map[PropositionSymbol,Boolean]) =
      clauses.forall(c =>
        (c.literals.contains(PositiveLiteral(p)),c.literals.contains(NegativeLiteral(p))) match {
          case (false,_) => true
          case (true,_) => c.isTrue(model) == Some(true)
        })

    symbols.find(isPurePositiveLiteral(_, clauses, model)) match {
      case Some(p) => Some((p,true))
      case None =>
        symbols.find(isPureNegativeLiteral(_, clauses, model)) match {
          case Some(q) => Some((q,false))
          case None => None
        }
    }
  }

  private def FindUnitClause(clauses: Set[Clause], model: Map[PropositionSymbol,Boolean]): Option[(PropositionSymbol,Boolean)] = {
    clauses.find( _.literals.filter(_.isTrue(model) != Some(true)).size == 1 ) match {
      case None => None
      case Some(c) => {
        val Some(l) = c.literals.find(_.isTrue(model) != Some(true))
        l match {
          case _:PositiveLiteral => Some((l.symbol,true))
          case _:NegativeLiteral => Some((l.symbol,false))
        }}
    }
  }
}

/** WALKSAT, described in Fig 7.18
 *
 * 0.0 <= probability <= 1.0
 * 
 * @author Himanshu Gupta
 */
/*object WalkSat {

  import scala.collection.immutable.Map

  def apply(clauses: Set[Clause], probability: Double, maxFlips: Int): Option[Map[Symbol,Boolean]] = {

    val random = new scala.util.Random(java.util.Random)()
    val randomModel = Map[Symbol,Boolean](clauses.symbols.map((_,random.nextBoolean)))
    
    def loop(counter: Int, model: Map[Symbol,Boolean]): Option = {
      if (counter < maxFlips) {
        //find clauses that fail in the model
        val failedClauses = clauses.filter(_.isTrue(model) match {
                                            case Some(true) => false
                                            case Some(false) => true
                                            case None =>
                                              throw new IllegalStateException("Model should have all symbols defined.")})
        //doable with current specs
    
    



    



*/