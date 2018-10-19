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
		
		public void removeChild(State childToRemove) {
			if (childToRemove == null || !this.children.contains(childToRemove)) {
				System.out.println("Error, if calling removeChild a valid child should be given");
			}
			else{
				this.children.remove(childToRemove);
			}
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
	
		public void deprecated_produceStateID() {
			// We can generate an ID creation that does not rely on the number of tasks
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
		private void sortTasksByID(ArrayList<Task> tasks) {
			for(int i=1; i<tasks.size(); i++) // for each task of the list
			{
				for(int j=i-1; j>=0; j--) // Let the bubble rise!
				{
					if(tasks.get(j+1).id < tasks.get(j).id)
					{
						Task trans = tasks.get(j+1);
						tasks.remove(j+1);
						tasks.add(j,trans);
					}
				}
			}
		}
		public void produceStateID() {
			
			String stateID = "";
			
			// Add city location
			stateID += "Loc:";
			stateID += Integer.toString(this.getLocation().id);
			
			// Add task lists to ID
			this.sortTasksByID(this.tasksToPickup);
			this.sortTasksByID(this.tasksCarried);
			
			stateID += "ToPickup:";
			for(int i=0; i<this.tasksToPickup.size(); i++) {
				stateID += Integer.toString(this.tasksToPickup.get(i).id);
				stateID += "-";
			}
			stateID += "ToDeliver:";
			for(int i=0; i<this.tasksCarried.size(); i++) {
				stateID += Integer.toString(this.tasksCarried.get(i).id);
				stateID += "-";
			}
			
			// Add remaining capacity
			stateID += "Cap:";
			stateID += Integer.toString(this.remaining_capacity);
			
			this.ID = stateID;
		}
		public String getStateID() {
			return this.ID;
		}
		
	}
	
	
	enum Algorithm { BFS, ASTAR, RANDOM }
	
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
		
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
	}
	
	private int getHighestTaskIndex(TaskSet tasks) {
		ArrayList<Task> tasksToPickup = new ArrayList<Task>(tasks);
		
		int index = 0;
		for(Task t : tasksToPickup) {
			if(index < t.id+1)
				index = t.id+1;
		}
		return index;
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		
		Plan plan;
		if(vehicle.getCurrentTasks().isEmpty())
			this.numTasks = tasks.size();
		else {
			this.numTasks = tasks.size();
			this.numTasks += vehicle.getCurrentTasks().size();
		}
		if(this.numTasks < getHighestTaskIndex(tasks))
			this.numTasks = getHighestTaskIndex(tasks);
		
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
		case RANDOM:
			plan = naivePlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}	
		
		return plan;
	}
	

	private Plan planBFS(Vehicle vehicle, TaskSet tasks) {
		
		System.out.println("Planning with BFS...");
		long startTime = System.currentTimeMillis();
		
		// Initialize best Hashmap linking a state and the distance to reach it. 
		// Used to check if a state has already been visited
        HashMap<String, State> C = new HashMap<String, State>(); 
        
        // Initialize first node of the tree
		State tree = new State(null);
		tree.setLocation(vehicle.getCurrentCity());
		ArrayList<Task> tasksToPickup = new ArrayList<Task>(tasks);
		tree.setTasksToPickup(tasksToPickup); 
		tree.setRemainingCapacity(vehicle.capacity());
		tree.setActionToState(null);
		tree.produceStateID();

		if (!vehicle.getCurrentTasks().isEmpty()) {
			ArrayList<Task> tasksCarried = new ArrayList<Task>(vehicle.getCurrentTasks());
			tree.setTasksCarried(tasksCarried);
			for(Task t : tasksCarried) {
				tree.addWeight(t.weight);
			}
		}
		
		
		// Implement search tree
		ArrayList<State> queue = new ArrayList<State>();
		ArrayList<State> goalStates = new ArrayList<State>();
		queue.add(tree);
		
		State state = null; // start on first node
		
		State bestFinalState = null; // Store the best final state
		
		
		while(!queue.isEmpty())
		{
			//System.out.println(queue.size());
			// Pop the first state from the queue
			state = queue.get(0);
			queue.remove(0);
			
			if(state.finalState) {
				if (bestFinalState!=null) {
					if(bestFinalState.distance>state.distance)
						bestFinalState=state;
				}				
				else {
					bestFinalState = state;
				}
				goalStates.add(state); //keeping it for debugging purpose. We don't need it since we keep directly the best final state
				continue;
			}
			
			if(!C.containsKey(state.getStateID())) {
				C.put(state.getStateID(), state);
				
				// Build children of current state
				for(City neighbour : state.getLocation().neighbors())
					state.takeAction(MOVE, neighbour);
				state.takeAction(PICKUP, null);
				state.takeAction(DELIVER, null);
				
				// Append new states to the end of the queue to implement BFS
				queue.addAll(state.getChildren()); 
				
			}else {
				if(C.get(state.getStateID()).getDistance()>state.getDistance()) { //We find a better solution for this state
					
					C.get(state.getStateID()).getParent().removeChild(C.get(state.getStateID()));
					//C.get(state.getStateID()).setActionToState(state.actionToState);
					//C.get(state.getStateID()).setParent(state.parent);
					//C.get(state.getStateID()).setDistance(state.distance);
					C.put(state.getStateID(), state);
					
					for(City neighbour : state.getLocation().neighbors())
						state.takeAction(MOVE, neighbour);
					state.takeAction(PICKUP, null);
					state.takeAction(DELIVER, null);
					
					// Merge newly created states to queue accordingly to heuristic
					queue.addAll(state.getChildren()); 
				}
			}
			
			/*
			// returns first three found goal 
			//(works too if statement is removed but takes much longer because whole tree is explored)
			*/
			
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
		int sizeM = M.size();
		int sizem = m.size();
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
				if(j < sizem-1) // consider next child
					j++;
				else
					break; // m is completely merged
			}
			// Check next place
			else if(i < sizeM-1)
				i++;
			else {
				if(!m.isEmpty()) { // Last children must still me added. 
					for(int k=j; k<sizem-1; k++)
						M.add(m.get(k));
				}
				break;
			}
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
		
		// Initialize best Hashmap linking a state and the distance to reach it. 
		// Used to check if a state has already been visited
        HashMap<String, State> C = new HashMap<String, State>(); 
		
		
		// Initialize first node of the tree
		State tree = new State(null);
		tree.setLocation(vehicle.getCurrentCity());
		ArrayList<Task> tasksToPickup = new ArrayList<Task>(tasks);
		tree.setTasksToPickup(tasksToPickup); 
		tree.setRemainingCapacity(vehicle.capacity());
		tree.setActionToState(null);
		tree.produceStateID();
		
		if (!vehicle.getCurrentTasks().isEmpty()) {
			ArrayList<Task> tasksCarried = new ArrayList<Task>(vehicle.getCurrentTasks());
			tree.setTasksCarried(tasksCarried);
			for(Task t : tasksCarried) {
				tree.addWeight(t.weight);
			}
		}
		
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
			
			if(state.finalState) {
				goalStates.add(state); 
				break;
			}
			
			if(!C.containsKey(state.getStateID())) {
				C.put(state.getStateID(), state);
				
				// Build children of current state
				for(City neighbour : state.getLocation().neighbors())
					state.takeAction(MOVE, neighbour);
				state.takeAction(PICKUP, null);
				state.takeAction(DELIVER, null);
				
				// Merge newly created states to queue accordingly to heuristic
				sort(state.getChildren());
				merge(queue,state.getChildren());
				
			}else {
				if(C.get(state.getStateID()).getDistance()>state.getDistance()) { 
					//We find a better solution for this state
					// Remove unwanted children from queue
					for(State s : C.get(state.getStateID()).getChildren()) {
						if(!queue.remove(s))
							System.out.println("Warning: A* tried to remove an unexisting state from the queue.");
						else
							System.out.println("Removed old state from queue.");
					}
					
					// Update the tree
					C.get(state.getStateID()).getParent().removeChild(C.get(state.getStateID()));
					//C.get(state.getStateID()).setActionToState(state.actionToState);
					//C.get(state.getStateID()).setParent(state.parent);
					//C.get(state.getStateID()).setDistance(state.distance);
					C.put(state.getStateID(), state);
					
					// Explore again child nodes
					for(City neighbour : state.getLocation().neighbors())
						state.takeAction(MOVE, neighbour);
					state.takeAction(PICKUP, null);
					state.takeAction(DELIVER, null);
					
					// Merge newly created states to queue accordingly to heuristic
					sort(state.getChildren());
					merge(queue,state.getChildren());
				}
			}
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