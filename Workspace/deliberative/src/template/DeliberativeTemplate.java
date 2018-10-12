package template;

/* import table */
import logist.simulation.Vehicle;

import java.util.ArrayList;
import java.util.List;

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * An optimal planner for one vehicle.
 */

@SuppressWarnings("unused")

public class DeliberativeTemplate implements DeliberativeBehavior {
	
	private final int MOVE 		= 0;
	private final int PICKUP 	= 1;
	private final int DELIVER 	= 2;
	
	
	
	
	class State {
		// This class represents a node of the state-tree. 
		private State parent;
		private ArrayList<State> children;
		
		// Actual state info
		private City location;
		private List<Task> tasksToPickup;
		private List<Task> tasksCarried;
		private int remaining_capacity;
		
		double distance = 0;
		
		
		public State(State p) {
			// Constructor specifying parent node
			this.parent = p;
			this.children = null;
			
			//Directly add distance from previous node
			this.distance += p.getDistance();
			
			// Initialize the different fields
			this.location = null;
			this.tasksCarried = null;
			this.tasksToPickup = null;
			this.remaining_capacity = 0;
		}
		
		public ArrayList<State> getChildren(){
			return this.children;
		}
		public void addChild(State child) {
			this.children.add(child);
		}
		
		public double getDistance() {
			return this.distance;
		}
		public void addDistance(double dist) {
			this.distance += dist;
		}
		
		public void setLocation(City currentCity) {
			this.location= currentCity;
		}
		public City getLocation() {
			return this.location;
		}
		
		public void setCityTaskToPickup(List<Task> nextCityTasktoPickup) {
			this.tasksToPickup= nextCityTasktoPickup;
		}
		public List<Task> getCityTaskToPickup() {
			return this.tasksToPickup;
		}
		
		public void setTasksCarried(List<Task> newTasksCarried) {
			this.tasksCarried= newTasksCarried;
		}
		public List<Task> getTasksCarried() {
			return this.tasksCarried;
		}
		
		private Task taskToDeliverHere(City cityToCheck) {
			Task taskToLeave = null;
			for(int t = 0; t<this.tasksCarried.size(); t++) {
				if(this.tasksCarried.get(t).deliveryCity.id == cityToCheck.id) {
					taskToLeave =  this.tasksCarried.get(t);
				}
			}
			return taskToLeave;
				
		}
		
		private Task taskToPickup(City cityToCheck) {
			
			Task taskToPickup = null;
			for(int t = 0; t<this.tasksToPickup.size(); t++) {
				if(this.tasksToPickup.get(t).deliveryCity.id == cityToCheck.id) {
					taskToPickup =  this.tasksToPickup.get(t);
				}
			}
			return taskToPickup;
				
		}
		
		// Return the new state if possible, else return null
		public State takeAction(int action, City nextCity) {
			State stateToReturn = null;
			switch(action) {
			case MOVE:
				if (nextCity == null) {
					System.out.println("Error, if choosing to move, you have to specify next city");
				}
				else {
					this.addChild(new State(this));
					this.children.get(this.children.size()-1).setLocation(nextCity);
					this.children.get(this.children.size()-1).setTasksCarried(this.tasksCarried);
					this.children.get(this.children.size()-1).setCityTaskToPickup(this.tasksToPickup);
					stateToReturn = this.children.get(this.children.size()-1);
				}
			case PICKUP:
				Task taskToPickup = this.taskToPickup(this.location);
				
				if(taskToPickup!=null) {
					ArrayList<Task> newTasksCarried = new ArrayList<Task>();
					newTasksCarried.addAll(this.tasksCarried);
					newTasksCarried.add(taskToPickup);
					
					ArrayList<Task> newTasksToPickup = new ArrayList<Task>();
					newTasksToPickup.addAll(this.tasksToPickup);
					newTasksToPickup.remove(taskToPickup);
					
					this.children.get(this.children.size()-1).setLocation(this.location);
					this.children.get(this.children.size()-1).setTasksCarried(newTasksCarried);
					this.children.get(this.children.size()-1).setCityTaskToPickup(newTasksToPickup);
				}
					
				stateToReturn = this.children.get(this.children.size()-1);
			}
			
		}
	}
	
	
	enum Algorithm { BFS, ASTAR }
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	int capacity;

	/* the planning class */
	Algorithm algorithm;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = naivePlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = naivePlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
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

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
		}
	}
}
