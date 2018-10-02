import java.awt.Color;

import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;

import uchicago.src.sim.space.Object2DGrid;


/**
 * Class that implements the simulation agent for the rabbits grass simulation.

 * @Author Lucas Waelti Lucas Burget
 */

public class RabbitsGrassSimulationAgent implements Drawable {
	private static int idGenerator = 0;
	private int id;
	private int x;
	private int y;
	private int energy;
	private RabbitsGrassSimulationSpace rgSpace;
	private int nx;
	private int ny;
	private int birthThres = 200;
	
	public RabbitsGrassSimulationAgent(int agentEnergy) {
		// Constructor
		x = -1;
		y = -1;
		energy = agentEnergy;
		idGenerator++;
		id = idGenerator;
	}
	
	public void eatGrass(int grass) {
		energy += grass;
	}
	
	public void setXY(int newX, int newY) {
		x = newX;
		y = newY;
	}
	
	public void setRabbitsGrassSimulationSpace(RabbitsGrassSimulationSpace space) {
		// Provide the space so that the agent can call action in the space
		rgSpace = space;
	}
	public void selectNewDestination() {
		int r = (int)Math.floor(Math.random()*3)-1; // [-1,1]
		if(r>0)
		{
			// Move horizontally
			ny = y;
			r = (int)Math.floor(Math.random()*3)-1;
			if(r>0)
				nx = x+1;
			else
				nx = x-1;
		}
		else
		{
			// Move vertically
			nx = x;
			r = (int)Math.floor(Math.random()*3)-1;
			if(r>0)
				ny = y+1;
			else
				ny = y-1;
		}
		Object2DGrid agentSpace = rgSpace.getCurrentAgentSpace();
		nx = clampCoordinate(nx,agentSpace.getSizeX());
		ny = clampCoordinate(ny,agentSpace.getSizeY());
	}

	public boolean canReproduce(int threshold) {
		birthThres = threshold;
		if(energy >= threshold)
		{
			energy = (int)energy/2;
			return true;
		}
			
		else
			return false;
	}
	public boolean mustDie() {
		if(energy <= 0)
			return true;
		return false;
	}
	
	public int getEnergy() {
		return energy;
	}
	public int getID() {
		return id;
	}
	
	public void report() {
		System.out.println("A-"+getID()+"at coord. ("+getX()+","+getY()+")"+" has the energy level: "+getEnergy());
	}
	
	// Implements Drawable
	public void draw(SimGraphics G) {
		if(energy <= 1)
			G.drawFastRoundRect(Color.gray);
		else if(energy >= birthThres)
			G.drawFastRoundRect(Color.red);
		else
			G.drawFastRoundRect(Color.white);
	}
	public int getX() {
		return x;
	}
	public int getY() {
		return y;
	}

	private int clampCoordinate(int coord, int size) { 
		int correction = 0;
		if(coord < 0)
			correction = size + coord;
		else if(coord >= size)
			correction = coord - size;
		else
			return coord;
		return correction;
	}
	private boolean tryMove(int newX, int newY) {
		return rgSpace.moveAgentAt(x, y, newX, newY);
	}
	public void step() {
		selectNewDestination(); // Updates nx and ny
		
		for(int i=0; i<5 && !tryMove(nx,ny); i++) 
		{
			selectNewDestination();
		}
		
		//System.out.println("agent.step: old("+x+","+y+"), current("+nx+","+ ny+")");
		
		int grass = rgSpace.removeGrass(x, y);
		eatGrass(grass);
		
		if(grass == 0)
			energy--; // Only lose energy if could not feed
	}
}
