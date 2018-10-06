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
	private final int NUMACTION = 2;
	private final int STATE_0 = 0;
	private final int STATE_1 = 1;
	private final int MOVE = 0;
	private final int PICKUP = 1;
	private final double TOLERANCE = 0.1;

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private Topology topology;
	private TaskDistribution td;
	
	private ArrayList<ArrayList<Double>> value;
	private ArrayList<ArrayList<ArrayList<PolicyAction>>> bestAction;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = NUMACTION;
		this.myAgent = agent;
		this.topology = topology;
		this.td = td;
		
		//displayTopologyInfo(topology, td);
		buildValueFunction(topology);
		learnValueFunction(discount);
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
			reward = (long) (this.td.probability(from, to) * this.td.reward(from, to) - cost);
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
			proba = this.td.probability(n, null);
		}
		else if(s_prime == STATE_1) {
			proba = 1-this.td.probability(n, null);
		}		
		return proba;
	}
	
	private void buildValueFunction(Topology topology) {
		
		/*this.value = new ArrayList<ArrayList<Double>>(topology.size()); // x along cities, y along states
		for(City city : topology)
		{
			this.value.set(city.id, new ArrayList<Double>(this.NUMSTATE));
			
			this.value.get(city.id).set(0, (double) (Math.random() * this.MAXVALUE));
			this.value.get(city.id).set(1, (double) (Math.random() * this.MAXVALUE));
		} OH SHIT, DOES NOT WORK!! DO AS FOLLOWS*/
		this.value = new ArrayList<ArrayList<Double>>();
		for(City city : topology)
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
		
		System.out.println("Learning Value Function...");
		
		List<City> neighbors;
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
						if (a == 0) // if just moving
						{
							neighbors = c.neighbors();
							for(City n : neighbors) // for moving from c towards each neighbor
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
						else if (a == 1) // if picking a task from c for each destination
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

	
	private class PolicyAction{
		private int action;
		private City nextCity;
		
		public PolicyAction(City city, int bestAction) {
			action=bestAction;
			nextCity=city;
		}
		public int getAction() {
			return action;
		}
		public void setAction(int action) {
			this.action = action;
		}
		public City getNextCity() {
			return nextCity;
		}
		public void setNextCity(City nextCity) {
			this.nextCity = nextCity;
		}
		
	} 
	
	
	private PolicyAction getBestAction(City currentCity, int state, City destinationCity) {
		return this.bestAction.get(currentCity.id).get(state).get(destinationCity.id);
	}
	
	
	private void bestAction(double discount) {
		this.bestAction = new ArrayList<ArrayList<ArrayList<PolicyAction>>>();
		for (City city : topology) { // through all cities
			this.bestAction.add(new ArrayList<ArrayList<PolicyAction>>());
			
			for (int state=0; state<NUMSTATE; state++) { // through S1 and S2
				this.bestAction.get(city.id).add(new ArrayList<PolicyAction>()) ;
				
				int bestAction;
				City bestDest;
				double sum = -Double.MAX_VALUE ;
				double maxSum = -Double.MAX_VALUE ;
				
				switch(state) {
				case STATE_0:
					for(City neighborCity: city.neighbors()) { //move to a neighbor
						double T_0 = 0, V_0=0, V_1=0, T_1=0;
						double immediateReward = this.getReward(city, neighborCity, MOVE); 
						T_0 = this.getTransitionProbability(neighborCity, STATE_0);
						V_0 = this.getValueFunction(neighborCity,  STATE_0);
						T_1 = this.getTransitionProbability(neighborCity, STATE_1);
						V_1 = this.getValueFunction(neighborCity,  STATE_1);
						sum =immediateReward+ discount*(T_0*V_0+T_1*V_1);
						if (sum>maxSum) {
							maxSum=sum;
							bestAction=MOVE;
							bestDest=neighborCity;
						}
					}
					this.bestAction.get(city.id).get(state).add(0, new PolicyAction(bestDest, bestAction));

				case STATE_1:
					for(City neighborCity: city.neighbors()) { //move to a neighbor
						double T_0 = 0, V_0=0, V_1=0, T_1=0;
						double immediateReward = this.getReward(city, neighborCity, MOVE); 
						T_0 = this.getTransitionProbability(neighborCity, STATE_0);
						V_0 = this.getValueFunction(neighborCity,  STATE_0);
						T_1 = this.getTransitionProbability(neighborCity, STATE_1);
						V_1 = this.getValueFunction(neighborCity,  STATE_1);
						sum =immediateReward+ discount*(T_0*V_0+T_1*V_1);
						if (sum>maxSum) {
							maxSum=sum;
							bestAction=MOVE;
							bestDest=neighborCity;
						}
					}
					for (City allCity : topology ) { // pickup and move to all city
						if(allCity.id == city.id) {
							continue;
						}
						else {
							double T_0 = 0, V_0=0, V_1=0, T_1=0;
							double immediateReward = this.getReward(city, allCity, PICKUP); 
							T_0 = this.getTransitionProbability(allCity, STATE_0);
							V_0 = this.getValueFunction(allCity,  STATE_0);
							T_1 = this.getTransitionProbability(allCity, STATE_1);
							V_1 = this.getValueFunction(allCity,  STATE_1);
							sum =immediateReward+ discount*(T_0*V_0+T_1*V_1);
							if (sum>maxSum) {
								maxSum=sum;
								bestAction=PICKUP;
								bestDest=null;
							}
							this.bestAction.get(city.id).get(state).add(allCity.id, new PolicyAction(bestDest, bestAction));
						}
					}					
				}
			}	
		} 
	}
}

