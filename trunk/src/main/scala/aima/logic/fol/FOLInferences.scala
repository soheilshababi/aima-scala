package aima.logic.fol

import aima.commons.Utils

object FOLFCAsk {
  //Empty set returned means failure
  def apply(KB: FOLKnowledgeBase, alpha: AtomicSentence): Set[Map[Variable,Term]] = {

    //TODO: check that KB contains Definite Clauses only

    //As explained in section 9.4.1, a query like Person(x) can be proved with
    //multiple substitutions like x -> John and x -> Richard
    //so the result is a Set of all substitutions possible
    var result = Set[Map[Variable,Term]]()

    //Add already alpha matching sentences from KB
    //to the result set
    result = result ++ KB.fetch(alpha)

    println("Alpha is: " + alpha)
    val rules = KB.implicationDefiniteClauses
    println("rules: " + rules)

    def loop: Set[Map[Variable,Term]] = {

      def rulesLoop(KB: FOLKnowledgeBase, rules: List[ImplicationDefiniteClause], shouldLoopContinue: Boolean): Set[Map[Variable,Term]] = {
        rules match {
          case rule :: rest =>
            val clause = standardizeVariables(rule,KB)
            println("rules are: " + rules)
            println("standardized rule is: " + clause)
          
            def unifierLoop(unifiers: List[Map[Variable,Term]],neW: Set[AtomicSentence]): Set[Map[Variable,Term]] = {
              println("Unifiers are: " + unifiers)
              println("new: " + neW)
              unifiers match {
                case unifier :: rest =>
                  val qPrime = Subst(unifier, clause.conclusion)
                  if (KB.fetch(qPrime).isEmpty && KB.fetch(qPrime,neW).isEmpty) {
                    val phi = Unify(qPrime,alpha)
                    if(phi != None)
                      result = result + phi.get
                    unifierLoop(rest,neW + qPrime)
                  }
                  else unifierLoop(rest,neW)
                case Nil => rulesLoop(KB.tell(neW),rest, !neW.isEmpty)
              }}
            unifierLoop(KB.fetch(clause.premise).toList,Set[AtomicSentence]())
          case Nil if shouldLoopContinue => loop
          case Nil => result
        }
      }
      rulesLoop(KB,rules.toList,false)
    }

    loop
  }

  def standardizeVariables(c: ImplicationDefiniteClause,KB: FOLKnowledgeBase) = {
    val theta = Map(CollectVariables(c).map(
      v => v -> KB.generateVariable(v.symbol)).toList:_*)
    new ImplicationDefiniteClause(
      Subst(theta,c.premise), Subst(theta,c.conclusion))
  }
}


object FOLBCAsk {
  
  //Empty set returned means failure
  def apply(KB: FOLKnowledgeBase, query: AtomicSentence): Set[Map[Variable,Term]] =
    FOLBCOr(KB,query,Some(Map()))

  def FOLBCOr(KB: FOLKnowledgeBase, goal: AtomicSentence, theta: Option[Map[Variable,Term]]): Set[Map[Variable,Term]] = {    
    var result = Set[Map[Variable,Term]]()

    val rules = FetchRulesForGoal(KB,goal)
    for(rule <- rules) {
      val stRule = standardizeVariables(rule,KB)
      val (lhs,rhs) = stRule match {
        case x: AtomicSentence => (Nil,x)
        case x: ImplicationDefiniteClause => (x.premise.toList,x.conclusion)
      }
      result = result ++ FOLBCAnd(KB,lhs,Unify(rhs,goal,theta))
    }
    result
  }

  def FOLBCAnd(KB: FOLKnowledgeBase, goals: List[AtomicSentence], theta: Option[Map[Variable,Term]]): Set[Map[Variable,Term]] =
    if(theta == None) Set()
    else {
      if(goals.isEmpty) Set(theta.get)
      else {
        val first :: rest = goals

        val firstResults = FOLBCOr(KB, first, theta)
        val restResults = FOLBCAnd(KB, rest, theta)

        firstResults.flatMap(m =>
          restResults.map(Unify.merge(_,m)).filter(_ != None)).map(_.get)
      }
    }

  def FetchRulesForGoal(KB: FOLKnowledgeBase,goal: AtomicSentence): Set[FOLDefiniteClause] =
    KB.definiteClauses.filter(
      _ match {
        case x: AtomicSentence => Unify(x,goal) != None
        case x: ImplicationDefiniteClause => Unify(x.conclusion,goal) != None
      })

  def standardizeVariables(c: FOLDefiniteClause, KB: FOLKnowledgeBase): FOLDefiniteClause =
    c match {
      case x: AtomicSentence =>
        Subst(Map(CollectVariables(x).map(
          v => v -> KB.generateVariable(v.symbol)).toList:_*),x)
      case x: ImplicationDefiniteClause =>
        val theta = Map(CollectVariables(x).map(
          v => v -> KB.generateVariable(v.symbol)).toList:_*)
        new ImplicationDefiniteClause(
        Subst(theta,x.premise), Subst(theta,x.conclusion))
    }
}

/** Resolution with Two-Finger-Method
 *
 * @author Himanshu Gupta
 */
object FOLResolution {
  def apply(KB: FOLKnowledgeBase, alpha: Sentence): Boolean = {
    val clauses = KB.tell(new Negation(alpha)).clauses
    println("The clauses are: " + clauses)

    def loop(clauses: Set[Clause], newSet: Set[Clause]):Boolean = {
      //println("clauses " + (clauses ++ newSet))
      loopIn(makePairs(clauses, newSet),Set.empty[Clause]) match {
        case None => true //Empty clause found, return true
        case Some(s) =>
          //println("newSet " + s)
          val oldClauses = clauses ++ newSet
          if (s.subsetOf(oldClauses)) false
          else loop(oldClauses,s)
      }}

    def loopIn(pairs: List[(Clause,Clause)], newSet: Set[Clause]): Option[Set[Clause]] =
      pairs match {
        case (c1,c2) :: rest =>
          val resolvents = resolve(c1,c2)
        if(resolvents.exists(_.isEmpty)) None //Empty clause found
        else loopIn(rest,newSet ++ resolvents)
        case Nil => Some(newSet)
      }

    /** Returns result of pairing each clause in "clauses" with each in "newSet"
     * plus the pairs made by combining clause in "newSet" with each other
     */
    def makePairs(clauses: Set[Clause], newSet: Set[Clause]): List[(Clause,Clause)] = {
      (for(ci <- clauses; cj <- newSet) yield (ci,cj)).toList ++ Utils.pairs(newSet.toList)
    }

    loop(Set.empty[Clause],clauses)
  }

/*
  def apply(KB: FOLKnowledgeBase, query: Sentence) = {
    //KB /\ ~query
    KB.tell(new Negation(query))
    tfm(KB.clauses)
  }

  def tfm(clauses: Set[Clause]) = {
    
    def iter(delta: Set[Clause], fast: List[Clause], slow: List[Clause]): Option[Set[Clause]] = {
      println("Fast: " + fast)
      println("Slow: " + slow)
      println("====================================\n")
      slow match {
        case Nil => None
        case sx :: restS => {
          val newDelta = delta ++ resolve(fast.head,sx)
          if(newDelta.exists(_.isEmpty)) Some(newDelta)
          else {
            if(fast == slow)
              iter(newDelta, newDelta.toList,restS)
            else
              iter(newDelta, fast.tail,slow)
          }
        }
      }
    }


    val l = clauses.toList
    iter(clauses,l,l)
  }
*/


  def resolve(c1: Clause, c2: Clause) = {

    val c2Literals = c2.literals.toList

    def loop(ls: List[Literal], result: Set[Clause]): Set[Clause] =
      ls match {
        case l :: rest =>
          findComplimentary(l, c2Literals) match {
            case None =>
              loop(rest, result)
            case Some((x,m)) =>
              println("Just resolved: " + l + " and " + x)
              println("A Unifier found is: " + m)
              loop(rest, result + Subst(m,new Clause(((c1.literals - l) ++ (c2.literals - x)).toList:_*)))
          }
        case Nil => result
      }

    //Find complimentary literal of l if it exists in ls
    def findComplimentary(l: Literal, ls: List[Literal]) : Option[(Literal,Map[Variable,Term])] =
      ls match {
        case x :: rest =>
          if((l.isPositive && x.isNegative) || (l.isNegative && x.isPositive)) {
            val unifier = Unify(l.sentence, x.sentence)
            unifier match {
              case None => findComplimentary(l,rest)
              case Some(m) => Some(x,m)
            }
          }
          else findComplimentary(l,rest)
        case Nil => None
      }

    loop(c1.literals.toList,Set.empty)
  }
}
    
