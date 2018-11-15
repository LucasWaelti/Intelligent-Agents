package template;

import java.io.File;
//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
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
	private TaskDistribution td;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private City currentCity;
	
	private int SLS_TIMEOUT = 5000;
	private double maximalRewardEstimator = 0;
	
	private int numberOpponents = 1;
	
	// Effective global plan with won tasks
	private ArrayList<VehiclePlan> currentGlobalPlan = new ArrayList<VehiclePlan>();
	private ArrayList<Task> wonTasks = new ArrayList<Task>();
	private double globalCost = 0;
	
	// Hypothetical global plan with the newly auctioned task
	private ArrayList<VehiclePlan> hypoPlan = new ArrayList<VehiclePlan>();
	private ArrayList<Task> hypoTasks = new ArrayList<Task>();
	private double hypoCost = 0;
	// Bids history
	private ArrayList<Long[]> bidsHistory = new ArrayList<Long[]>();
	
	// Define default timeouts (updated by XML)
	protected long timeout_setup = 5000;
	protected long timeout_plan  = 5000;
	protected long timeout_bid   = 5000;
	
	private class Path{
		// A class containing all the towns a vehicle will travel through. 
		private ArrayList<City>   path = new ArrayList<City>();
		private ArrayList<Double> load = new ArrayList<Double>();
		private Vehicle vehicle = null;
		
		public Path(VehiclePlan vehiclePlan, Vehicle vehicle) {
			this.convertVehiclePlan(vehiclePlan, vehicle);
		}
		
		public void convertVehiclePlan(VehiclePlan vehiclePlan, Vehicle vehicle) {
			// Reset the stored path
			this.path = new ArrayList<City>();
			this.load = new ArrayList<Double>();
			this.vehicle = vehicle;
			
			this.path.add(vehicle.getCurrentCity());
			this.load.add(0.0);
			
			// Build the path
			List<City> itinerary = null;
			for(SingleAction a : vehiclePlan.plan) {
				if(a.action == VehiclePlan.PICKUP) 
					itinerary = path.get(path.size()-1).pathTo(a.task.pickupCity);
				else if(a.action == VehiclePlan.DELIVER) 
					itinerary = path.get(path.size()-1).pathTo(a.task.deliveryCity);
				
				if(itinerary.isEmpty()) {
					// The vehicle does not move for this SingleAction
					this.load.set(load.size()-1,a.load);
					continue;
				}
				for(City c : itinerary) {
					this.path.add(c);
					this.load.add(a.load);
				}
			}
		}
		
		private boolean generatesOverload(int start, int end, int weight) {
			for(int c=start; c<=end; c++) {
				if(this.load.get(c) + weight > this.vehicle.capacity())
					return true;
			}
			return false;
		}
		public double computeDirectPotential(TaskDistribution td) {
			// For new tasks directly along the path. Returns expected reward. 
			double cumulatedReward = 0;
			int rewardCount = 0;
			for(int c1=0; c1<this.path.size(); c1++) {
				for(int c2=c1; c2<this.path.size(); c2++) {
					if(td.weight(path.get(c1), path.get(c2)) < 
							this.vehicle.capacity()-this.load.get(c1) &&
							!generatesOverload(c1,c2,td.weight(path.get(c1), path.get(c2)))){
						// If the task can be picked up. 
						cumulatedReward += td.reward(path.get(c1), path.get(c2))*
								td.probability(path.get(c1), path.get(c2));
						rewardCount += td.probability(path.get(c1), path.get(c2));
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
			// Check if the vehicle can slightly deviate from its course
			double cumulatedReward = 0;
			int rewardCount = 0;
			// From each city from the path
			for(int c1=0; c1<this.path.size()-1; c1++) {
				// Get its neighbours
				for(City n : path.get(c1).neighbors()) {
					// See any of these neighbours can join back the next city on the path
					for(City d : n.neighbors()) {
						if(d.id == path.get(c1+1).id) {
							// We found a detour! Compute its potential:
							for(int c2 = c1+1; c2<this.path.size();c2++) {
								if(td.weight(n, path.get(c2)) < 
										this.vehicle.capacity()-this.load.get(c1) && 
										!generatesOverload(c1,c2,td.weight(path.get(c1), path.get(c2)))){
									cumulatedReward += ( td.reward(n, path.get(c2)) -
															(path.get(c1).distanceTo(n) + n.distanceTo(path.get(c2))) * this.vehicle.costPerKm() ) * 
																td.probability(n, path.get(c2));
									rewardCount += td.probability(n, path.get(c2));
								}
							}
						}
					}
				}
			}
			// Take into account empty plans. 
			if(rewardCount == 0)
				return 0;
			else
				return cumulatedReward/rewardCount;
		}
	}

	private void estimateMaximalReward() {
		for(City c1 : this.topology.cities()) 
			for(City c2 : this.topology.cities()) 
				if(this.maximalRewardEstimator < this.td.reward(c1, c2)) 
					this.maximalRewardEstimator = this.td.reward(c1, c2);
	}
	
	private ArrayList<VehiclePlan> buildGlobalPlanFromTasks(ArrayList<Task> tasks) {
		ArrayList<VehiclePlan> plan = new ArrayList<VehiclePlan>();
		for(Vehicle v : this.agent.vehicles()) {
			if(v.id() == this.vehicle.id()) {
				plan.add( new VehiclePlan(this.vehicle) );
				plan.get(plan.size()-1).initPlan(tasks);
			}
			else {
				plan.add( new VehiclePlan(v) );
				plan.get(plan.size()-1).initPlan(null);
			}
		}
		// Compute a plan for the given set of won tasks
		StochasticLocalSearch.setGlobalPlan(plan);
		StochasticLocalSearch.setTaskSet(wonTasks);
		StochasticLocalSearch.slSearch(this.timeout_bid-500);
		
		return plan;
	}
	
	/*
	private ArrayList<VehiclePlan> updateAGlobalPlan(Task task) {
		
		ArrayList<VehiclePlan> plan = new ArrayList<VehiclePlan>();
		if(currentGlobalPlan.isEmpty()) {
			
			for(Vehicle v : this.agent.vehicles()) {
				if( v.id() == this.vehicle.id()) {
					plan.add( new VehiclePlan(v) );
					plan.get(plan.size()-1).addTaskToPlan(task);
				}
				else {
					plan.add( new VehiclePlan(v) );
				}
			}	
		}else {
			plan.get(plan.size()-1).addTaskToPlan(task);
		}
			
		return plan; 
	
	}*/
	
	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
		
		// this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config" + File.separator + "settings_auction.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        // the bid method cannot execute more than timeout_bid milliseconds
        timeout_bid = ls.get(LogistSettings.TimeoutKey.BID);
		
		this.topology = topology;
		this.td = distribution;
		this.agent = agent;
		
		// Get the biggest vehicle
		Vehicle biggestVehicle = null;
		int bestCapa = 0;
		for(Vehicle v : this.agent.vehicles()) {
			if(v.capacity() > bestCapa) {
				bestCapa = v.capacity();
				biggestVehicle = v;
			}
		}
		this.vehicle = biggestVehicle;
		
		this.currentCity = vehicle.homeCity();
		
		estimateMaximalReward();

		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		// Long[] bids - The bid placed by each agent for the previous task -> bids.length == NumAgents
		this.numberOpponents = bids.length-1;
		
		if (winner == agent.id()) {
			// Add the task to the list of won tasks
			this.wonTasks.add(previous);
			this.globalCost = this.hypoCost;
			this.currentGlobalPlan = this.hypoPlan;
		}
		
		//Display the last bids
		bidsHistory.add(bids);
		for(int i=0;i<bids.length; i++)
			System.out.println(bids[i]);
		System.out.println("Number of won tasks by agent 24: "+ wonTasks.size());
	}
	
	@Override
	public Long askPrice(Task task) {
		
		// 1) Check for extreme cases
		if (vehicle.capacity() < task.weight)
			return null;
		if(this.numberOpponents == 0) {
			return (long) 1000000000;
		}
		
		
		// 2) Compute the hypothetical plan with the newly auctioned task
		this.hypoTasks = this.wonTasks;
		this.hypoTasks.add(task);
		this.hypoPlan = buildGlobalPlanFromTasks(wonTasks);
		this.hypoCost = StochasticLocalSearch.computeCost();
		
		// 3) Define floor bid
		double taskCost = this.hypoCost-this.globalCost;
		// TODO - take into account the task's reward??
		
		// 4) Compute task's potential
		ArrayList<Path> paths = new ArrayList<Path>();
		// Convert each VehiclePlan to an explicit itinerary.
		double potential = 0;
		for(VehiclePlan vp : hypoPlan) {
			paths.add(new Path(vp, vp.vehicle));
			potential += paths.get(paths.size()-1).computeDirectPotential(this.td);
			potential += paths.get(paths.size()-1).computeIndirectPotential(this.td);
		}
		potential /= this.agent.vehicles().size()/2; // Averaged expected reward
		potential /= this.maximalRewardEstimator; // Normalised between [0,1]
		// TODO
		
		// 5) Evaluate opponents' behaviour (get in their head!)
		// TODO
		
		// 6) Compute bid
		// TODO
		
		return (long) Math.round(100);
		
		/*long distanceTask = task.pickupCity.distanceUnitsTo(task.deliveryCity);
		long distanceSum = distanceTask
				+ currentCity.distanceUnitsTo(task.pickupCity);
		double marginalCost = Measures.unitsToKM(distanceSum
				* vehicle.costPerKm());

		double ratio = 1.0 + (random.nextDouble() * 0.05 * task.id);
		double bid = ratio * marginalCost;

		return (long) Math.round(bid);*/
	}
	
	
	private List<Plan> produceLogistPlan(List<Vehicle> vehicles){
    	// Based on the globalPlan
    	
    	// Order of the plans matters! And don't forget to include empty plans
        List<Plan> plans = new ArrayList<Plan>();
        VehiclePlan plan_i = null;
        for(int i=0; i<vehicles.size();i++) {
        	// Find the right vehicle's plan
        	for(int j=0; j<this.currentGlobalPlan.size();j++) {
        		if(this.currentGlobalPlan.get(j).vehicle.id() == vehicles.get(i).id()) {
        			plan_i = this.currentGlobalPlan.get(j);
        			break;
        		}
        	}
        	if(plan_i != null && !plan_i.plan.isEmpty())
        		plans.add(plan_i.convertToLogistPlan());
        	else
        		plans.add(Plan.EMPTY);
        	plan_i = null;
        } 
        return plans;
    }

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);

		/*List<Plan> plans = new ArrayList<Plan>();
		while (plans.size() < vehicles.size())
			plans.add(Plan.EMPTY);*/
		
		ArrayList<Task> agentTasks = new ArrayList<Task>();
		for(Task t : tasks) {
			agentTasks.add(t);
		}
		
		this.currentGlobalPlan = buildGlobalPlanFromTasks(agentTasks);

		return produceLogistPlan(this.agent.vehicles());
	}
}
