package template;

import java.util.List;
//import java.util.Random;
import java.util.ArrayList;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class Reactive implements ReactiveBehavior {
	
	private final int NUMSTATE = 2;
	private final long MAXVALUE = 1000;
	private final int NUMACTION = 2;
	private final int STATE_0 = 0;
	private final int STATE_1 = 1;
	private final int MOVE = 0;
	private final int PICKUP = 1;
	private final double TOLERANCE = 0.001;

	private int numActions;
	private Agent myAgent;
	private Topology topology;
	private TaskDistribution td;
	private double discount;
	private double cumulatedReward = 0;
	
	private ArrayList<ArrayList<Double>> value;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		this.discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.numActions = 0;
		this.myAgent = agent;
		this.topology = topology;
		this.td = td;
		
		// Initialize the controller
		buildValueFunction();
		learnValueFunction(discount);
		
		System.out.println("Value Function (discount="+discount+") = "+value);
		
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		
		Action action = getBestAction(vehicle,availableTask);
		
		if(numActions >= 1) {
			System.out.println("Agent "+myAgent.id()+", vehicle "+vehicle.id()+": cumulated reward is "+this.cumulatedReward+" after "+(numActions+1)+" actions" + " (average reward per action : " + (this.cumulatedReward/(this.numActions+1)) );
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit per action: "+(myAgent.getTotalProfit() / (double)this.numActions)+")");
		}
		numActions++;
		return action;
	}
	
	private int getCostPerKm() {
		List<Vehicle> vehicles = this.myAgent.vehicles();
		if(vehicles.size() > 1)
			System.out.println("Warning in getReward(): more than one vehicle for this agent. Taking first vehicle.");
		else if(vehicles.size() < 1)
		{
			System.out.println("Error in getReward(): Current agent has no vehicle. Returning null.");
			return -1;
		} 
		Vehicle v = vehicles.get(0);
		return v.costPerKm();
	}
	
	private long getReward(City from, City to, int action) {
		// Get reward when just moving or picking a task
		long reward = 0;
		double cost = from.distanceTo(to) * getCostPerKm();
		
		switch(action) {
		case MOVE:
			reward = (long) -cost;
			break;
		case PICKUP:
			reward = (long) (this.td.probability(from, to) * (this.td.reward(from, to) - cost));
			break;
		}
		return reward;
	}
	// Overload: get reward of a given task
	private long getReward(Task task) {
		// Get reward when picking a task and moving to target
		long reward = 0;
		double cost = task.pickupCity.distanceTo(task.deliveryCity) * getCostPerKm();
		reward = task.reward - (long) cost;
		return reward;
	}
	
	
	
	private double getTransitionProbability(City n, int s_prime) {
		double proba =  0.0;
		if (s_prime==STATE_0){
			proba = this.td.probability(n, null); //proba that there is no task in city n
		}
		else if(s_prime == STATE_1) {
			proba = 1-this.td.probability(n, null); //proba that there is a task in city n
		}
		//System.out.println(proba);
		return proba;
	}
	
	private void buildValueFunction() {

		this.value = new ArrayList<ArrayList<Double>>();
		for(City city : this.topology)
		{
			this.value.add(new ArrayList<Double>());
			
			this.value.get(city.id).add((double) (Math.random() * this.MAXVALUE));
			this.value.get(city.id).add((double) (Math.random() * this.MAXVALUE));
		}
		
		return;
	}
	
	private double getValueFunction(City city, int state) {
		return this.value.get(city.id).get(state);
	}
	
	private void learnValueFunction(double discount) {
		
		System.out.println("Learning Value Function of agent "+this.myAgent.id()+"...");
		
		long reward = 0;
		double T = 0;
		double V = 0;
		double tv = 0;
		double Q = 0;
		double Q_max = 0;
		
		int k = 0;
		double error = 0;
		
		do {
			k = 0;
			error = 0;
			for(City c : this.topology) // for each city
			{
				for(int s=0; s<this.NUMSTATE; s++) // for each state
				{
					Q_max = 0;
					for(int a=0; a<this.NUMACTION; a++) // for each action
					{
						if (a == this.MOVE) // if just moving
						{
							for(City n : c.neighbors()) // for moving from c towards each neighbor
							{
								reward = getReward(c, n, a);
								tv = 0;
								for(int s_prime=0; s_prime<this.NUMSTATE; s_prime++) // Get all states for the given neighbor
								{
									T = getTransitionProbability(n,s_prime);
									V = getValueFunction(n,s_prime);
									tv += T*V;
								}
								Q = reward + discount * tv;
								if(Q > Q_max)
									Q_max = Q;
							}
						}
						else if (a == this.PICKUP) // if picking a task from c for each destination
						{
							for(City d : this.topology) // for each possible destination
							{
								if(c.id == d.id)
									continue; // No task from and to the same city
								
								reward = getReward(c,d,a);
								tv = 0;
								for(int s_prime=0; s_prime<this.NUMSTATE; s_prime++) // Get all states for the given destination
								{
									T = getTransitionProbability(d,s_prime);
									V = getValueFunction(d,s_prime);
									tv += T*V;
								}
								Q = reward + discount * tv;
								if(Q > Q_max)
									Q_max = Q;
							}
						}
					}
					k += 1;
					error += this.value.get(c.id).get(s) - Q_max;
					this.value.get(c.id).set(s, Q_max); // assign value function to current state
				}
			}
			error /= k; // Compute average difference between Q and V (over all states)
		}while(Math.abs(error) > this.TOLERANCE);
		
		System.out.println("...Done!");
	}

	
	private Action getBestAction(Vehicle vehicle, Task availableTask) {
		Action action = null;
		double expectedReward = - Double.MAX_VALUE;
		double intermediateReward = 0;
		double immediateReward = 0;
		
		City currentCity = vehicle.getCurrentCity();
		// First consider going to neighbors city
		for(City n : currentCity.neighbors())
		{
			// Calculate expected reward for this neighbor
			intermediateReward = getReward(currentCity, n, this.MOVE); // Immediate reward
			for(int s=0;s<this.NUMSTATE; s++) // Predicted reward
			{
				intermediateReward += this.discount*getTransitionProbability(n,s)*getValueFunction(n,s);
			}
			if(intermediateReward > expectedReward)
			{
				immediateReward = getReward(currentCity, n, this.MOVE);
				expectedReward = intermediateReward;
				action = new Move(n);
			}
		}
		
		// Then check if a task is available. See if it eventually brings more reward. 
		if (availableTask != null && availableTask.weight <= vehicle.capacity()) 
		{
			intermediateReward = getReward(availableTask); // Immediate reward
			for(int s=0;s<this.NUMSTATE; s++) // Predicted reward
			{
				intermediateReward += this.discount*getTransitionProbability(availableTask.deliveryCity,s)*getValueFunction(availableTask.deliveryCity,s);
			}
			if(intermediateReward > expectedReward)
			{
				immediateReward = getReward(availableTask);
				expectedReward = intermediateReward;
				action = new Pickup(availableTask);
			}
			else 
				System.out.println("\n\nDid not pick a task! \n\n");
		} 
		
		this.cumulatedReward += immediateReward;
		
		return action;
	}
}
