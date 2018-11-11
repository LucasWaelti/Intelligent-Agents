package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import template.VehiclePlan;
import template.VehiclePlan.SingleAction;
import template.StochasticLocalSearch;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
public class AuctionMain24 implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private City currentCity;
	
	private final int INDIRECT_DEPTH = 1;
	
	private class Path{
		// A class containing all the towns a vehicle will travel through. 
		private ArrayList<City>   path = new ArrayList<City>();
		private ArrayList<Double> load = new ArrayList<Double>();
		private Vehicle vehicle = null;
		
		public Path(){}
		public Path(VehiclePlan vehiclePlan, Vehicle vehicle) {
			this.convertVehiclePlan(vehiclePlan, vehicle);
		}
		
		public void convertVehiclePlan(VehiclePlan vehiclePlan, Vehicle vehicle) {
			// Reset the stored path
			this.path = new ArrayList<City>();
			this.load = new ArrayList<Double>();
			this.vehicle = vehicle;
			
			this.path.add(vehicle.getCurrentCity());
			
			// Build the path
			List<City> itinary = null;
			for(SingleAction a : vehiclePlan.plan) {
				if(a.action == VehiclePlan.PICKUP) 
					itinary = path.get(path.size()-1).pathTo(a.task.pickupCity);
				else if(a.action == VehiclePlan.DELIVER) 
					itinary = path.get(path.size()-1).pathTo(a.task.deliveryCity);
				
				if(itinary.isEmpty()) {
					// The vehicle does not move for this SingleAction
					this.load.set(load.size()-1,a.load);
					continue;
				}
				for(City c : itinary) {
					this.path.add(c);
					this.load.add(a.load);
				}
			}
		}
		
		public double computeDirectPotential(TaskDistribution td) {
			// For new tasks directly along the path. Returns expected reward. 
			double cumulatedReward = 0;
			int rewardCount = 0;
			for(int c1=0; c1<this.path.size(); c1++) {
				for(int c2=c1; c2<this.path.size(); c2++) {
					if(td.weight(path.get(c1), path.get(c2)) < 
							this.vehicle.capacity()-this.load.get(c1)){
						// If the task can be picked up. 
						cumulatedReward += td.reward(path.get(c1), path.get(c2));
						rewardCount++;
					}
				}
			}
			// Take into account empty plans. 
			if(rewardCount == 0)
				return 0;
			else
				return cumulatedReward/rewardCount;
		}
		public double computeIndirectPotential(TaskDistribution td) {
			// For new tasks close to the path
			double cumulatedReward = 0;
			int rewardCount = 1;
			for(int c1=0; c1<this.path.size(); c1++) {
				for(int c2=c1; c2<this.path.size(); c2++) {
					// TODO - not as easy as it seems
				}
			}
			
			return cumulatedReward/rewardCount;
		}
	}

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicle = agent.vehicles().get(0);
		this.currentCity = vehicle.homeCity();

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		// Long[] bids - The bid placed by each agent for the previous task -> bids.length == NumAgents
		if (winner == agent.id()) {
			currentCity = previous.deliveryCity;
		}
		for(int i=0;i<bids.length; i++)
			System.out.println(bids[i]);
		System.out.println();
	}
	
	@Override
	public Long askPrice(Task task) {

		if (vehicle.capacity() < task.weight)
			return null;

		long distanceTask = task.pickupCity.distanceUnitsTo(task.deliveryCity);
		long distanceSum = distanceTask
				+ currentCity.distanceUnitsTo(task.pickupCity);
		double marginalCost = Measures.unitsToKM(distanceSum
				* vehicle.costPerKm());

		double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
		double bid = ratio * marginalCost;

		return (long) Math.round(bid);
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

		Plan planVehicle1 = naivePlan(vehicle, tasks);

		List<Plan> plans = new ArrayList<Plan>();
		plans.add(planVehicle1);
		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);

		return plans;
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
}
