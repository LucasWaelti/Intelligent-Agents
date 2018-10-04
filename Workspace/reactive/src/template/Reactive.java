package template;

import java.util.List;
import java.util.Random;
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

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private Topology topology;
	private TaskDistribution td;
	
	private ArrayList<ArrayList<Long>> value;

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
		this.topology = topology;
		this.td = td;
		
		displayTopologyInfo(topology, td);
		buildValueFunction(topology);
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		
		System.out.println("I am an intelligent agent!");

		if (availableTask == null || random.nextDouble() > pPickup) {
			City currentCity = vehicle.getCurrentCity();
			action = new Move(currentCity.randomNeighbor(random));
		} else {
			action = new Pickup(availableTask);
		}
		
		if (numActions >= 1) {
			System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
		}
		numActions++;
		
		return action;
	}
	
	private void displayTopologyInfo(Topology topology, TaskDistribution td) {
		System.out.println("/*************************Info*************************/");
		System.out.println("Topology, " + topology.size() + " cities:");
		System.out.println(topology.cities());
		for(City city : topology) // Use an iterator through all cities
		{
			System.out.println("City: " + (topology.cities()).get(city.id) ); // Get city name 
			System.out.println("City id: " + city.id);						  // Get city id
			System.out.println("City neighbors: ");							  // Show neighbors
			// city.neighbors(); returns a list of neighbor cities
			for(City n : city) // Display neighbors of each city
			{
				System.out.println(n);
			}
		}
		
		System.out.println("\nAgent related info:");
		System.out.println("Agent's vehicles:" + this.myAgent.vehicles() +" "+ this.myAgent.vehicles().size() +" vehicles in total");
		System.out.println("Agent's tasks (accepted or not picked or delivered yet): \n" + this.myAgent.getTasks());
		
		System.out.println("\nTask related info:");
		System.out.println("Probability of presence for each city:");
		for(City city : topology)
		{
			System.out.println("P(task in " + (topology.cities()).get(city.id) + ") = " + (1 - td.probability(city, null)));
		}
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
		case 0:
			reward = (long) -cost;
			break;
		case 1:
			reward = this.td.reward(from, to) - (long) cost;
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
	
	private void buildTransition() {
		
	}
	
	private void buildValueFunction(Topology topology) {
		
		this.value = new ArrayList<ArrayList<Long>>(topology.size()); // x along cities, y along states
		
		for(City city : topology)
		{
			this.value.set(city.id, new ArrayList<Long>(this.NUMSTATE));
			
			this.value.get(city.id).set(0, (long) (Math.random() * this.MAXVALUE));
			this.value.get(city.id).set(1, (long) (Math.random() * this.MAXVALUE));
		}
		return;
	}
	
}

