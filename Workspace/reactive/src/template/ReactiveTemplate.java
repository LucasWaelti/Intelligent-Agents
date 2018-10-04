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

public class ReactiveTemplate implements ReactiveBehavior {

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;

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
		
		displayTopologyInfo(topology, td);
		buildValueFunction(topology);
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;

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
	
	private void buildValueFunction(Topology topology) {
		for(City city : topology)
		{
			
		}
		return;
	}
}
