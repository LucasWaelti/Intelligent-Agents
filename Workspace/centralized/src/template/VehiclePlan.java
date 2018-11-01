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
		
		public SingleAction(Task t, int a) {
			this.task = t;
			this.action = a;
		}
	}
	
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
		this.load = new ArrayList<Double>();
		for(int i=0; i<this.plan.size();i++) {
			if(this.plan.get(i).action == CentralizedMain.PICKUP) {
				this.load.add(pred + this.plan.get(i).task.weight);
				pred = pred + this.plan.get(i).task.weight;
			}
			else {
				this.load.add(pred - this.plan.get(i).task.weight);
				pred = pred - this.plan.get(i).task.weight;
			}
		}
	}
	
	
	public boolean hasOverload() {
		for(int i=0; i<this.load.size();i++) {
			if(this.load.get(i) > this.vehicle.capacity())
				return true;
		}
		return false;
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
