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
	
	protected static final int PICKUP = 0;
	protected static final int DELIVER = 1;
	protected static final int MOVE = 2 ; 
	
    protected static Topology topology;
    private TaskDistribution distribution;
    private TaskSet taskSet;
    private Agent agent;
    protected long timeout_setup;
    protected long timeout_plan;
    
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
    
    /************** Initialize the global plan from clusters info **************/
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
     	boolean succes = false; 
     	// Make a random change
    	if(randomChoose > 0.5) {
     		succes = this.changingVehicle(); 
     	}else {
     		succes = this.changingOrder(); 
    	}
    	// Update the load after change of plan
    	for(VehiclePlan plan : this.globalPlan) {
    		plan.generateLoadTable();
    	}
     	return succes; 
    }
    
    private boolean changingOrder() {
		boolean succes = false; 
		
		//Randomly choose a vehicle
    	int indexVehicleChange = (int) (Math.random()*globalPlan.size());
    	VehiclePlan vToChange = globalPlan.get(indexVehicleChange);
    	
    	
    	// Randomly pickup a task in the vehicle if the vehicle plan is not empty
       	if(vToChange.plan.size()==0)
    		return false;
       	
    	int indexTaskToGet = (int) (Math.random()*vToChange.plan.size());
    	SingleAction actionToPick = vToChange.plan.get(indexTaskToGet);
    	
    	SingleAction ap = null;
    	SingleAction ad = null;
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == CentralizedMain.PICKUP) {
			ap = actionToPick;
			for(int i=0; i< vToChange.plan.size();i++) {
				if(vToChange.plan.get(i).task.id==ap.task.id 
						&& vToChange.plan.get(i).action !=ap.action ) {
					ad=vToChange.plan.get(i);
					break;
				}
			}
    	}else {
    		ad=actionToPick;
    		for(int i=0; i< vToChange.plan.size();i++) {
				if(vToChange.plan.get(i).task.id==ad.task.id 
						&& vToChange.plan.get(i).action !=ad.action ) {
					ap=vToChange.plan.get(i);
					break;
				}
			}
      	}
		
		vToChange.removePair(ap, ad);
    	succes = vToChange.addPairRandom(ap, ad);
    	
    	return succes;
	}
    
    private boolean changingVehicle() {
    	boolean succes = false;
    	//Randomly choose two vehicle to exchange tasks
    	
    	int indexVehicleToGet = (int) (Math.random()*globalPlan.size());
    	int indexVehicleToSet = (int) (Math.random()*globalPlan.size());
    	
    	//Get 2 different indices.
    	while(indexVehicleToSet==indexVehicleToGet) {
    		indexVehicleToSet = (int) (Math.random()*globalPlan.size());
    	}
    	
    	VehiclePlan vToSet = globalPlan.get(indexVehicleToSet);
    	VehiclePlan vToGet = globalPlan.get(indexVehicleToGet);
    	
     	// Randomly pickup a task in the vehicle to get, if the vehicle plan is not empty
    	if(vToGet.plan.size()==0)
    		return false;
    	
    	int indexTaskToGet = (int) (Math.random()*vToGet.plan.size());
    	SingleAction actionToPick = vToGet.plan.get(indexTaskToGet);
    	
    	SingleAction ap = null;
    	SingleAction ad = null;
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == CentralizedMain.PICKUP) {
			ap = actionToPick;
			for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==ap.task.id
						&& vToGet.plan.get(i).action!=ap.action) {
					
					ad=vToGet.plan.get(i);
					break;
				}
			}
    	}else {
    		ad=actionToPick;
    		for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==ad.task.id
						&& vToGet.plan.get(i).action!=ad.action) {
					
					ap=vToGet.plan.get(i);
					break;
				}
			}
      	}
    	
		succes = vToSet.addPairRandom(ap, ad);
    	vToGet.removePair(ap, ad);
		return succes;
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
    	//System.out.println(cost);
    	return cost;
    }
      
    /************** Stochastic Local Search implementation **************/
    private void slSearch() {
    	// update all vehicle plans through Stochastic Local Search
    	
        long time_start = System.currentTimeMillis();
        
        
        double newCost = 0;
    	double oldCost = this.computeCost(); 
    	
    	// Simulated Annealing parameters
    	double learningRate = 0.99999;
    	double temperature  = oldCost;

    	ArrayList<VehiclePlan> oldPlan = this.cloneGlobalPlan(this.globalPlan);
    	
        do {
        	
	    	//Create a copy of the current plan. Used to compare new and old plan. 
        	oldPlan = this.cloneGlobalPlan(this.globalPlan);// TODO
	    	oldCost = this.computeCost();
	    	
	    	if(!this.searchNeighbor()) {
	    		//System.out.println("NO valid neighbouring solution found");
	    		this.globalPlan = oldPlan;
	    	}
	    	else {
	        	newCost = this.computeCost();
	        	double exponentielle =  Math.exp((oldCost-newCost)/(1+temperature));
	        	if(newCost < oldCost || Math.random() < exponentielle) {
	        		// Keep new plan!
	        		oldCost=newCost;
	        	}
	        	else {
	        		// Don't keep the new plan
	        		globalPlan=oldPlan; //TODO
		        	//System.out.println("keep old");
	        	}

	        	temperature *= learningRate;
	        	//System.out.println(computeCost() + " - " + System.currentTimeMillis());
	        	//System.out.println("old cost" + oldCost +"new cost"+newCost);

	    	}
    	
        }while(System.currentTimeMillis()-time_start < this.timeout_plan-1000) ;
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
    
    private void naivePlanV2(List<Vehicle> vehicles, TaskSet tasks) {
        
        this.globalPlan.add(new VehiclePlan(vehicles.get(0)));
        int v = 0, v_counter =0;
        int changeVehicle = (int) tasks.size()/vehicles.size();
        for (Task t : tasks) {
        	globalPlan.get(v).add(globalPlan.get(v).new SingleAction(t,CentralizedMain.PICKUP));
        	globalPlan.get(v).add(globalPlan.get(v).new SingleAction(t,CentralizedMain.DELIVER)); 
        	v_counter++;
        	if(v_counter > changeVehicle) {
                this.globalPlan.add(new VehiclePlan(vehicles.get(v)));
        		v++;
        		v_counter = 0;
        	}
        	
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
        
        CentralizedMain.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    /************** Produce a Plan for each Vehicle **************/
    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        this.taskSet = tasks;
        
        
        // Create clusters of tasks (less or as much as number of vehicles)
        ClusterGenerator clusterGenerator = new ClusterGenerator();
        ArrayList<TasksCluster> clusters = clusterGenerator.clusterTasks(vehicles,tasks);
        // Assign clusters to each vehicle (subdivide clusters if required)
        clusterGenerator.assignClusters(vehicles,clusters,this.taskSet.size());
        clusterGenerator.displayCluster(clusters);
       
        // Initialize the global plan (each VehiclePlan is created) and make it feasible
        initGlobalPlan(clusters);
        validateGlobalPlan(this.globalPlan);
        
        // Stochastic Local Search in solution space
        this.slSearch();
        
        // Generate the Logist plans for each vehicle. 
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
        	if(plan_i != null)
        		plans.add(plan_i.convertToLogistPlan());
        	else
        		plans.add(Plan.EMPTY);
        	plan_i = null;
        } 
        
        
        /*
        naivePlanV2(vehicles, tasks);
        System.out.println(globalPlan);
        this.slSearch();
        
        
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
    
}
