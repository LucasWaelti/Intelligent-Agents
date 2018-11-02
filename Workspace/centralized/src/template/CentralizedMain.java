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


@SuppressWarnings("unused")

public class CentralizedMain implements CentralizedBehavior {
	
	protected static final int PICKUP = 0;
	protected static final int DELIVER = 1;
	protected static final int MOVE = 2 ; 
	
    protected static Topology topology;
    private TaskDistribution distribution;
    private TaskSet taskSet;
    private Agent agent;
    protected static long timeout_setup;
    protected static long timeout_plan;
    
    protected TaskSet getTaskSet() {
    	return this.taskSet;
    }
    
    // Create empty ArrayList of VehiclePlans
    private ArrayList<VehiclePlan> globalPlan = new ArrayList<VehiclePlan>();
    
    /************** Validate a global plan **************/
    private boolean hasBeenPicked(ArrayList<Integer> IDs, int id) {
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

    
    
    private boolean searchNeighbor(ArrayList<VehiclePlan> oldPlan) {
    	double randomChoose = Math.random();
    	if(randomChoose >0.5) {
    		this.changingVehicle();
    	}else {
    		//this.changingOrder();
    	}
    	return false;
    }
    
    private boolean changingVehicle() {
    	boolean succes = false;
    	//Randomly choose to vehicle to exchange tasks
    	
    	int indexVehicleToGet = (int) Math.random()*globalPlan.size();
    	int indexVehicleToSet = (int) Math.random()*globalPlan.size();
    	
    	//Get 2 diferents indices.
    	while(indexVehicleToSet==indexVehicleToGet) {
    		indexVehicleToSet = (int) Math.random()*globalPlan.size();
    	}
    	
    	VehiclePlan vToSet = globalPlan.get(indexVehicleToSet);
    	VehiclePlan vToGet = globalPlan.get(indexVehicleToGet);
    	
    	// Randomly pickup a task in the vehicle to set
    	int indexTaskToGet = (int) Math.random()*vToGet.plan.size();
    	SingleAction actionToPick = vToGet.plan.get(indexTaskToGet);
    	
    	SingleAction ap = null;
    	SingleAction ad = null;
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == CentralizedMain.PICKUP) {
			ap = actionToPick;
			for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==ap.task.id) {
					ad=vToGet.plan.get(i);
					break;
				}
			}
    	}else {
    		ad=actionToPick;
    		for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==ad.task.id) {
					ap=vToGet.plan.get(i);
					break;
				}
			}
      	}
    	
    	succes = globalPlan.get(indexVehicleToSet).addPairRandom(ap, ad);
		globalPlan.get(indexVehicleToGet).removePair(ap, ad);
		
		return succes;
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
				VehiclePlan.SingleAction currentAction = currentVehicle.plan.get(t);
				
				// First action -> always pickup
        		if(t == 0) {
        			cost += vehicleCity.distanceTo(currentAction.task.pickupCity)
        				*currentVehicle.vehicle.costPerKm();
        			vehicleCity=currentAction.task.pickupCity;
        		}
        		else {
        			
        			//Clarify coding
        			VehiclePlan.SingleAction previousAction = currentVehicle.plan.get(t-1);
    				
        			if(currentAction.action== CentralizedMain.PICKUP) {
        				cost += vehicleCity.distanceTo(currentAction.task.pickupCity)*currentVehicle.vehicle.costPerKm();
            			vehicleCity=currentAction.task.pickupCity;
        			}
        			else if (currentAction.action== CentralizedMain.DELIVER) {
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
    	
        } while(System.currentTimeMillis()-time_start < CentralizedMain.timeout_plan - 1000) ;
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
        	globalPlan.get(0).addPairInit(globalPlan.get(0).new SingleAction(t,CentralizedMain.PICKUP),globalPlan.get(0).new SingleAction(t,CentralizedMain.DELIVER));           
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
       
        // Initialize the global plan (each VehiclePlan is created)
        initGlobalPlan(clusters);
        
        validateGlobalPlan(this.globalPlan);
        
        
        // Watch out! Order of the plans matters! And don't forget to include empty plans
        List<Plan> plans = new ArrayList<Plan>();
        VehiclePlan plan_i = null;
        for(int i=0; i<vehicles.size();i++) {
        	// Find the right vehicle's plan
        	for(int j=0; j<this.globalPlan.size();j++) {
        		if(this.globalPlan.get(j).vehicle.id() == i) {
        			plan_i = this.globalPlan.get(j);
        		}
        	}
        	if(plan_i != null)
        		plans.add(plan_i.convertToLogistPlan());
        	else
        		plans.add(Plan.EMPTY);
        	plan_i = null;
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
    
}
