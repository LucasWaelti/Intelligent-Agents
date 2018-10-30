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
	
    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
    
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
    	double epsilon = 20;
    	ArrayList<TasksCluster> clusters;
    	do {
    		clusters = new ArrayList<TasksCluster>();
	    	for(Task T : tasks) {
	    		if(isTaskAssigned(clusters,T))
	    			continue;
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
    	}while(clusters.size() > ideal_number_clusters);
    	
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
    	
    	protected ArrayList<SingleAction> plan = new ArrayList<SingleAction>();
    	protected ArrayList<Double> 		load = new ArrayList<Double>();
    	
    	public VehiclePlan(Vehicle v) {
    		// Constructor
    		this.vehicle = v;
    		return;
    	}
    	
    	public VehiclePlan clone() {
    		// Clone a Vehicle plan
    		VehiclePlan clone = new VehiclePlan(this.vehicle);
    		
    		for(SingleAction a : this.plan) {
    			clone.add(new SingleAction(a.task,a.action));
    		}
    		clone.load = this.load;
    		return clone;
    	}
    	
    	public void add(SingleAction a) {
    		this.plan.add(a);
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
    }
    
    /************** Setup and Plan **************/
    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_default.xml");
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
    
    private ArrayList<VehiclePlan> cloneGlobalPlan(ArrayList<VehiclePlan> original){
    	ArrayList<VehiclePlan> newPlan = new ArrayList<VehiclePlan>();
    	for(int i=0; i<original.size();i++) {
    		newPlan.add(original.get(i).clone());
    	}
    	return newPlan;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
        // Create clusters of tasks (less or as much as number of vehicles)
        ArrayList<TasksCluster> clusters = clusterTasks(vehicles,tasks);
        // Assign clusters to each vehicle (subdivide clusters if required)
        assignClusters(vehicles,clusters);
        
        // Create empty ArrayList of VehiclePlans
        ArrayList<VehiclePlan> globalPlan = new ArrayList<VehiclePlan>();
        for(TasksCluster c : clusters) {
        	// TODO
        }
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size()) {
            plans.add(Plan.EMPTY);
        }
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }
    
    private void slSearch() {
    	// TODO - update all vehicle plans through Stochastic Local Search
    	
    }
    
    private Plan convertToPlan(ArrayList<VehiclePlan> plans) {
    	Plan plan = null;
    	// TODO
    	return plan;
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
}
