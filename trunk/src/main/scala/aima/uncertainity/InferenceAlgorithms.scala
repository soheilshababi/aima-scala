package aima.uncertainity

/** ENUMERATION-ASK, described in Fig 14.9
 *
 * @author Himanshu Gupta
 */
object EnumerationAsk {
  def apply(X: RandomVariable, e: Map[RandomVariable,String], bn: BayesNet): Map[String,Double] = {

    val q = X.domain.map(x => (x -> enumerateAll(bn.variables, e + (X -> x),bn))).
      foldLeft(Map[String,Double]())(_ + _)
    //normalize
    val alpha = 1/q.values.reduceLeft(_ + _) //normalization constant
    q.transform((_,v) => alpha*v)
  }

  def enumerateAll(vars: List[RandomVariable], e: Map[RandomVariable,String], bn: BayesNet): Double =
    vars match {
      case Nil => 1.0
      //Parents(y) will always be present in the evidence e
      case y :: rest if e.contains(y) =>
        bn.getProbability(y,e(y),e) * enumerateAll(rest,e,bn)
      case y :: rest =>
        y.domain.map(v => bn.getProbability(y,v,e) * enumerateAll(rest,e + (y -> v),bn)).reduceLeft(_ + _)
    }
}


/** ENUMERATION-ASK with Variable elimination, described in Fig 14.11
 *
 * @author Himanshu Gupta
 */
object EnumerationAskWithVariableElimination {

  def apply(X: RandomVariable, e: Map[RandomVariable,String], bn: BayesNet): Map[String,Double] = {

    def hidden(x: RandomVariable) = x != X && !e.contains(x)

    var factors = collectFactors(e,bn)

    for(x <- order(bn.variables)) {
      if(hidden(x))
        factors = sumOut(x,factors)
    }

    val factor = factors.reduceLeft(pointwiseProduct(_,_))

    //now the factor should only contain the query variable
    if(factor.variables != Set(X))
      throw new RuntimeException("Variables in final factor " + factor.variables + " not matching with " + X)
    else {
      val q = factor.ptable.keySet.foldLeft(Map[String,Double]())(
        (m,k) => m + (k(X) -> factor.ptable(k)))
      //normalize
      val alpha = 1/q.values.reduceLeft(_ + _) //normalization constant
      q.transform((_,v) => alpha*v)
    }
  }

  private def order(variables: List[RandomVariable]) = variables

  private def collectFactors(e: Map[RandomVariable,String], bn: BayesNet): Set[Factor] =
    Set(bn.variables.map(makeFactor(_,e,bn)):_*)

  private def makeFactor(x: RandomVariable, e: Map[RandomVariable,String], bn: BayesNet) = {
    val parentsX = bn.parents(x)
    val vars = parentsX + x

    //for all variables in vars, make a map of [RandomVariable,String]
    //which exists in evidence e
    val varsInE = e.filter((x:(RandomVariable,String)) => vars.contains(x._1))

    if(varsInE.isEmpty) new Factor(vars,bn.cpt(x))
    else {
      var cpt = bn.cpt(x)
      //whatever is in the evidence, remove it from cpt keys
      //keep the one that agrees with evidence
      cpt = cpt.keySet.foldLeft(Map[Map[RandomVariable,String],Double]())(
        (m,k) =>
          if(varsInE.forall(x => k(x._1) == x._2))
            m + ((k -- varsInE.keySet) -> cpt(k))
          else m)
      new Factor(vars.filter(!e.contains(_)),cpt)
    }
  }

  def pointwiseProduct(f1: Factor, f2: Factor): Factor = {
    //find union of variables in f1 and f2
    val allVars = f1.variables ++ f2.variables
    val size = allVars.size

    val ptbl1 = f1.ptable
    val ptbl2 = f2.ptable

    val ptbl = ptbl1.keySet.foldLeft(Map[Map[RandomVariable,String],Double]())(
      (m,pk1) => {
        ptbl2.keySet.foldLeft(m)(
          (m,pk2) => {
            val k = Set(pk1.toSeq:_*) ++ Set(pk2.toSeq:_*)
            if(k.size == size)
              m + ((pk1 ++ pk2) -> ptbl1(pk1)*ptbl2(pk2))
            else m
          })})

    new Factor(allVars,ptbl)
  }
    
  def sumOut(x: RandomVariable, factors: Set[Factor]): Set[Factor] = {
    //take the relevant ones
    val relevants = factors.filter(_.variables.exists(x == _))

    if(relevants.size > 0) {
      val factor = relevants.reduceLeft(pointwiseProduct(_,_))
      (factors -- relevants) + sumOutAFactor(x,factor)
    }
    else factors
  }

  //Sums out given Random variable from a Factor and returns the
  //new Factor
  def sumOutAFactor(x: RandomVariable, factor: Factor): Factor = {
    val otherVars = factor.variables - x
    val allKeys = allCombinations(otherVars)
    
    val oldPtbl = factor.ptable

    val newPtbl = allKeys.foldLeft(Map[Map[RandomVariable,String],Double]())(
      (m,a) => m + (a -> oldPtbl.keySet.foldLeft(0.0)(
        (p,k) =>
          if(a.forall(x => k(x._1) == x._2)) p+oldPtbl(k)
          else p)))

    new Factor(otherVars,newPtbl)
  }

  //Given a set of variables, it returns all possible combinations for probability
  //distribution
  //For example, if we give variables = Set(RandomVariable(A),RandomVariable(B))
  //It returns Set(Map((A,true), (B,true)), 
  //               Map((A,true), (B,false)),
  //               Map((A,false), (B,true)),
  //               Map((A,false), (B,false)))
  def allCombinations(variables: Set[RandomVariable]) = {
    def loop(variables: List[RandomVariable], result: Set[Map[RandomVariable,String]]): Set[Map[RandomVariable,String]] =
      variables match {
        case Nil => result
        case x :: rest =>
          loop(rest,result.flatMap(s => x.domain.map((v:String) => s + (x -> v))))
      }

    loop(variables.toList,Set(Map.empty))
  } 
}

class Factor(val variables: Set[RandomVariable],
             val ptable: Map[Map[RandomVariable,String],Double])





/** PRIOR-SAMPLE, described in Fig 14.13
 *
 * @author Himanshu Gupta
 */
object PriorSample {
  def apply(bn: BayesNet): Map[RandomVariable,String] =
    bn.variables.foldLeft(Map[RandomVariable,String]())(
      (m,x) => m + (x -> randomSample(x,m,bn)))

  //Returns one value from domain of x, as per given probability
  def randomSample(x: RandomVariable, parentX: Map[RandomVariable,String], bn: BayesNet) = {
    val pd = bn.getProbabilityDistribution(x,parentX)
    
    val keys = pd.keySet.toList                                   //e.g. (true,false)
    val values = keys.map(pd(_))                                  //e.g. (0.7,0.3)
    val intervals = values.foldLeft(List[Double]())(
      (l,v) => l match {
        case Nil => v :: l
        case x :: _ => (v + x) :: l
      }).reverse                                                  //e.g. (0.7,1.0)

    //select a random # between 0.0 and 1.0, look at the interval
    //it falls in and return that string from the keys
    val rand = Math.random
    val index = intervals.findIndexOf(rand < _)
    if(index < 0) keys.last else keys(index)
  }
}


/** REJECTION-SAMPLING, described in Fig 14.14
 *
 * @author Himanshu Gupta
 */
object RejectionSampling {
  def apply(x: RandomVariable, e: Map[RandomVariable,String], bn: BayesNet, n: Int) = {
    
    def loop(n: Int, samples: Map[String,Int]): Map[String,Int] =
      if(n > 0) {
        val event = PriorSample(bn)
        if(isConsistent(event,e)){
          //find value of query variable in it and add to sample
          val str = event(x)
          loop(n-1,samples + (str -> (samples(str)+1)))
        }
        else loop(n-1,samples)
      }
      else samples

    val q = loop(n, x.domain.foldLeft(Map[String,Int]())(
      (m,d) => m + (d -> 0)))
    //normalize
    val alpha = 1.0/q.values.reduceLeft(_ + _) //normalization constant
    q.transform((_,v) => alpha*v)
                 
  }

  def isConsistent(event: Map[RandomVariable,String], evidence: Map[RandomVariable,String]) =
    evidence.forall((x:(RandomVariable,String)) => event(x._1) == x._2)
}

