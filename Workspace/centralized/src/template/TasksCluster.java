package template;

import java.util.ArrayList;

import logist.simulation.Vehicle;
import logist.task.Task;

public class TasksCluster {
	// Contains a subset of tasks clustered together according to their distance. 
	
	private ArrayList<Task> cluster = new ArrayList<Task>();
	public Vehicle assignedVehicle = null;
	
	public void addTask(Task t) {
		this.cluster.add(t);
	}
	public void addAllTasks(ArrayList<Task> tasks) {
		this.cluster.addAll(tasks);
	}
	public void removeTask(Task t) {
		this.cluster.remove(t);
	}
	public void resetList() {
		this.cluster = new ArrayList<Task>();
	}
	public ArrayList<Task> getList(){
		return this.cluster;
	}
	public boolean hasTask(Task t) {
		for(int i=0; i<this.cluster.size(); i++) {
			if(this.cluster.get(i).id == t.id)
				return true;
		}
		return false;
	}
	public int getTotalWeight() {
		int weight = 0;
		for(int i=0; i<this.cluster.size();i++) {
			weight += this.cluster.get(i).weight;
		}
		return weight;
	}
}
