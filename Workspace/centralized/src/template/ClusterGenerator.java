package template;

import java.util.ArrayList;
import java.util.List;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;

import template.TasksCluster;

public class ClusterGenerator {
	
	
	/************** Initial Solution generation (DBSCAN based) **************/
	private boolean isTaskAssigned(ArrayList<TasksCluster> clusters, Task t) {
    	for(TasksCluster c : clusters) 
    		if(c.hasTask(t)) 
    			return true;
    	return false;
    }
    private void assignT1ToSameClusterAsT2(Task t1, Task t2, ArrayList<TasksCluster> clusters) {
    	for(TasksCluster c : clusters) {
    		if(c.hasTask(t2)) {
    			c.addTask(t1);
    		}
    	}
    }
    protected ArrayList<TasksCluster> clusterTasks(List<Vehicle> vehicles, TaskSet tasks) {
    	int ideal_number_clusters = vehicles.size();
    	double epsilon = 20;
    	if(ideal_number_clusters == 1)
    		// If there is only one vehicle -> produce 1 cluster
    		epsilon = Double.MAX_VALUE;
    	ArrayList<TasksCluster> clusters = new ArrayList<TasksCluster>();
    	do {
    		clusters = new ArrayList<TasksCluster>();
	    	for(Task T : tasks) {
	    		if(isTaskAssigned(clusters,T)) {
	    			continue;
	    		}
	    		for(Task t : tasks) {
	    			if(t.id == T.id)
	    				continue;
	    			else {
	    				double dist = T.pickupCity.distanceTo(t.pickupCity);
	    				if(dist <= epsilon) {
	    					// Both tasks must be assigned to same cluster
	    					if(isTaskAssigned(clusters,t))
	    						assignT1ToSameClusterAsT2(T,t,clusters);
	    					else {
	    						TasksCluster new_cluster = new TasksCluster();
	    						new_cluster.addTask(t);
	    						new_cluster.addTask(T);
		    					clusters.add(new_cluster);
	    					}
	    				}
	    			}
	    			if(isTaskAssigned(clusters,T))
	    				break;
	    		}
	    	}
	    	// Create a new cluster for each outlier ! -> would lose tasks otherwise
	    	for(Task t : tasks) {
	    		if(!isTaskAssigned(clusters,t)) {
	    			TasksCluster new_cluster = new TasksCluster();
					new_cluster.addTask(t);
					clusters.add(new_cluster);
	    		}
	    	}
	    	epsilon += 10;
    	}while(clusters.size() > ideal_number_clusters);
    	
    	return clusters;
    }
    
    
    
    
    /************** Apply clustering to vehicles **************/
    private boolean isVehicleAssigned(Vehicle v, ArrayList<TasksCluster> clusters) {
    	for(int i=0; i<clusters.size(); i++) {
    		if(clusters.get(i).assignedVehicle != null && 
    				clusters.get(i).assignedVehicle.id() == v.id())
    			return true;
    	}
    	return false;
    }
    protected void assignClusters(List<Vehicle> vehicles, ArrayList<TasksCluster> clusters, int num_tasks) {
    	// If there are less clusters than vehicles, refactor the clusters
    	// Only if there are more tasks than vehicles!!
    	
    	// The following only applies if there are more tasks than vehicles
    	while(clusters.size() != vehicles.size()  && vehicles.size() < num_tasks)
    	{
    		// Find the biggest cluster
    		TasksCluster biggest = clusters.get(0);
    		for(TasksCluster c : clusters) {
    			if(biggest.getList().size() < c.getList().size()) 
    				biggest = c;
    		}
    		// Divide the biggest cluster into 2 smaller clusters
    		if(biggest.getList().size() >= 2) {
    			int boundary = 0;
    			if(biggest.getList().size() % 2 == 0)
    				boundary = biggest.getList().size()/2;
    			else
    				boundary = (biggest.getList().size()+1)/2;
    			ArrayList<Task> sub1 = new ArrayList<Task>(biggest.getList().subList(0, boundary));
    			ArrayList<Task> sub2 = new ArrayList<Task>(biggest.getList().subList(boundary, biggest.getList().size()));
    			
    			biggest.resetList();
    			biggest.addAllTasks(sub1);
    			TasksCluster new_cluster = new TasksCluster();
    			new_cluster.addAllTasks(sub2);
    			clusters.add(new_cluster);
    		}
    	}
    	
    	// Assign a vehicle to each cluster
    	for(TasksCluster c : clusters) {
    		double min_dist = Double.MAX_VALUE;
    		for(Vehicle v : vehicles) {
    			// Compute minimal distance
    			double dist = Double.MAX_VALUE;
    			for(int i=0; i<c.getList().size(); i++) {
    				if(v.getCurrentCity().distanceTo(c.getList().get(i).pickupCity) < dist)
    					dist = v.getCurrentCity().distanceTo(c.getList().get(i).pickupCity);
    				if(dist == 0.0)
    					break;
    			}
    			if(dist < min_dist && !isVehicleAssigned(v,clusters)) {
    				c.assignedVehicle = v;
    			}
    		}
    	}
    	return;
    }
}
