package burlap.behavior.singleagent.planning.stochastic.policyiteration;

import burlap.behavior.policy.EnumerablePolicy;
import burlap.behavior.policy.GreedyQPolicy;
import burlap.behavior.policy.Policy;
import burlap.behavior.singleagent.planning.Planner;
import burlap.behavior.singleagent.planning.PlanningObserver;
import burlap.behavior.singleagent.planning.stochastic.DynamicProgramming;
import burlap.debugtools.DPrint;
import burlap.mdp.core.action.Action;
import burlap.mdp.core.state.State;
import burlap.mdp.singleagent.SADomain;
import burlap.mdp.singleagent.model.FullModel;
import burlap.mdp.singleagent.model.TransitionProb;
import burlap.statehashing.HashableState;
import burlap.statehashing.HashableStateFactory;

import java.util.*;

public class PolicyIteration extends DynamicProgramming implements Planner {

	/**
	 * When the maximum change in the value function is smaller than this value, policy evaluation will terminate. 
	 */
	protected double												maxEvalDelta;
	
	
	/**
	 * When the maximum change between policy evaluations is smaller than this value, planning will terminate.
	 */
	protected double												maxPIDelta;
	
	/**
	 * When the number of policy evaluation iterations exceeds this value, policy evaluation will terminate.
	 */
	protected int													maxIterations;
	
	
	/**
	 * When the number of policy iterations passes this value, planning will terminate.
	 */
	protected int													maxPolicyIterations;
	
	/**
	 * The current policy to be evaluated
	 */
	protected EnumerablePolicy evaluativePolicy;
	
	
	/**
	 * Indicates whether the reachable states has been computed yet.
	 */
	protected boolean												foundReachableStates = false;


	/**
	 * The total number of policy iterations performed
	 */
	protected int													totalPolicyIterations = 0;

	/**
	 * The total number of value iterations used to evaluated policies performed
	 */
	protected int													totalValueIterations = 0;


	/**
	 * Boolean to indicate whether planning as been run at least once
	 */
	protected boolean												hasRunPlanning = false;

	protected List<PlanningObserver>								observers = new ArrayList<PlanningObserver>();

	/**
	 * Initializes the valueFunction.
	 * @param domain the domain in which to plan
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factor to use
	 * @param maxDelta when the maximum change in the value function is smaller than this value, policy evaluation will terminate. Similarly, when the maximum value value function change between policy iterations is smaller than this value planning will terminate.
	 * @param maxEvaluationIterations when the number iterations of value iteration used to evaluate a policy exceeds this value, policy evaluation will terminate.
	 * @param maxPolicyIterations when the number of policy iterations passes this value, planning will terminate.
	 */
	public PolicyIteration(SADomain domain, double gamma, HashableStateFactory hashingFactory, double maxDelta, int maxEvaluationIterations, int maxPolicyIterations){
		this.DPPInit(domain, gamma, hashingFactory);
		
		this.maxEvalDelta = maxDelta;
		this.maxPIDelta = maxDelta;
		this.maxIterations = maxEvaluationIterations;
		this.maxPolicyIterations = maxPolicyIterations;
		
		this.evaluativePolicy = new GreedyQPolicy(this.getCopyOfValueFunction());
	}
	
	
	/**
	 * Initializes the valueFunction.
	 * @param domain the domain in which to plan
	 * @param gamma the discount factor
	 * @param hashingFactory the state hashing factor to use
	 * @param maxPIDelta when the maximum value value function change between policy iterations is smaller than this value planning will terminate.
	 * @param maxEvalDelta when the maximum change in the value function is smaller than this value, policy evaluation will terminate.
	 * @param maxEvaluationIterations when the number iterations of value iteration used to evaluate a policy exceeds this value, policy evaluation will terminate.
	 * @param maxPolicyIterations when the number of policy iterations passes this value, planning will terminate.
	 */
	public PolicyIteration(SADomain domain, double gamma, HashableStateFactory hashingFactory, double maxPIDelta, double maxEvalDelta, int maxEvaluationIterations, int maxPolicyIterations){
		this.DPPInit(domain, gamma, hashingFactory);
		
		this.maxEvalDelta = maxEvalDelta;
		this.maxPIDelta = maxPIDelta;
		this.maxIterations = maxEvaluationIterations;
		this.maxPolicyIterations = maxPolicyIterations;
		
		this.evaluativePolicy = new GreedyQPolicy(this.getCopyOfValueFunction());
	}
	
	
	/**
	 * Sets the initial policy that will be evaluated when planning with policy iteration begins. After the first policy iteration,
	 * the evaluative policy will be {@link burlap.behavior.policy.GreedyQPolicy} on the function evaluation.
	 * @param p the initial policy to evaluate when planning begins.
	 */
	public void setPolicyToEvaluate(EnumerablePolicy p){
		this.evaluativePolicy = p;
	}
	
	
	/**
	 * Returns the policy that was last computed (or the initial policy if no planning has been performed).
	 * @return the policy that was last computed.
	 */
	public Policy getComputedPolicy(){
		return this.evaluativePolicy;
	}
	
	/**
	 * Calling this method will force the valueFunction to recompute the reachable states when the {@link #planFromState(State)} method is called next.
	 * This may be useful if the transition dynamics from the last planning call have changed and if planning needs to be restarted as a result.
	 */
	public void recomputeReachableStates(){
		this.foundReachableStates = false;
	}


	/**
	 * Returns the total number of policy iterations that have been performed.
	 * @return the total number of policy iterations that have been performed.
	 */
	public int getTotalPolicyIterations() {
		return totalPolicyIterations;
	}

	/**
	 * Returns the total number of value iterations used to evaluate policies.
	 * @return the total number of value iterations used to evaluate policies.
	 */
	public int getTotalValueIterations() {
		return totalValueIterations;
	}

	public void addObserver(PlanningObserver o) {
		this.observers.add(o);
	}

	/**
	 * Plans from the input state and then returns a {@link burlap.behavior.policy.GreedyQPolicy} that greedily
	 * selects the action with the highest Q-value and breaks ties uniformly randomly.
	 * @param initialState the initial state of the planning problem
	 * @return a {@link burlap.behavior.policy.GreedyQPolicy}.
	 */
	@Override
	public GreedyQPolicy planFromState(State initialState) {

		int iterations = 0;
		if(this.performReachabilityFrom(initialState) || !this.hasRunPlanning){
			
			double delta;
			do{
				delta = this.evaluatePolicy();
				iterations++;
				this.evaluativePolicy = new GreedyQPolicy(this.getCopyOfValueFunction());
				System.out.println("PI [" + iterations + "] delta: " + delta);
			}while(delta > this.maxPIDelta && iterations < maxPolicyIterations);

			this.hasRunPlanning = true;
			
		}

		DPrint.cl(this.debugCode, "Total policy iterations: " + iterations);
		this.totalPolicyIterations += iterations;

		return (GreedyQPolicy)this.evaluativePolicy;
	}
	
	
	@Override
	public void resetSolver(){
		super.resetSolver();
		this.foundReachableStates = false;
		this.totalValueIterations = 0;
		this.totalPolicyIterations = 0;
	}
	
	/**
	 * Computes the value function under following the current evaluative policy.
	 * @return the maximum single iteration change in the value function
	 */
	protected double evaluatePolicy(){

		if(!this.foundReachableStates){
			throw new RuntimeException("Cannot run VI until the reachable states have been found. Use planFromState method at least once or instead.");
		}
		
		double maxChangeInPolicyEvaluation = Double.NEGATIVE_INFINITY;
		
		Set <HashableState> states = valueFunction.keySet();
		
		int i;
		double delta = 0.;
		for(i = 0; i < this.maxIterations; i++){
			
			for(HashableState sh : states){
				
				double v = this.value(sh);
				double maxQ = this.performFixedPolicyBellmanUpdateOn(sh, this.evaluativePolicy);
				delta = Math.max(Math.abs(maxQ - v), delta);
				
			}
			
			maxChangeInPolicyEvaluation = Math.max(delta, maxChangeInPolicyEvaluation);
			
			if(delta < this.maxEvalDelta){
				i++;
				break; //approximated well enough; stop iterating
			}
		}

		DPrint.cl(this.debugCode, "Iterations in inner VI for policy eval: " + i);
		for (PlanningObserver observer: observers) {
			observer.observe(new GreedyQPolicy(this), i, delta);
		}

		this.totalValueIterations += i;
		
		return maxChangeInPolicyEvaluation;
		
	}
	
	
	
	
	
	/**
	 * This method will find all reachable states that will be used when computing the value function.
	 * This method will not do anything if all reachable states from the input state have been discovered from previous calls to this method.
	 * @param si the source state from which all reachable states will be found
	 * @return true if a reachability analysis had never been performed from this state; false otherwise.
	 */
	public boolean performReachabilityFrom(State si){
		
		
		
		HashableState sih = this.stateHash(si);
		//if this is not a new state and we are not required to perform a new reachability analysis, then this method does not need to do anything.
		if(valueFunction.containsKey(sih) && this.foundReachableStates){
			return false; //no need for additional reachability testing
		}
		
		DPrint.cl(this.debugCode, "Starting reachability analysis");
		
		//add to the open list
		LinkedList <HashableState> openList = new LinkedList<HashableState>();
		Set <HashableState> openedSet = new HashSet<HashableState>();
		openList.offer(sih);
		openedSet.add(sih);
		
		
		while(!openList.isEmpty()){
			HashableState sh = openList.poll();
			
			//skip this if it's already been expanded
			if(valueFunction.containsKey(sh)){
				continue;
			}
			
			//do not need to expand from terminal states
			if(model.terminal(sh.s())){
				continue;
			}

			valueFunction.put(sh, valueInitializer.value(sh.s()));


			List<Action> actions = this.applicableActions(sh.s());
			for(Action a : actions){
				List<TransitionProb> tps = ((FullModel)model).transitions(sh.s(), a);
				for(TransitionProb tp : tps){
					HashableState tsh = this.stateHash(tp.eo.op);
					if(!openedSet.contains(tsh) && !valueFunction.containsKey(tsh)){
						openedSet.add(tsh);
						openList.offer(tsh);
					}
				}
			}
			
			
		}
		
		DPrint.cl(this.debugCode, "Finished reachability analysis; # states: " + valueFunction.size());
		
		this.foundReachableStates = true;
		
		return true;
		
	}

	
}
