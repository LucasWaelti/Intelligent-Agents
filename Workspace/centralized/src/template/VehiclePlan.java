package template;

import java.util.ArrayList;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology.City;

import template.CentralizedMain;

/*
 * This class allows to generate a plan for a vehicle
 * 
 * It contains the subclass SingleAction that populates a vehicle's plan
 */

public class VehiclePlan {
	
	public class SingleAction{
		protected Task task = null;
		protected int action = -1;
		protected double load = -1;
		
		public SingleAction(Task t, int a) {
			this.task = t;
			this.action = a;
		}
		public SingleAction(Task t, int a, double l) {
			this.task = t;
			this.action = a;
			this.load = l;
		}
	}
	
	protected Vehicle vehicle = null;
	
	protected ArrayList<SingleAction> 	plan = new ArrayList<SingleAction>();
	
	public VehiclePlan(Vehicle v) {
		// Constructor
		this.vehicle = v;
		return;
	}
	
	@Override
	public VehiclePlan clone() {
		// Returns a copy of itself
		VehiclePlan clone = new VehiclePlan(this.vehicle);
		
		for(SingleAction a : this.plan) {
			clone.add(new SingleAction(a.task,a.action,a.load));
		}
		return clone;
	}
	
	public void generateLoadTable() {
		// Update the load in the vehicle after each action

		double pred = 0;
		for(SingleAction a : this.plan) {
			if(a == null)
				System.out.println("Null single action!!");
			if(a.action == CentralizedMain.PICKUP) {
				a.load = pred + a.task.weight;
				pred = a.load;
			}
			else if(a.action == CentralizedMain.DELIVER){
				a.load = pred - a.task.weight;
				pred = a.load;
			}
		}
	}
	
	
	public boolean hasOverload() {
		for(SingleAction a : this.plan) {
			if(a.load > this.vehicle.capacity())
				return true;
		}
		return false;
	}
	
	public void add(SingleAction a) {
		this.plan.add(a);
 		this.generateLoadTable(); 
	}
	public void add(int i,SingleAction a) {
		this.plan.add(i,a);
		this.generateLoadTable();
	}
	public void remove(SingleAction a) {
		this.plan.remove(a);
		this.generateLoadTable();
	}
	public void addPairInit(SingleAction ap,SingleAction ad) {
		// Pickup everything first then deliver by adding new pairs in the middle of the schedule
		if(this.plan.size() > 0) {
			int ind = this.plan.size()/2;
    		this.plan.add(ind,ap);
    		this.plan.add(ind+1,ad);
		}
		else {
			this.plan.add(ap);
    		this.plan.add(ad);
		}
	}
	public boolean addPairRandom(SingleAction ap, SingleAction ad) {
		boolean succes = false;
		int indexToPlacePickup = (int) (Math.random()*this.plan.size());
		this.plan.add(indexToPlacePickup, ap);
			
		int indexToPlaceDeliver = (int) (Math.random()*(this.plan.size() - (indexToPlacePickup+1))) + indexToPlacePickup+1;
		this.plan.add(indexToPlaceDeliver, ad);
		succes = hasOverload();
		return !succes;
	}
	public void removePair(SingleAction ap,SingleAction ad) {
		this.remove(ap);
		this.remove(ad);
		this.generateLoadTable();
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
    	if(a.action == CentralizedMain.PICKUP) {
    		plan.appendPickup(a.task);
    	}
    	else if(a.action == CentralizedMain.DELIVER) {
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
    			if(this.plan.get(i).action == CentralizedMain.PICKUP)
    				from = this.plan.get(i).task.pickupCity;
    			else
    				from = this.plan.get(i).task.deliveryCity;
        		if(this.plan.get(i+1).action == CentralizedMain.PICKUP)
    				to = this.plan.get(i+1).task.pickupCity;
    			else
    				to = this.plan.get(i+1).task.deliveryCity;
    			goFromTo(logist_plan,from ,to);
    		}
    	}
    	return logist_plan;
    }
}
