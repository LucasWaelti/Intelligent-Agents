package template;

/* import table */
import logist.simulation.Vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

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

//@SuppressWarnings("unused")

public class DeliberativeTemplate implements DeliberativeBehavior {
	
	private final int MOVE 		= 0;
	private final int PICKUP 	= 1;
	private final int DELIVER 	= 2;
	private int numTasks;
	private double meanDistance;

		
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
		double heuristic = 0;
		
		private String ID;
		
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
		public void setParent(State p) {
			this.parent = p;
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
		public ArrayList<Task> getTasksToPickup() {
			return this.tasksToPickup;
		}
		
		public void setTasksCarried(ArrayList<Task> newTasksCarried) {
			this.tasksCarried= newTasksCarried;
		}
		public ArrayList<Task> getTasksCarried() {
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
		
		private boolean isEqual(State p) {
			if(this.location.id == p.location.id) {
				if(this.remaining_capacity==p.remaining_capacity) {
					if(this.tasksCarried.size()==p.tasksCarried.size()) {
						for(int i=0; i<this.tasksCarried.size();i++) {
							if(!p.tasksCarried.contains(this.tasksCarried.get(i))) 
								return false;
						}	
						if(this.tasksToPickup.size()==p.tasksToPickup.size()) {
							for(int i=0; i<this.tasksToPickup.size();i++) {
								if(!p.tasksToPickup.contains(this.tasksToPickup.get(i)))
									return false;
							}
							return true; // Everything was checked at this point
						}
					}
				}
			}
			return false;
		}
		
		private boolean detectCycle() {
			State iterator = this.getParent();
			
			if(iterator == null)
				return false;
			
			do{
				if(iterator.isEqual(this))
					return true;
				iterator = iterator.getParent();
			}while(iterator != null);
			
			return false;
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
					if(child.detectCycle())
						return stateToReturn;
					child.heuristic = heuristic(child);
					child.produceStateID();
					this.addChild(child);
					stateToReturn = this.children.get(this.children.size()-1);
				}
				break;

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
					if(child.detectCycle())
						return stateToReturn;
					child.heuristic = heuristic(child);
					child.produceStateID();
					this.addChild(child);
					stateToReturn = this.children.get(this.children.size()-1);
				}
				break;
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
					if(child.detectCycle())
						return stateToReturn;
					child.heuristic = heuristic(child);
					child.produceStateID();
					this.addChild(child);
					stateToReturn = this.children.get(this.children.size()-1);
				}
				break;
			}
			return stateToReturn;
		}
	
		public void produceStateID() {
			
			String stateID = "";
			
			// Add city location
			stateID += Integer.toString(this.getLocation().id);
			
			// Add task lists to ID
			ArrayList<Integer> listPickup = new ArrayList<Integer>(Collections.nCopies(numTasks, 0));
			ArrayList<Integer> listCarried = new ArrayList<Integer>(Collections.nCopies(numTasks, 0));
			for(Task t : this.tasksToPickup) {
				listPickup.set(t.id, 1);
			}
			for(Task t : this.tasksCarried) {
				listCarried.set(t.id, 1);
			}
			for(int i=0; i<numTasks; i++) {
				stateID += Integer.toString(listPickup.get(i));
			}
			for(int i=0; i<numTasks; i++) {
				stateID += Integer.toString(listCarried.get(i));
			}
			
			// Add remaining capacity
			stateID += Integer.toString(this.remaining_capacity);
			
			this.ID = stateID;
		}
		public String getStateID() {
			return this.ID;
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
		this.meanDistance = this.computeMeanDistance(topology);
		
		// initialize the planner
		int capacity = agent.vehicles().get(0).capacity();
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;
		this.numTasks = tasks.size();

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			//plan = naivePlan(vehicle, tasks);
			plan = planASTAR(vehicle, tasks);
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
		long startTime = System.currentTimeMillis();
		
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
			//System.out.println(queue.size());
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
			
			// returns first three found goal 
			//(works too if statement is removed but takes much longer because whole tree is explored)
			//if(goalStates.size() >=4)
				//break;
			
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
		
		System.out.println("Optimal distance is "+ bestGoal.getDistance());
		
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
		
		long endTime = System.currentTimeMillis();
		System.out.println("...Done! (search took "+ (endTime - startTime) +" ms)");
		return returnPlan;
	}
	
	private double computeMeanDistance(Topology topology) {
		double sumDist = 0;
		int nbrConnections = 0;
		for(City c : topology.cities()) {
			for(City n : c.neighbors()) {
				nbrConnections++;
				sumDist += c.distanceTo(n);
				//System.out.println(c.distanceTo(n));

			}
		}
		return sumDist/nbrConnections;
	}

	private double heuristic(State s) {
		// Estimate of the best path from node n: f(n)
		double f = 0;
		
		// Add cost of node n: g(n)
		f += s.getDistance();
		
		// Add heuristic of node n: h(n)
		double max_distance = 0;
		for(Task t : s.getTasksToPickup()) {
			double dist = s.getLocation().distanceTo(t.pickupCity);
			dist += t.pickupCity.distanceTo(t.deliveryCity);
			if(max_distance < dist)
				max_distance = dist;
		}
		for(Task t : s.getTasksCarried()) {
			double dist = s.getLocation().distanceTo(t.deliveryCity);
			if(max_distance < dist)
				max_distance = dist;
		}
		f += max_distance;
		
		return f;
	}
	
	private void sort(ArrayList<State> list) {
		// Implementing bubble sort
		for(int i=1; i<list.size(); i++) // for each element of the list
		{
			for(int j=i-1; j>=0; j--) // Let the bubble rise!
			{
				if(list.get(j+1).heuristic < list.get(j).heuristic)
				{
					State trans = list.get(j+1);
					list.remove(j+1);
					list.add(j,trans);
				}
			}
		}
	}
	private void merge(ArrayList<State> M, ArrayList<State> m) {
		// Both lists must be sorted! Merging M <- m
		int i = 0; // M iterator
		int j = 0; // m iterator
		while(true)
		{
			if(M.isEmpty()) {
				M.addAll(m);
				break;
			}
			if(m.isEmpty()) {
				break;
			}
			// Found a place to merge
			if(M.get(i).heuristic >= m.get(j).heuristic) {
				M.add(i,m.get(j)); 
				if(j < m.size()-1)
					j++;
				else
					break; // m is completely merged
			}
			// Check next place
			else if(i < M.size()-1)
				i++;
			else
				break;
		}
	}
	
	private void limitSize(ArrayList<State> list, int N) {
		// Implement beam search
		while(list.size() > N)
		{
			list.remove(list.size()-1);
		}
	}

	private Plan planASTAR(Vehicle vehicle, TaskSet tasks) {
		
		System.out.println("Planning with A*...");
		long startTime = System.currentTimeMillis();
		
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
			
			// returns first three found goal 
			//(works too if statement is removed but takes much longer because whole tree is explored)
			if(goalStates.size() >=4)
				break;
			
			// Append new states to the end of the queue to implement BFS
			sort(state.getChildren());
			merge(queue,state.getChildren());
			//queue.addAll(state.getChildren()); 
			//sort(queue);
			limitSize(queue, 500);
		}
		
		// Extract best found solution
		double distance = Double.MAX_VALUE;
		State bestGoal = null;
		for(State s : goalStates)
			if(s.getDistance() < distance) {
				distance = s.getDistance();
				bestGoal = s;
			}
		
		System.out.println("Optimal distance is "+ bestGoal.getDistance());
		
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
		
		long endTime = System.currentTimeMillis();
		System.out.println("...Done! (search took "+ (endTime - startTime) +" ms)");
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