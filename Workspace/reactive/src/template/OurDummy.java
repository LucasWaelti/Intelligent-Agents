package template;

import java.util.List;
import java.util.Random;

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

public class OurDummy implements ReactiveBehavior {

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private double cumulatedReward = 0;
	private final int MOVE = 0;
	private final int PICKUP = 1;
	private TaskDistribution td;


	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

		if (availableTask == null) {
			City currentCity = vehicle.getCurrentCity();
			City nextCity = this.bestCity(currentCity);
			action = new Move(nextCity);
			this.cumulatedReward += this.getReward(currentCity, nextCity, MOVE);
		} else {
			action = new Pickup(availableTask);
			this.cumulatedReward += this.getReward(availableTask);
		}
		
		if(numActions >= 1) {
			System.out.println("Agent "+myAgent.id()+", vehicle "+vehicle.id()+": cumulated reward is "+this.cumulatedReward+" after "+(numActions+1)+" actions" + " (average reward per action : " + (this.cumulatedReward/(this.numActions+1)) );
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit per action: "+(myAgent.getTotalProfit() / (double)this.numActions)+")");
		}
		numActions++;
		
		return action;
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
	private City bestCity(City currentCity) {
		double maxReward = -Double.MAX_VALUE;
		City bestCity = null;
		double currentReward = 0;
		for(City neighborCity : currentCity.neighbors()) {
			currentReward = this.getReward(currentCity, neighborCity , MOVE);
			if(currentReward>maxReward) {
				bestCity=neighborCity;
				maxReward = currentReward;
			}
		}
		return bestCity;
	}
}
