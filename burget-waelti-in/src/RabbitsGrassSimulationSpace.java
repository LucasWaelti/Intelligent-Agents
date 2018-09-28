/**
 * Class that implements the simulation space of the rabbits grass simulation.
 * @Author Lucas Waelti
 */

import uchicago.src.sim.space.Object2DGrid;

public class RabbitsGrassSimulationSpace {
	private Object2DGrid grassSpace;
	private Object2DGrid agentSpace;
	
	public RabbitsGrassSimulationSpace(int xSize, int ySize) {
		// Constructor
		grassSpace = new Object2DGrid(xSize,ySize);
		agentSpace = new Object2DGrid(xSize,ySize);
		
		for(int i=0; i<xSize; i++)
		{
			for(int j=0; j<ySize; j++)
			{
				grassSpace.putObjectAt(i, j, new Integer(0));
			}
		}
	}
	
	// Grass related methods
	public void spreadGrass(int grassEnergy) {
		// Covers 50% of the grass grid with grass
		int n = (int)grassSpace.getSizeX() * grassSpace.getSizeY() / 2;
		int count = 0;
		int limit = 100;
		
		for(int i=0; i<n; i++)
		{
			int x = (int)Math.floor(Math.random()*grassSpace.getSizeX());
			int y = (int)Math.floor(Math.random()*grassSpace.getSizeY());
			if(x > grassSpace.getSizeX() || y > grassSpace.getSizeY())
				System.out.println("Grass out of bound.");
			
			count = 0;
			while(count < limit)
			{
				if(((Integer)grassSpace.getObjectAt(x, y)).intValue() == 0)
				{
					grassSpace.putObjectAt(x,y,new Integer(1));
					break;
				}
				else
				{
					x = (int)Math.floor(Math.random()*grassSpace.getSizeX());
					y = (int)Math.floor(Math.random()*grassSpace.getSizeY());
					count++;
				}
			}
		}
	}
	
	public void growGrass(int quantity, int grassEnergy) {
		// Add "quantity" of grass to the grassSpace
		int count = 0;
		int limit = 10;
		
		for(int i=0; i<quantity; i++)
		{
			int x = (int)Math.floor(Math.random()*grassSpace.getSizeX());
			int y = (int)Math.floor(Math.random()*grassSpace.getSizeY());
			
			count = 0;
			while(count < limit)
			{
				if(((Integer)grassSpace.getObjectAt(x, y)).intValue() == 0)
				{
					grassSpace.putObjectAt(x,y,new Integer(grassEnergy));
					break;
				}
				else
				{
					x = (int)Math.floor(Math.random()*grassSpace.getSizeX());
					y = (int)Math.floor(Math.random()*grassSpace.getSizeY());
					count++;
				}
			}
		}
	}
	
	public int getGrass(int x, int y) {
		int I;
		if(grassSpace.getObjectAt(x, y) != null)
		{
			I = ((Integer)grassSpace.getObjectAt(x,y)).intValue();
		}
		else
		{
			I = 0;
		}
		return I; // I
	}
	
	public int removeGrass(int x, int y) {
		int grass = getGrass(x,y);
		grassSpace.putObjectAt(x, y, new Integer(0));
		return grass;
	}
	
	public int getTotalGrass() {
		int total = 0;
		for(int i = 0; i < grassSpace.getSizeX(); i++){
			for(int j = 0; j < grassSpace.getSizeY(); j++){
				total += getGrass(i,j);
			}
		}
		return total;
	}
	
	// Agent related methods
	public boolean addAgent(RabbitsGrassSimulationAgent agent) {
		//Try to find a random place on the grid for a new agent
		boolean agentPlaced = false;
		int count = 0;
		int countLimit = 10 * agentSpace.getSizeX() * agentSpace.getSizeY();
		
		while( (agentPlaced==false) && (count<countLimit) )
		{
			int x = (int)(Math.random()*(agentSpace.getSizeX()));
			int y = (int)(Math.random()*(agentSpace.getSizeY()));
			if(isCellOccupied(x,y) == false)
			{
				agentSpace.putObjectAt(x,y,agent);
		        agent.setXY(x,y);
		        agent.setRabbitsGrassSimulationSpace(this);
		        agentPlaced = true;
			}
			count++;
		}
		return agentPlaced;
	}
	
	public boolean isCellOccupied(int x, int y) {
		boolean occupied = false;
		if(agentSpace.getObjectAt(x,y) != null) occupied = true;
		return occupied;
	}
	
	public RabbitsGrassSimulationAgent getAgentAt(int x, int y) {
		RabbitsGrassSimulationAgent a = null;
		if(agentSpace.getObjectAt(x, y) != null)
		{
			a = (RabbitsGrassSimulationAgent)agentSpace.getObjectAt(x,y);
		}
		return a;
	}
	
	public void removeAgentAt(int x, int y) {
		agentSpace.putObjectAt(x, y, null); 
	}
	
	public boolean moveAgentAt(int x, int y, int newX, int newY) {
		boolean agentPlaced = false;
		if(!isCellOccupied(newX,newY))
		{
			RabbitsGrassSimulationAgent a = (RabbitsGrassSimulationAgent)agentSpace.getObjectAt(x, y);
			removeAgentAt(x,y);
			a.setXY(newX, newY);
			agentSpace.putObjectAt(newX, newY, a);
			agentPlaced = true;
		}
		return agentPlaced;
	}
	
	public Object2DGrid getCurrentGrassSpace() {
		return grassSpace;
	}
	public Object2DGrid getCurrentAgentSpace(){
	    return agentSpace;
	}
		
}
