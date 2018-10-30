package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedMain implements CentralizedBehavior {
	
	private final int PICKUP = 0;
	private final int DELIVER = 1;
	private final int MOVE = 2 ; 
	
    private Topology topology;
    private TaskDistribution distribution;
    private TaskSet taskSet;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
    // Create empty ArrayList of VehiclePlans
    private ArrayList<VehiclePlan> globalPlan = new ArrayList<VehiclePlan>();
    
    
    /************** Initial Solution generation (DBSCAN based) **************/
    private class TasksCluster{
		// Contains a set of tasks clustered together according to their distance. 
		private ArrayList<Task> cluster = new ArrayList<Task>();
		public Vehicle assignedVehicle = null;
		
		public void addTask(Task t) {
			this.cluster.add(t);
		}
		public void addAllTasks(ArrayList<Task> tasks) {
			this.cluster.addAll(tasks);
		}
		public void removeTask(Task t) {
			this.cluster.remove(t);
		}
		public void resetList() {
			this.cluster = new ArrayList<Task>();
		}
		public ArrayList<Task> getList(){
			return this.cluster;
		}
		public boolean hasTask(Task t) {
			for(int i=0; i<this.cluster.size(); i++) {
				if(this.cluster.get(i).id == t.id)
					return true;
			}
			return false;
		}
		public int getTotalWeight() {
			int weight = 0;
			for(int i=0; i<this.cluster.size();i++) {
				weight += this.cluster.get(i).weight;
			}
			return weight;
		}
	}
    
    private boolean isTaskAssigned(ArrayList<TasksCluster> clusters, Task t) {
    	for(TasksCluster c : clusters) 
    		if(c.hasTask(t)) 
    			return true;
    	return false;
    }
    private void assignT1ToSameClusterAsT2(Task t1, Task t2, ArrayList<TasksCluster> clusters) {
    	for(TasksCluster c : clusters) {
    		if(c.hasTask(t2)) {
    			c.addTask(t1);
    		}
    	}
    }
    private ArrayList<TasksCluster> clusterTasks(List<Vehicle> vehicles, TaskSet tasks) {
    	int ideal_number_clusters = vehicles.size();
    	int nbrTaskAssigned = 0;
    	double epsilon = 20;
    	ArrayList<TasksCluster> clusters = new ArrayList<TasksCluster>();
    	do {
	    	for(Task T : tasks) {
	    		if(isTaskAssigned(clusters,T)) {
	    			nbrTaskAssigned++;
	    			continue;
	    		}
	    		for(Task t : tasks) {
	    			if(t.id == T.id)
	    				continue;
	    			else {
	    				double dist = T.pickupCity.distanceTo(t.pickupCity);
	    				if(dist <= epsilon) {
	    					// Both tasks must be assigned to same cluster
	    					if(isTaskAssigned(clusters,t))
	    						assignT1ToSameClusterAsT2(T,t,clusters);
	    					else {
	    						TasksCluster new_cluster = new TasksCluster();
	    						new_cluster.addTask(t);
	    						new_cluster.addTask(T);
		    					clusters.add(new_cluster);
	    					}
	    				}
	    			}
	    			if(isTaskAssigned(clusters,T))
	    				break;
	    		}
	    	}
	    	epsilon += 10;
    	}while(nbrTaskAssigned < tasks.size());
    		
    		//(clusters.size() < ideal_number_clusters);
    	
    	return clusters;
    }
    
    /************** Apply clustering to vehicles **************/
    private boolean isVehicleAssigned(Vehicle v, ArrayList<TasksCluster> clusters) {
    	for(int i=0; i<clusters.size(); i++) {
    		if(clusters.get(i).assignedVehicle != null && 
    				clusters.get(i).assignedVehicle.id() == v.id())
    			return true;
    	}
    	return false;
    }
    private void assignClusters(List<Vehicle> vehicles, ArrayList<TasksCluster> clusters) {
    	// If there are less clusters than vehicles, refactor the clusters
    	while(clusters.size() != vehicles.size())
    	{
    		// Find the biggest cluster
    		TasksCluster biggest = clusters.get(0);
    		for(TasksCluster c : clusters) {
    			if(biggest.getList().size() < c.getList().size()) 
    				biggest = c;
    		}
    		// Divide the biggest cluster into 2 smaller clusters
    		if(biggest.getList().size() >= 2) {
    			int boundary = 0;
    			if(biggest.getList().size() % 2 == 0)
    				boundary = biggest.getList().size()/2;
    			else
    				boundary = (biggest.getList().size()+1)/2;
    			ArrayList<Task> sub1 = new ArrayList<Task>(biggest.getList().subList(0, boundary));
    			ArrayList<Task> sub2 = new ArrayList<Task>(biggest.getList().subList(boundary, biggest.getList().size()));
    			
    			biggest.resetList();
    			biggest.addAllTasks(sub1);
    			TasksCluster new_cluster = new TasksCluster();
    			new_cluster.addAllTasks(sub2);
    			clusters.add(new_cluster);
    		}
    	}
    	
    	// Assign a vehicle to each cluster
    	for(TasksCluster c : clusters) {
    		double min_dist = Double.MAX_VALUE;
    		Vehicle best_v = null;
    		for(Vehicle v : vehicles) {
    			// Compute minimal distance
    			double dist = Double.MAX_VALUE;
    			for(int i=0; i<c.getList().size(); i++) {
    				if(v.getCurrentCity().distanceTo(c.getList().get(i).pickupCity) < dist)
    					dist = v.getCurrentCity().distanceTo(c.getList().get(i).pickupCity);
    				if(dist == 0.0)
    					break;
    			}
    			if(dist < min_dist && !isVehicleAssigned(v,clusters)) {
    				c.assignedVehicle = v;
    			}
    		}
    	}
    	return;
    }
    
    /************** Scheduling **************/
    private class SingleAction{
    	protected Task task = null;
    	protected int action = -1;
    	
    	public SingleAction(Task t, int a) {
    		this.task = t;
    		this.action = a;
    	}
    }
    
    
    
    private class VehiclePlan{
    	
    	protected Vehicle vehicle = null;
    	
    	protected ArrayList<SingleAction> 	plan = new ArrayList<SingleAction>();
    	protected ArrayList<Double> 		load = new ArrayList<Double>();
    	
    	public VehiclePlan(Vehicle v) {
    		// Constructor
    		this.vehicle = v;
    		return;
    	}
    	
    	public VehiclePlan clone() {
    		// Returns a copy of itself
    		VehiclePlan clone = new VehiclePlan(this.vehicle);
    		
    		for(SingleAction a : this.plan) {
    			clone.add(new SingleAction(a.task,a.action));
    		}
    		for(int i=0; i<this.load.size(); i++) {
    			clone.load.add(this.load.get(i));
    		}
    		return clone;
    	}
    	
    	public void generateLoadTable() {
    		// Populate the ArrayList
    		double pred = 0;
    		for(int i=0; i<this.plan.size();i++) {
    			if(this.plan.get(i).action == PICKUP) {
    				this.load.add(pred + this.plan.get(i).task.weight);
    				pred = pred + this.plan.get(i).task.weight;
    			}
    			else {
    				this.load.add(pred - this.plan.get(i).task.weight);
    				pred = pred - this.plan.get(i).task.weight;
    			}
    		}
    	}
    	public void updateLoadTable() {
    		// Update the values of the ArrayList
    		double pred = 0;
    		for(int i=0; i<this.load.size();i++) {
    			if(this.plan.get(i).action == PICKUP) {
    				this.load.set(i,pred + this.plan.get(i).task.weight);
    				pred = pred + this.plan.get(i).task.weight;
    			}
    			else {
    				this.load.set(i,pred - this.plan.get(i).task.weight);
    				pred = pred - this.plan.get(i).task.weight;
    			}
    		}
    	}
    	
    	public void add(SingleAction a) {
    		this.plan.add(a);
    	}
    	public void add(int i,SingleAction a) {
    		this.plan.add(i,a);
    	}
    	public void remove(SingleAction a) {
    		this.plan.remove(a);
    	}
    	public void addPairInit(SingleAction ap,SingleAction ad) {
    		// Pickup everything first then deliver by adding new pairs in the middle of the schedule
    		if(this.plan.size() > 0) {
	    		this.plan.add(this.plan.size()/2,ap);
	    		this.plan.add(this.plan.size()/2+1,ad);
    		}
    		else {
    			this.plan.add(ap);
	    		this.plan.add(ad);
    		}
    	}
    	public void addPairRandom(SingleAction ap,SingleAction ad) {
    		// TODO
    	}
    	public void removePair(SingleAction ap,SingleAction ad) {
    		// TODO
    	}
    	public void addTask(Task t) {
    		// TODO
    	}
    	public void removeTask(Task t) {
    		// TODO
    	}
    	
    	/************** Generate an actual Logist plan for a VehiclePlan **************/
        private void goFromTo(Plan plan, City from, City to) {
        	for (City city : from.pathTo(to)) {
        		if(city != null)
        			plan.appendMove(city);
            }
        }
        private void appendSingleAction(Plan plan, SingleAction a) {
        	if(a.action == PICKUP) {
        		plan.appendPickup(a.task);
        	}
        	else if(a.action == DELIVER) {
        		plan.appendDelivery(a.task);
        	}
        }
        public Plan convertToLogistPlan() {
        	Plan logist_plan = new Plan(this.vehicle.getCurrentCity());
        	City from = null;
        	City to = null;
        	
        	goFromTo(logist_plan,this.vehicle.getCurrentCity(),this.plan.get(0).task.pickupCity);
        	for(int i=0; i<this.plan.size();i++) {
        		appendSingleAction(logist_plan,this.plan.get(i));
        		if(i<this.plan.size()-1) {
        			if(this.plan.get(i).action == PICKUP)
        				from = this.plan.get(i).task.pickupCity;
        			else
        				from = this.plan.get(i).task.deliveryCity;
	        		if(this.plan.get(i+1).action == PICKUP)
	    				to = this.plan.get(i+1).task.pickupCity;
	    			else
	    				to = this.plan.get(i+1).task.deliveryCity;
        			goFromTo(logist_plan,from ,to);
        		}
        	}
        	return logist_plan;
        }
    }
    
    /************** Setup and Plan **************/
    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds (303 seconds)
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds (303 seconds)
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }
    
    /************** Validate a global plan **************/
    private boolean isGlobalPlanValid(ArrayList<VehiclePlan> plan_global) {
    	int num_tasks = 0;
    	for(VehiclePlan plan_vehicle : plan_global) {
    		// Check if the load never exceeds the capacity
    		for(int i=0; i<plan_vehicle.load.size();i++) {
    			if(plan_vehicle.load.get(i) > plan_vehicle.vehicle.capacity())
    				return false;
    		}
    		num_tasks += plan_vehicle.plan.size()/2;
    	}
    	if(num_tasks != this.taskSet.size()) 
    		// Check if number of tasks is correct
    		return false;
    	else
    		return true;
    }
    private void validateGlobalPlan(ArrayList<VehiclePlan> plan_global) {
    	// Try and make an invalid plan (capacity overshoot) valid by changing the order of actions
    	for(VehiclePlan plan_vehicle : plan_global) {
    		// Check if the load never exceeds the capacity
    		for(int i=0; i<plan_vehicle.load.size();i++) {
    			while(plan_vehicle.load.get(i) > plan_vehicle.vehicle.capacity()) {
    				// Deliver tasks before picking the one causing an issue
    				
    				// Find the heaviest task and command to deliver it first
    				double heaviest = 0;
    				SingleAction act = null;
    				for(int j=i+1;j<plan_vehicle.plan.size();j++) {
    					if(plan_vehicle.plan.get(j).action==DELIVER && plan_vehicle.plan.get(j).task.weight > heaviest) {
    						heaviest = plan_vehicle.plan.get(j).task.weight;
    						act = plan_vehicle.plan.get(j);
    					}
    				}
    				plan_vehicle.remove(act);
    				plan_vehicle.add(i,act);
    				
    				plan_vehicle.updateLoadTable();
    			}
    		}
    	}
    }
    /************** Clone a global plan **************/
    private ArrayList<VehiclePlan> cloneGlobalPlan(ArrayList<VehiclePlan> original){
    	ArrayList<VehiclePlan> newPlan = new ArrayList<VehiclePlan>();
    	for(int i=0; i<original.size();i++) {
    		newPlan.add(original.get(i).clone());
    	}
    	return newPlan;
    }
    /************** Initialize the global plan from clusters info **************/
    private void initGlobalPlan(ArrayList<TasksCluster> clusters){
    	int i = 0;
        for(TasksCluster c : clusters) {
        	this.globalPlan.add(new VehiclePlan(c.assignedVehicle));
        	for(Task t : c.getList()) {
        		globalPlan.get(i).addPairInit(new SingleAction(t,this.PICKUP),new SingleAction(t,this.DELIVER));
        	}
        	globalPlan.get(i).generateLoadTable();
        	i++;
        }
    }

    /************** Produce a Plan for each Vehicle **************/
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        this.taskSet = tasks;
        
        
        // Create clusters of tasks (less or as much as number of vehicles)
        ArrayList<TasksCluster> clusters = clusterTasks(vehicles,tasks);
        // Assign clusters to each vehicle (subdivide clusters if required)
        assignClusters(vehicles,clusters);
       
        // Initialize the global plan (each VehiclePlan is created)
        initGlobalPlan(clusters);

        validateGlobalPlan(this.globalPlan);
        
        List<Plan> plans = new ArrayList<Plan>();
        for(int i=0; i<this.globalPlan.size();i++) {
        	plans.add(this.globalPlan.get(i).convertToLogistPlan());
        }
        
        
        /*
        naivePlanV2(vehicles.get(0), tasks);
        System.out.println(globalPlan);

        computeCost();
        Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size()) {
            plans.add(Plan.EMPTY);
        }
        
        */           
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }
    
    
    private boolean searchNeighbor(ArrayList<VehiclePlan> oldPlan) {
    	return false;
    }
    
    /************** Compute the cost of the current plan **************/
    private double computeCost() {
    	double cost = 0.0;
    	for(int v = 0; v< this.globalPlan.size(); v++){
    		
			//Clarify coding
    		VehiclePlan currentVehicle = this.globalPlan.get(v);
			
			//Give a track of the vehicle position. Will be updated after each action
			City vehicleCity = currentVehicle.vehicle.getCurrentCity();
			
			for(int t = 0; t< currentVehicle.plan.size(); t++) {
				
				//Clarify coding
				SingleAction currentAction = currentVehicle.plan.get(t);
				
				// First action -> always pickup
        		if(t == 0) {
        			cost += vehicleCity.distanceTo(currentAction.task.pickupCity)
        				*currentVehicle.vehicle.costPerKm();
        			vehicleCity=currentAction.task.pickupCity;
        		}
        		else {
        			
        			//Clarify coding
    				SingleAction previousAction = currentVehicle.plan.get(t-1);
    				
        			if(currentAction.action== this.PICKUP) {
        				cost += vehicleCity.distanceTo(currentAction.task.pickupCity)*currentVehicle.vehicle.costPerKm();
            			vehicleCity=currentAction.task.pickupCity;
        			}
        			else if (currentAction.action== this.DELIVER) {
        				cost += vehicleCity.distanceTo(currentAction.task.deliveryCity)*currentVehicle.vehicle.costPerKm();
        						//+ currentAction.task.reward;
            			vehicleCity=currentAction.task.deliveryCity;
        			}    				
        		}
    		}
    	}
    	System.out.println(cost);
    	return cost;
    }
      
    private void slSearch() {
    	// TODO - update all vehicle plans through Stochastic Local Search
    	
        long time_start = System.currentTimeMillis();
        
        do {
        	
	    	//Create a copy of the current plan. Used to compare new and old plan. 
	    	ArrayList<VehiclePlan> oldPlan = this.cloneGlobalPlan(this.globalPlan);
	    	
	    	
	    	int iter = 0;
	    	boolean findSolution = false;
	    	
	    	double newCost = 0;
	    	double oldCost = 0; 
	    	double learningRate=1;
	    	
	    	//Search for solutions close to the current plan. Do while a valid new plan is found. 
	    	do {
	    		findSolution = this.searchNeighbor(oldPlan);
	    		iter++;
	    	} while(!findSolution || iter <1000);
	    	
	    	if(!findSolution) {
	    		System.out.println("NO valid neighboring solution found");
	    		globalPlan = oldPlan;
	    	}
	    	else {
	        	newCost = this.computeCost();
	        	if(Math.random() >= Math.exp((oldCost-newCost)/learningRate)) {        		
	        		// We don't keep the new plan, even if it might be better
	        		globalPlan=oldPlan;
	        	}
	        	oldCost=newCost;
	    	}
    	
        } while(System.currentTimeMillis()-time_start < this.timeout_plan - 1000) ;
    }
    
    

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }
    
    private void naivePlanV2(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        this.globalPlan.add(new VehiclePlan(vehicle));
        for (Task t : tasks) {
        	globalPlan.get(0).addPairInit(new SingleAction(t,this.PICKUP),new SingleAction(t,this.DELIVER));           
        }
    }
}
