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

// Auxiliary classes
import template.ClusterGenerator;
import template.VehiclePlan;
import template.VehiclePlan.SingleAction;


//@SuppressWarnings("unused")

public class CentralizedMain implements CentralizedBehavior {
	
	private final int PHI = 1000; // Plateau detection constant
	private final double DECAY_RATE = 0.9999;
	private int T0 = 2000;
	
	protected static final int PICKUP = 0;
	protected static final int DELIVER = 1;
	protected static final int MOVE = 2 ; 
	
    protected static Topology topology;
    private TaskDistribution distribution;
    private TaskSet taskSet;
    private Agent agent;
    protected long timeout_setup;
    protected long timeout_plan;
    

    private SingleAction ap = null;
    private SingleAction ad = null;
    private int apIndex = -1;
    private int adIndex = -1; 
    private int vehicleGetBefore = -1;
    private int vehicleSetBefore = -1;
    
    protected TaskSet getTaskSet() {
    	return this.taskSet;
    }
    
    // Create empty ArrayList of VehiclePlans
    private ArrayList<VehiclePlan> globalPlan = new ArrayList<VehiclePlan>();
    
    /************** Validate a global plan **************/
    private boolean hasBeenPicked(ArrayList<Integer> IDs, int id) {
    	// Check if a task was picked up (before trying delivering it)
    	if(IDs.size() == 0){
    		return false;
    	}
    	for(int i=0; i<IDs.size(); i++) {
    		if(IDs.get(i) == id) {
    			return true;
    		}
    	}
    	return false;
    }
    private boolean isGlobalPlanValid(ArrayList<VehiclePlan> plan_global) {
    	int num_tasks = 0;
    	ArrayList<Integer> IDs = new ArrayList<Integer>();
    	for(VehiclePlan plan_vehicle : plan_global) {
    		// Check if the load never exceeds the capacity
    		for(int i=0; i<plan_vehicle.plan.size();i++) {
    			// Verify that a task that needs to be delivered was picked up first
    			if(plan_vehicle.plan.get(i).action == PICKUP) {
    				IDs.add(plan_vehicle.plan.get(i).task.id);
    			}
    			else if(plan_vehicle.plan.get(i).action == DELIVER) {
    				if(!hasBeenPicked(IDs,plan_vehicle.plan.get(i).task.id)) {
    					System.out.println("Error: a delivery occurs before a pickup action.");
    					return false;
    				}
    			}
    			// Check if an overload occurred
    			if(plan_vehicle.plan.get(i).load > plan_vehicle.vehicle.capacity())
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
    	
    	while(!isGlobalPlanValid(plan_global)) {
    		System.out.println("Adjusting a plan!");
	    	// Try and make an invalid plan (capacity overshoot) valid by changing the order of actions
	    	for(VehiclePlan plan_vehicle : plan_global) {
	    		// Check if the load ever exceeds the capacity
	    		for(int i=0; i<plan_vehicle.plan.size();i++) {
	    			if(plan_vehicle.plan.get(i).load > plan_vehicle.vehicle.capacity()) {
	    				//System.out.println("Issue in plan " + plan_vehicle + " overload = " + 
	    						//plan_vehicle.plan.get(i).load + " at " + i);
	    				// Deliver tasks before picking the one causing an issue
	    				
	    				// Get the SingleAction that creates an overload
	    				SingleAction ap = plan_vehicle.plan.get(i);
	    				SingleAction ad = null;
	    				
	    				
	    				// Find its corresponding deliver action
	    				for(int j=0; j<plan_vehicle.plan.size();j++) {
	    					if(plan_vehicle.plan.get(j).task.id == ap.task.id && 
	    							plan_vehicle.plan.get(j).action == DELIVER) {
	    						ad = plan_vehicle.plan.get(j);
	    						plan_vehicle.plan.remove(j);
	    						plan_vehicle.plan.remove(i);
	    						
	    						// Move them both at the end of the plan
	    	    				plan_vehicle.plan.add(ap);
	    	    				plan_vehicle.plan.add(ad);

	    	    				/*for(SingleAction sa : plan_vehicle.plan) {
			    	        		System.out.print(sa.action);
			    	        	}
			    	        	System.out.println(" ");*/
	    	    				break;
	    					}
	    				}
	    				
	    				plan_vehicle.generateLoadTable();
	    				
	    				i--; // The overloaded task was moved, don't skip the next one!
	    			}
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
    
    /************** Initialise the global plan from clusters info **************/
    private void initGlobalPlan(ArrayList<TasksCluster> clusters){
    	int i = 0;
        for(TasksCluster c : clusters) {
        	this.globalPlan.add(new VehiclePlan(c.assignedVehicle));
        	for(Task t : c.getList()) {
        		globalPlan.get(i).addPairInit(globalPlan.get(i).new SingleAction(t,CentralizedMain.PICKUP),globalPlan.get(i).new SingleAction(t,CentralizedMain.DELIVER));
        	}
        	globalPlan.get(i).generateLoadTable();
        	i++;
        }
    }


    private boolean searchNeighbor() {
    	double randomChoose = Math.random();
     	boolean success = false;
     	if(globalPlan.size()==1) {
     		success = this.changingOrder(); 
     	}else {
     		// Make a random change
        	if(randomChoose > 0.5) {
         		success = this.changingVehicle(); 
         	}else {
         		success = this.changingOrder(); 
        	}
     	}
     	
    	if(!success)
    		return false;
    	// Update the load after change of plan
    	for(VehiclePlan plan : this.globalPlan) {
    		plan.generateLoadTable();
    	}
     	return isGlobalPlanValid(this.globalPlan); 
    }
    
    private boolean changingOrder() { 
		
		//Randomly choose a vehicle
    	this.vehicleGetBefore = (int) (Math.random()*globalPlan.size());
    	this.vehicleSetBefore = this.vehicleGetBefore;
    	VehiclePlan vToChange = globalPlan.get(this.vehicleGetBefore);
    	
    	
    	// Randomly pickup a task in the vehicle if the vehicle plan is not empty
       	if(vToChange.plan.size()==0)
    		return false;
       	
    	int indexTaskToGet = (int) (Math.random()*vToChange.plan.size());
    	SingleAction actionToPick = vToChange.plan.get(indexTaskToGet);
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == CentralizedMain.PICKUP) {
			this.ap = actionToPick;
			this.apIndex = indexTaskToGet;
			for(int i=0; i< vToChange.plan.size();i++) {
				if(vToChange.plan.get(i).task.id==ap.task.id 
						&& vToChange.plan.get(i).action !=ap.action ) {
					this.ad=vToChange.plan.get(i);
					this.adIndex=i;
					break;
				}
			}
    	}else {
    		this.ad = actionToPick;
			this.adIndex = indexTaskToGet;
    		for(int i=0; i< vToChange.plan.size();i++) {
				if(vToChange.plan.get(i).task.id==ad.task.id 
						&& vToChange.plan.get(i).action !=ad.action ) {
					this.ap=vToChange.plan.get(i);
					this.apIndex = i;
					break;
				}
			}
      	}
		
		vToChange.removePair(ap, ad);
    	vToChange.addPairRandom(ap, ad);
    	
    	return true;
	}
    
    private boolean changingVehicle() {
    	//Randomly choose two vehicle to exchange tasks
    	
    	this.vehicleGetBefore = (int) (Math.random()*globalPlan.size());
    	this.vehicleSetBefore = (int) (Math.random()*globalPlan.size());
    	
    	//Get 2 different indices.
    	while(this.vehicleSetBefore==this.vehicleGetBefore) {
    		this.vehicleSetBefore = (int) (Math.random()*globalPlan.size());
    	}
    	
    	VehiclePlan vToSet = globalPlan.get(this.vehicleSetBefore);
    	VehiclePlan vToGet = globalPlan.get(this.vehicleGetBefore);
    	
     	// Randomly pickup a task in the vehicle to get, if the vehicle plan is not empty
    	if(vToGet.plan.size()==0)
    		return false;
    	
    	int indexTaskToGet = (int) (Math.random()*vToGet.plan.size());
    	SingleAction actionToPick = vToGet.plan.get(indexTaskToGet);
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == CentralizedMain.PICKUP) {
			this.ap = actionToPick;
			this.apIndex = indexTaskToGet;
			for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==this.ap.task.id
						&& vToGet.plan.get(i).action!=this.ap.action) {
					
					this.ad=vToGet.plan.get(i);
					this.adIndex=i;
					break;
				}
			}
    	}else {
    		this.ad=actionToPick;
    		this.adIndex=indexTaskToGet;
    		for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==this.ad.task.id
						&& vToGet.plan.get(i).action!=this.ad.action) {
					
					this.ap=vToGet.plan.get(i);
					this.apIndex=i;
					break;
				}
			}
      	}
    	
		vToSet.addPairRandom(ap, ad);
    	vToGet.removePair(ap, ad);
		return true;
	}
	/************** Compute the cost of the current plan **************/
    private double computeCost() {
    	double cost = 0.0;
    	for(int v = 0; v< this.globalPlan.size(); v++){
    		
			//For each vehicle plan
    		VehiclePlan currentVehiclePlan = this.globalPlan.get(v);
			
			//Get the starting city of the vehicle for the given plan
			City vehicleCity = currentVehiclePlan.vehicle.getCurrentCity();
			
			for(int t = 0; t< currentVehiclePlan.plan.size(); t++) {
				//For each action of the vehicle plan 
				VehiclePlan.SingleAction actionToReach = currentVehiclePlan.plan.get(t);
				
    			if(actionToReach.action== CentralizedMain.PICKUP) {
    				cost += vehicleCity.distanceTo(actionToReach.task.pickupCity)
    						*currentVehiclePlan.vehicle.costPerKm();
        			vehicleCity=actionToReach.task.pickupCity;
    			}
    			else if (actionToReach.action== CentralizedMain.DELIVER) {
    				cost += vehicleCity.distanceTo(actionToReach.task.deliveryCity)
    						*currentVehiclePlan.vehicle.costPerKm();
        			vehicleCity=actionToReach.task.deliveryCity;
    			}    				
    		}
    	}

    	return cost;
    }
      
    /************** Stochastic Local Search implementation **************/
    private void cancelLastChange() {
    	// Remove the two actions that were moved
    	if(!this.globalPlan.get(this.vehicleSetBefore).plan.remove(this.ap) ||
    			!this.globalPlan.get(this.vehicleSetBefore).plan.remove(this.ad)) {
    		System.out.println("cancelLastChange error");
    	}
    	
    	// Put them back where they originally were taken from
    	if(!this.globalPlan.get(this.vehicleGetBefore).plan.isEmpty()) {
    		if(this.apIndex <= this.globalPlan.get(this.vehicleGetBefore).plan.size()) {
    			this.globalPlan.get(this.vehicleGetBefore).plan.add(this.apIndex,this.ap);
    			this.globalPlan.get(this.vehicleGetBefore).plan.add(this.adIndex,this.ad);
    		}
    	}
    	else {
    		this.globalPlan.get(this.vehicleGetBefore).plan.add(this.ap);
    		this.globalPlan.get(this.vehicleGetBefore).plan.add(this.ad);
    	}
    	// Update load table back to its previous state
    	for(VehiclePlan plan : this.globalPlan) {
    		plan.generateLoadTable();
    	}
    }
    private void slSearch() {
    	// update all vehicle plans through Stochastic Local Search
    	
    	System.out.println("SLS algorithm launched...");
        long time_start = System.currentTimeMillis();
        
        double newCost = 0;
    	double oldCost = this.computeCost(); 
    	
    	// Simulated Annealing parameters
    	double learningRate = this.DECAY_RATE;
    	this.T0 = (int) (this.computeCost()/100)+100;
    	double temperature  = T0; 
    	
    	boolean changeSuccess = false;
    	
    	// Store the best plan found so far as the stochastic search might end up
    	// with a less optimal solution when timing out. 
    	ArrayList<VehiclePlan> bestPlan = null;
    	double bestCost = Double.MAX_VALUE;
    	
    	
    	// Determine if the cost is not changing
    	int count = 0;
    	// Total number of iterations
    	int iter = 0;
    	
        do {
	    	oldCost = this.computeCost();
	    	changeSuccess = this.searchNeighbor();
	    	
	    	if(changeSuccess && !isGlobalPlanValid(this.globalPlan)) {
	    		cancelLastChange();
	    	}
	    	else if(changeSuccess && isGlobalPlanValid(this.globalPlan)){
	    		iter++;
	        	newCost = this.computeCost();

	        	double exponential =  Math.exp((oldCost-newCost)/(temperature));
	        	double rand = Math.random();
	        	if(newCost <= oldCost || rand < exponential) {
	        		// Keep new plan!
	        		if(newCost==oldCost)
	        			count++;
	        		else {
	        			count=0;
	        		}
	        			
	        	}
	        	else {
	        		// Don't keep the new plan, keep the previous one
		        	cancelLastChange();
	        	}
	        	if(newCost < bestCost) {
        			// If the plan has a better cost than the one previously registered
        			bestPlan = cloneGlobalPlan(this.globalPlan);
        			bestCost = newCost;
        		}
	        	temperature *= learningRate;
	        	temperature =  temperature < T0/Math.log(1 + iter) ? 
	        			T0/Math.log(1 + iter) : temperature;
	    	}
	    	if(count>this.PHI) {
        		temperature = T0;
        		iter=0;
    	    	count=0;
        	}
	    	
    	
        }while(System.currentTimeMillis()-time_start < this.timeout_plan - 2000); // -100*this.taskSet.size()
        
        if(bestCost < newCost)
        	this.globalPlan = bestPlan;
        System.out.println("SLS algorithm terminated. Computed cost: " + this.computeCost());
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
        
        CentralizedMain.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    /************** Produce a Plan for each Vehicle **************/
    private List<Plan> produceLogistPlan(List<Vehicle> vehicles){
    	// Based on the globalPlan
    	
    	// Order of the plans matters! And don't forget to include empty plans
        List<Plan> plans = new ArrayList<Plan>();
        VehiclePlan plan_i = null;
        for(int i=0; i<vehicles.size();i++) {
        	// Find the right vehicle's plan
        	for(int j=0; j<this.globalPlan.size();j++) {
        		if(this.globalPlan.get(j).vehicle.id() == vehicles.get(i).id()) {
        			plan_i = this.globalPlan.get(j);
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
    private List<Plan> produceEmptyLogistPlan(List<Vehicle> vehicles){
    	// In case there are no tasks in the topology.
    	List<Plan> plans = new ArrayList<Plan>();
    	for(int i=0; i<vehicles.size();i++) {
    		plans.add(Plan.EMPTY);
    	}
    	return plans;
    }
    
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        this.taskSet = tasks;
        
        if(tasks.size() == 0) {
        	System.out.println("No task available, generate empty plans.");
        	return produceEmptyLogistPlan(vehicles);
        }
        
        // If there are less vehicles than tasks
        if(vehicles.size() < tasks.size()) {
	        // Create clusters of tasks (less or as much as number of vehicles)
	        ClusterGenerator clusterGenerator = new ClusterGenerator();
	        ArrayList<TasksCluster> clusters = clusterGenerator.clusterTasks(vehicles,tasks);
	        // Assign clusters to each vehicle (subdivide clusters if required/possible)
	        clusterGenerator.assignClusters(vehicles,clusters,this.taskSet.size());
	        //clusterGenerator.displayCluster(clusters);
	       
	        // Initialize the global plan (each VehiclePlan is created) and make it feasible
	        initGlobalPlan(clusters);
        }
        else {
        	// Assign all tasks to the first vehicle
        	this.globalPlan.add(new VehiclePlan(vehicles.get(0)));
        	for(Task t : tasks) {
        		globalPlan.get(0).addPairInit(globalPlan.get(0).new SingleAction(t,CentralizedMain.PICKUP),globalPlan.get(0).new SingleAction(t,CentralizedMain.DELIVER));
        	}
        	globalPlan.get(0).generateLoadTable();
        	
        	for(int i=1; i<vehicles.size(); i++) {
            	this.globalPlan.add(new VehiclePlan(vehicles.get(i)));
        	}
        }
        // Rearrange the initial solution to make it valid if necessary
        validateGlobalPlan(this.globalPlan);
        
        // Stochastic Local Search in solution space
        this.slSearch();
        
        // Generate the Logist plans for each vehicle. 
        List<Plan> plans = produceLogistPlan(vehicles);
        
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }
    
}
