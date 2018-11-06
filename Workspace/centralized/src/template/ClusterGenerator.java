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
    
    public void displayCluster(ArrayList<TasksCluster> clusterList) {
    	int i = 0;
    	for(TasksCluster cluster : clusterList) {
    		System.out.println("In cluster " + i + "(vehicle "+ (int)(cluster.assignedVehicle.id()+1) +"):");
    		for(int j=0; j<cluster.getList().size(); j++) {
    			System.out.println(cluster.getList().get(j).pickupCity);
    		}
    		i++;
    	}
    }
    
    
    /************** Apply clustering to vehicles **************/
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
    	
    	// For each vehicle, find the closest cluster
    	for(Vehicle v : vehicles) {
    		
    		
    		// Find cluster with minimal distance to the vehicle
    		TasksCluster closest_c = null;
    		double dist = Double.MAX_VALUE;
    		for(TasksCluster c : clusters){
    			if(c.assignedVehicle != null)
    				continue;
    			
    			// Find the closest task in the cluster for the vehicle
				double min_task_dist = Double.MAX_VALUE;
				for(Task t : c.getList()) {
					if(min_task_dist > v.getCurrentCity().distanceTo(t.pickupCity)) {
						min_task_dist = v.getCurrentCity().distanceTo(t.pickupCity);
					}
				}
				
				if(dist > min_task_dist && c.assignedVehicle == null) {
					dist = min_task_dist;
					closest_c = c;
				}
    		}
    		// Assign the vehicle to the closest cluster
    		closest_c.assignedVehicle = v;
    	}
    	return;
    }
}
