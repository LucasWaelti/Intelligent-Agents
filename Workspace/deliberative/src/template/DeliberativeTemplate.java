package template;

/* import table */
import logist.simulation.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Action.Delivery;
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
		private ArrayList<Task> tasksToPickup;
		private ArrayList<Task> tasksCarried;
		private int remaining_capacity;
		private boolean finalState;
		private Action actionToState; 
		
		double distance = 0;
		
		
		public State(State p) {
			// Constructor specifying parent node
			this.parent = p;
			this.children = new ArrayList<State>();
						
			// Initialize the different fields
			this.location = null;
			this.tasksCarried = new ArrayList<Task>();
			this.tasksToPickup = new ArrayList<Task>();
			this.remaining_capacity = 0;
			this.distance = 0;
			this.finalState = false;
			this.actionToState = null;
		}
		
		public State getParent() {
			return this.parent;
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
		public void setDistance(double dist) {
			this.distance = dist;
		}
		public void addDistance(double dist) {
			this.distance += dist;
		}
		
		public void setLocation(City currentCity) {
			this.location = currentCity;
		}
		public City getLocation() {
			return this.location;
		}
		
		public void setTasksToPickup(ArrayList<Task> tasksToPickup) {
			this.tasksToPickup= tasksToPickup;
		}
		public List<Task> getTasksToPickup() {
			return this.tasksToPickup;
		}
		
		public void setTasksCarried(ArrayList<Task> newTasksCarried) {
			this.tasksCarried= newTasksCarried;
		}
		public List<Task> getTasksCarried() {
			return this.tasksCarried;
		}
		
		public void setActionToState(Action action) {
			this.actionToState= action;
		}
		public Action getActionToState() {
			return this.actionToState;
		}
		
		public void setRemainingCapacity(int capacity) {
			this.remaining_capacity= capacity;
		}
		public void removeWeight(int weight) {
			this.remaining_capacity+=weight;
		}
		public void addWeight(int weight) {
			this.remaining_capacity-=weight;
		}
		public int getRemainingCapacity() {
			return this.remaining_capacity;
		}
		
		public void setFinalState(boolean finalState) {
			this.finalState = finalState;
		}
		
		//Check if this state is a final state
		private boolean finalState(State stateToCheck) {
			boolean finalState;
			if (stateToCheck.tasksCarried.isEmpty() && stateToCheck.tasksToPickup.isEmpty()) {
				finalState=true;
			}else {
				finalState = false;
			}
			return finalState;
				
		}
		
		// Check if there is a task carried by the agent to be delivered in the city
		private Task taskToDeliverHere(City cityToCheck) {
			Task taskToLeave = null;
			for(int t = 0; t<this.tasksCarried.size(); t++) {
				if(this.tasksCarried.get(t).deliveryCity.id == cityToCheck.id) {
					taskToLeave =  this.tasksCarried.get(t);
				}
			}
			return taskToLeave;
		}
		
		// Check if there is a task to pickup in this city and if the vehicle has enough space left to carry it.
		// It return the task Picked up or null.
		private Task taskToPickup(City cityToCheck) {
			Task taskToPickup = null;
			for(int t = 0; t<this.tasksToPickup.size(); t++) {
				if(this.tasksToPickup.get(t).pickupCity.id == cityToCheck.id && 
						this.tasksToPickup.get(t).weight <= this.remaining_capacity) {
					taskToPickup = this.tasksToPickup.get(t);
				}
			}
			return taskToPickup;
				
		}
		
		// Return the new state if possible, else return null
		public State takeAction(int action, City nextCity) {
			
			State stateToReturn = null;
			
			// Block the creation of children if it is a goal state
			if(this.finalState)
				return stateToReturn;
			
			switch(action) {
			case MOVE:
				if (nextCity == null) {
					System.out.println("Error, if choosing to move, you have to specify next city");
				}
				else {
					State child = new State(this);
					
					child.setLocation(nextCity);
					child.setActionToState(new Move(nextCity));
					child.setTasksCarried(this.tasksCarried);
					child.setTasksToPickup(this.tasksToPickup);
					child.setDistance(this.distance);
					child.addDistance(this.location.distanceTo(nextCity));
					child.setRemainingCapacity(this.getRemainingCapacity());
					child.setFinalState(this.finalState(child));
					this.addChild(child);
					stateToReturn = this.children.get(this.children.size()-1);
					break;
				}
			case PICKUP:
				Task taskToPickup = this.taskToPickup(this.location);
				
				if(taskToPickup!=null) {
					//Create new ArrayList to transfer it to the new children
					//Add the task to pickup to the list of task carried
					ArrayList<Task> newTasksCarried = new ArrayList<Task>();
					newTasksCarried.addAll(this.tasksCarried);
					newTasksCarried.add(taskToPickup);
					
					//Remove task to pickup from the list of the list of the remaining task to pickup
					ArrayList<Task> newTasksToPickup = new ArrayList<Task>();
					newTasksToPickup.addAll(this.tasksToPickup);
					newTasksToPickup.remove(taskToPickup);
					
					State child = new State(this);

					child.setLocation(this.location);
					child.setActionToState(new Pickup(taskToPickup));
					child.setTasksCarried(newTasksCarried);
					child.setTasksToPickup(newTasksToPickup);
					child.setDistance(this.distance);
					child.setRemainingCapacity(this.getRemainingCapacity());
					child.addWeight(taskToPickup.weight);
					child.setFinalState(this.finalState(child));
					this.addChild(child);
					stateToReturn = this.children.get(this.children.size()-1);
					break;
				}
			case DELIVER:
				Task taskToDeliver = this.taskToDeliverHere(this.location);
				
				if(taskToDeliver!=null) {
					//Create new ArrayList to transfer it to the new children
					//Remove the task delivered from the carried list
					ArrayList<Task> newTasksCarried = new ArrayList<Task>();
					newTasksCarried.addAll(this.tasksCarried);
					newTasksCarried.remove(taskToDeliver);
					
					ArrayList<Task> newTasksToPickup = new ArrayList<Task>();
					newTasksToPickup.addAll(this.tasksToPickup);
					
					State child = new State(this);

					child.setLocation(this.location);
					child.setActionToState(new Delivery(taskToDeliver));
					child.setTasksCarried(newTasksCarried);
					child.setTasksToPickup(newTasksToPickup);
					child.setDistance(this.distance);
					child.setRemainingCapacity(this.getRemainingCapacity());
					child.removeWeight(taskToDeliver.weight);
					child.setFinalState(this.finalState(child));
					this.addChild(child);					
					stateToReturn = this.children.get(this.children.size()-1);
					break;
				}
			}
			return stateToReturn;
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
			//plan = naivePlan(vehicle, tasks);
			plan = planBFS(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		return plan;
	}
	

private Plan planBFS(Vehicle vehicle, TaskSet tasks) {
		
		System.out.println("Planning with BFS...");
		
		// Initialize first node of the tree
		State tree = new State(null);
		tree.setLocation(vehicle.getCurrentCity());
		ArrayList<Task> tasksToPickup = new ArrayList<Task>(tasks);
		tree.setTasksToPickup(tasksToPickup); 
		tree.setRemainingCapacity(vehicle.capacity());
		tree.setActionToState(null);
		
		// Implement search tree
		ArrayList<State> queue = new ArrayList<State>();
		ArrayList<State> goalStates = new ArrayList<State>();
		queue.add(tree);
		
		State state = null; // start on first node
		
		while(!queue.isEmpty())
		{
			// Pop the first state from the queue
			state = queue.get(0);
			queue.remove(0);
			
			// Build children of current state
			for(City neighbour : state.getLocation().neighbors())
				state.takeAction(MOVE, neighbour);
			state.takeAction(PICKUP, null);
			state.takeAction(DELIVER, null);
			
			// Store final states if existing
			for(State s : state.getChildren())
				if(s.finalState)
					goalStates.add(s);
			
			// DEBUGGING STATEMENT - returns first found goal
			if(!goalStates.isEmpty())
				break;
			
			// Append new states to the end of the queue to implement BFS
			queue.addAll(state.getChildren()); 
		}
		
		// Extract best found solution
		double distance = Double.MAX_VALUE;
		State bestGoal = null;
		for(State s : goalStates)
			if(s.getDistance() < distance) {
				distance = s.getDistance();
				bestGoal = s;
			}
		
		// Build plan
		ArrayList<Action> plan = new ArrayList<Action>();
		do {
			plan.add(bestGoal.getActionToState());
			bestGoal = bestGoal.getParent();
		}while(bestGoal.getParent() != null);	
		Collections.reverse(plan);
		Plan returnPlan = new Plan(vehicle.getCurrentCity());
		for(int i=0; i<plan.size(); i++)
			returnPlan.append(plan.get(i));
		
		System.out.println("...Done!");
		return returnPlan;
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