package template;

import java.util.ArrayList;

import logist.topology.Topology.City;
import logist.task.Task;

import template.VehiclePlan;
import template.VehiclePlan.SingleAction;

public class StochasticLocalSearch {
	
	private static final int PHI = 1000; // Plateau detection constant
	private static final double DECAY_RATE = 0.9999;
	private static int T0 = 2000;
	
	private static SingleAction ap = null;
    private static SingleAction ad = null;
    private static int apIndex = -1;
    private static int adIndex = -1; 
    private static int vehicleGetBefore = -1;
    private static int vehicleSetBefore = -1;
    
    private static ArrayList<VehiclePlan> globalPlan = new ArrayList<VehiclePlan>();
    private static ArrayList<Task> taskSet = new ArrayList<Task>();
    
    public static void setGlobalPlan(ArrayList<VehiclePlan> plan) {
    	StochasticLocalSearch.globalPlan = plan;
    }
    public static void setTaskSet(ArrayList<Task> set) {
    	StochasticLocalSearch.taskSet = set;
    }
    
    private static boolean hasBeenPicked(ArrayList<Integer> IDs, int id) {
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
    public static boolean isGlobalPlanValid() {
    	
    	int num_tasks = 0;
    	ArrayList<Integer> IDs = new ArrayList<Integer>();
    	for(VehiclePlan plan_vehicle : StochasticLocalSearch.globalPlan) {
    		// Check if the load never exceeds the capacity
    		for(int i=0; i<plan_vehicle.plan.size();i++) {
    			// Verify that a task that needs to be delivered was picked up first
    			if(plan_vehicle.plan.get(i).action == VehiclePlan.PICKUP) {
    				IDs.add(plan_vehicle.plan.get(i).task.id);
    			}
    			else if(plan_vehicle.plan.get(i).action == VehiclePlan.DELIVER) {
    				if(!hasBeenPicked(IDs,plan_vehicle.plan.get(i).task.id)) {
    					//System.out.println("Error: a delivery occurs before a pickup action.");
    					return false;
    				}
    			}
    			// Check if an overload occurred
    			if(plan_vehicle.plan.get(i).load > plan_vehicle.vehicle.capacity())
    				return false;
    		}
    		num_tasks += plan_vehicle.plan.size()/2;
    	}
    	if(num_tasks != StochasticLocalSearch.taskSet.size()) 
    		// Check if number of tasks is correct
    		return false;
    	else
    		return true;
    }
    
    public static void displayLoads(ArrayList<VehiclePlan> plan) {
    	StochasticLocalSearch.globalPlan = plan;
    	for(VehiclePlan plan_vehicle : StochasticLocalSearch.globalPlan) {
    		ArrayList<Task> carriedTasks = new ArrayList<Task>();
    		System.out.print(plan_vehicle.vehicle.capacity() + ": ");
    		for(int i=0; i<plan_vehicle.plan.size();i++) {
    			if(plan_vehicle.plan.get(i).action == VehiclePlan.PICKUP) 
    				carriedTasks.add(plan_vehicle.plan.get(i).task);
    			else
    				carriedTasks.remove(plan_vehicle.plan.get(i).task);
    			System.out.print(plan_vehicle.plan.get(i).load + "(");
    			for(int j=0; j<carriedTasks.size();j++) {
    				System.out.print(carriedTasks.get(j).id+",");
    			}
    			System.out.print(") ");
    		}
    		System.out.println();
    	}
    }
    
    /************** Clone a global plan **************/
    public static ArrayList<VehiclePlan> cloneGlobalPlan(ArrayList<VehiclePlan> original){
    	ArrayList<VehiclePlan> newPlan = new ArrayList<VehiclePlan>();
    	for(int i=0; i<original.size();i++) {
    		newPlan.add(original.get(i).clone());
    	}
    	return newPlan;
    }
	
	private static boolean searchNeighbor() {
    	double randomChoose = Math.random();
     	boolean success = false;
     	
 		// Make a random change
    	if(randomChoose > 0.5 && globalPlan.size()>1) {
     		success = StochasticLocalSearch.changingVehicle(); 
     	}else {
     		success = StochasticLocalSearch.changingOrder(); 
    	}
     	
    	if(!success)
    		return false;
    	// Update the load after change of plan
    	for(VehiclePlan plan : StochasticLocalSearch.globalPlan) {
    		plan.generateLoadTable();
    	}
     	return StochasticLocalSearch.isGlobalPlanValid(); 
    }
    
    private static boolean changingOrder() { 
		
		//Randomly choose a vehicle
    	vehicleGetBefore = (int) (Math.random()*globalPlan.size());
    	StochasticLocalSearch.vehicleSetBefore = StochasticLocalSearch.vehicleGetBefore;
    	VehiclePlan vToChange = globalPlan.get(StochasticLocalSearch.vehicleGetBefore);
    	
    	
    	// Randomly pickup a task in the vehicle if the vehicle plan is not empty
       	if(vToChange.plan.size()==0)
    		return false;
       	
    	int indexTaskToGet = (int) (Math.random()*vToChange.plan.size());
    	SingleAction actionToPick = vToChange.plan.get(indexTaskToGet);
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == VehiclePlan.PICKUP) {
			StochasticLocalSearch.ap = actionToPick;
			StochasticLocalSearch.apIndex = indexTaskToGet;
			for(int i=0; i< vToChange.plan.size();i++) {
				if(vToChange.plan.get(i).task.id==ap.task.id 
						&& vToChange.plan.get(i).action !=ap.action ) {
					StochasticLocalSearch.ad=vToChange.plan.get(i);
					StochasticLocalSearch.adIndex=i;
					break;
				}
			}
    	}else {
    		StochasticLocalSearch.ad = actionToPick;
			StochasticLocalSearch.adIndex = indexTaskToGet;
    		for(int i=0; i< vToChange.plan.size();i++) {
				if(vToChange.plan.get(i).task.id==ad.task.id 
						&& vToChange.plan.get(i).action !=ad.action ) {
					StochasticLocalSearch.ap=vToChange.plan.get(i);
					StochasticLocalSearch.apIndex = i;
					break;
				}
			}
      	}
		
		vToChange.removePair(ap, ad);
    	vToChange.addPairRandom(ap, ad);
    	
    	return true;
	}
    
    private static boolean changingVehicle() {
    	//Randomly choose two vehicle to exchange tasks
    	
    	StochasticLocalSearch.vehicleGetBefore = (int) (Math.random()*globalPlan.size());
    	StochasticLocalSearch.vehicleSetBefore = (int) (Math.random()*globalPlan.size());
    	
    	//Get 2 different indices.
    	while(StochasticLocalSearch.vehicleSetBefore==StochasticLocalSearch.vehicleGetBefore) {
    		StochasticLocalSearch.vehicleSetBefore = (int) (Math.random()*globalPlan.size());
    	}
    	
    	VehiclePlan vToSet = globalPlan.get(StochasticLocalSearch.vehicleSetBefore);
    	VehiclePlan vToGet = globalPlan.get(StochasticLocalSearch.vehicleGetBefore);
    	
     	// Randomly pickup a task in the vehicle to get, if the vehicle plan is not empty
    	if(vToGet.plan.size()==0)
    		return false;
    	
    	int indexTaskToGet = (int) (Math.random()*vToGet.plan.size());
    	SingleAction actionToPick = vToGet.plan.get(indexTaskToGet);
    	
    	//Separate case if the SingleAction picked is pickup or delivery.
		if(actionToPick.action == VehiclePlan.PICKUP) {
			StochasticLocalSearch.ap = actionToPick;
			StochasticLocalSearch.apIndex = indexTaskToGet;
			for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==StochasticLocalSearch.ap.task.id
						&& vToGet.plan.get(i).action!=StochasticLocalSearch.ap.action) {
					
					StochasticLocalSearch.ad=vToGet.plan.get(i);
					StochasticLocalSearch.adIndex=i;
					break;
				}
			}
    	}else {
    		StochasticLocalSearch.ad=actionToPick;
    		StochasticLocalSearch.adIndex=indexTaskToGet;
    		for(int i=0; i< vToGet.plan.size();i++) {
				if(vToGet.plan.get(i).task.id==StochasticLocalSearch.ad.task.id
						&& vToGet.plan.get(i).action!=StochasticLocalSearch.ad.action) {
					
					StochasticLocalSearch.ap=vToGet.plan.get(i);
					StochasticLocalSearch.apIndex=i;
					break;
				}
			}
      	}
    	
		vToSet.addPairRandom(ap, ad);
    	vToGet.removePair(ap, ad);
		return true;
	}
	/************** Compute the cost of the current plan **************/
    public static double computeCost() {
    	double cost = 0.0;
    	for(int v = 0; v< StochasticLocalSearch.globalPlan.size(); v++){
    		
			//For each vehicle plan
    		VehiclePlan currentVehiclePlan = StochasticLocalSearch.globalPlan.get(v);
			
			//Get the starting city of the vehicle for the given plan
			City vehicleCity = currentVehiclePlan.vehicle.getCurrentCity();
			
			for(int t = 0; t< currentVehiclePlan.plan.size(); t++) {
				//For each action of the vehicle plan 
				VehiclePlan.SingleAction actionToReach = currentVehiclePlan.plan.get(t);
				
    			if(actionToReach.action== VehiclePlan.PICKUP) {
    				cost += vehicleCity.distanceTo(actionToReach.task.pickupCity)
    						*currentVehiclePlan.vehicle.costPerKm();
        			vehicleCity=actionToReach.task.pickupCity;
    			}
    			else if (actionToReach.action== VehiclePlan.DELIVER) {
    				cost += vehicleCity.distanceTo(actionToReach.task.deliveryCity)
    						*currentVehiclePlan.vehicle.costPerKm();
        			vehicleCity=actionToReach.task.deliveryCity;
    			}    				
    		}
    	}

    	return cost;
    }
      
    /************** Stochastic Local Search implementation **************/
    private static void cancelLastChange() {
    	// Remove the two actions that were moved
    	if(!StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleSetBefore).plan.remove(StochasticLocalSearch.ap) ||
    			!StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleSetBefore).plan.remove(StochasticLocalSearch.ad)) {
    		//System.out.println("cancelLastChange error");
    	}
    	
    	// Put them back where they originally were taken from
    	if(!StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleGetBefore).plan.isEmpty()) {
    		if(StochasticLocalSearch.apIndex <= StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleGetBefore).plan.size()) {
    			StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleGetBefore).plan.add(StochasticLocalSearch.apIndex,StochasticLocalSearch.ap);
    			StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleGetBefore).plan.add(StochasticLocalSearch.adIndex,StochasticLocalSearch.ad);
    		}
    	}
    	else {
    		StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleGetBefore).plan.add(StochasticLocalSearch.ap);
    		StochasticLocalSearch.globalPlan.get(StochasticLocalSearch.vehicleGetBefore).plan.add(StochasticLocalSearch.ad);
    	}
    	// Update load table back to its previous state
    	for(VehiclePlan plan : StochasticLocalSearch.globalPlan) {
    		plan.generateLoadTable();
    	}
    }
    public static ArrayList<VehiclePlan> slSearch(ArrayList<VehiclePlan> plan,long timeout) {
    	// update all vehicle plans through Stochastic Local Search
    	System.out.println("SLS algorithm launched...");
    	
    	long time_start = System.currentTimeMillis();
    	
    	StochasticLocalSearch.globalPlan = plan;
    	
    	// Immediately return if the plan is empty
    	boolean emptyPlan = true;
    	for(VehiclePlan vp : globalPlan) {
    		if(!vp.plan.isEmpty()) {
    			emptyPlan = false;
    			break;
    		}
    	}
    	if(emptyPlan) {
	    	System.out.println("SLS algorithm terminated early: no task.");
			return globalPlan;
    	}
        
        double newCost = 0;
    	double oldCost = StochasticLocalSearch.computeCost(); 
    	
    	// Simulated Annealing parameters
    	double learningRate = StochasticLocalSearch.DECAY_RATE;
    	StochasticLocalSearch.T0 = (int) StochasticLocalSearch.computeCost();//(int) (StochasticLocalSearch.computeCost()/100)+100;
    	double temperature  = T0; 
    	
    	boolean changeSuccess = false;
    	
    	// Store the best plan found so far as the stochastic search might end up
    	// with a less optimal solution when timing out. 
    	ArrayList<VehiclePlan> bestPlan = null;
    	double bestCost = Double.MAX_VALUE;
    	
    	
    	// Determine if the cost is not changing
    	int count = 0;
    	// Total number of iterations
    	//int iter = 0;
    	
        do {
	    	oldCost = StochasticLocalSearch.computeCost();
	    	changeSuccess = StochasticLocalSearch.searchNeighbor();
	    	
	    	if(changeSuccess && !isGlobalPlanValid()) {
	    		cancelLastChange();
	    	}
	    	else if(changeSuccess && isGlobalPlanValid()){
	    		//iter++;
	        	newCost = StochasticLocalSearch.computeCost();

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
        			bestPlan = cloneGlobalPlan(StochasticLocalSearch.globalPlan);
        			bestCost = newCost;
        		}
	        	temperature *= learningRate;
	        	//temperature =  temperature < T0/Math.log(1 + iter) ? 
	        			//T0/Math.log(1 + iter) : temperature;
	    	}
	    	if(count>StochasticLocalSearch.PHI) {
        		temperature = T0;
        		//iter=0;
    	    	count=0;
        	}
	    	
    	
        }while(System.currentTimeMillis()-time_start < timeout); 
        
        if(bestCost < newCost)
        	StochasticLocalSearch.globalPlan = bestPlan;
        System.out.println("SLS algorithm terminated. Computed cost: " + StochasticLocalSearch.computeCost());
        
        return globalPlan;
    }
}
