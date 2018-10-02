import java.awt.Color;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


import uchicago.src.sim.analysis.DataSource;		//Used for charts
import uchicago.src.sim.analysis.OpenSequenceGraph; //Used for charts
import uchicago.src.sim.analysis.Sequence;			//Used for charts (population plot)

//import uchicago.src.reflector.RangePropertyDescriptor; // Slider
//import uchicago.src.reflector.DescriptorContainer;

import uchicago.src.sim.engine.Schedule;	// Schedule
import uchicago.src.sim.engine.BasicAction;

import uchicago.src.sim.engine.SimModelImpl;// Model
import uchicago.src.sim.engine.SimInit; 	// Runs the simulation

import uchicago.src.sim.gui.DisplaySurface; // GUI Display
import uchicago.src.sim.gui.ColorMap;		// GUI
import uchicago.src.sim.gui.Value2DDisplay; // GUI -> Displays values
import uchicago.src.sim.gui.Object2DDisplay;// GUI -> Displays Objects


/**
 * Class that implements the simulation model for the rabbits grass
 * simulation.  This is the first class which needs to be setup in
 * order to run Repast simulation. It manages the entire RePast
 * environment and the simulation.
 *
 * @Author Lucas Waelti Lucas Burget
 */


public class RabbitsGrassSimulationModel extends SimModelImpl{	
	
	// Default values for changeable parameters
	private static final int GRIDXSIZE  = 20;
	private static final int GRIDYSIZE  = 20;
	private static final int NUMAGENTS  = 10; // Initial number of rabbits 
	private static final int AGENTENERGY = 5;
	private static final int BIRTHTHRES = 10; // Birth threshold (energy level)
	private static final int GRASSGRATE = 20; // Grass grow rate
	private static final int GRASSENERGY = 1; // Energy contained in grass
	private static final double TIME_DELAY = 1;
	
	// Actual parameters
	private int gridXSize  = GRIDXSIZE; 
	private int gridYSize  = GRIDYSIZE; 
	private int numAgents  = NUMAGENTS; // *slider required for all these parameters
	private int birthThres = BIRTHTHRES; 
	private int grassGRate = GRASSGRATE;
	
	// Supplementary parameters
	private boolean fullSpeed = true;
	private int grassEnergy = GRASSENERGY;
	private int agentEnergy = AGENTENERGY;
	// Sliders used to modify the parameters
	//public RangePropertyDescriptor agents_slider;
	//public RangePropertyDescriptor x_slider;
	//public RangePropertyDescriptor y_slider;
	
	private Schedule schedule;
	private DisplaySurface displaySurf;
	
	private ArrayList<RabbitsGrassSimulationAgent> agentList; // The reference list stocking the agents
	private RabbitsGrassSimulationSpace rgSpace;
	
	private OpenSequenceGraph agentsAndGrassInSpace;
	
	/**
	 * Used to create a plot of the grass quantity
	 */
	class grassInSpace implements DataSource,Sequence{
		public Object execute() {
	    	return new Double(getSValue());
	    }
	    public double getSValue() {
	    	if(grassEnergy == 0)
	    		grassEnergy = 1;
	    	return (double)rgSpace.getTotalGrass()/grassEnergy;
	    }
	}
	/**
	 * Used to create a plot of the agent population
	 */
	class agentsInSpace implements DataSource,Sequence{
		public Object execute() {
	    	return new Double(getSValue());
	    }
	    public double getSValue() {
	    	return (double)agentList.size();
	    }
	}
	
	
	public RabbitsGrassSimulationModel() {
		/* Constructor -> used here to implement sliders
		System.out.println("Constructor of class RabbitsGrassSimulationModel called");
		
		RangePropertyDescriptor agents_slider = new RangePropertyDescriptor("numAgents", 10, 100, 10);
		descriptors.put("numAgents", agents_slider);
		
		RangePropertyDescriptor x_slider = new RangePropertyDescriptor("gridXSize", 10, 100, 10);
		descriptors.put("numAgents", x_slider);
		
		RangePropertyDescriptor y_slider = new RangePropertyDescriptor("gridYSize", 10, 100, 10);
		descriptors.put("numAgents", y_slider);*/
	}

	public static void main(String[] args) {
		// Load an instance of the model
		System.out.println("RabbitsGrassSimulationModel started.");
		
		SimInit init = new SimInit();
		RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
		init.loadModel(model, "", false);
	}

	public void begin() {
		// Called when pressing the Curved Arrow (Initialize)
		buildModel();
	    buildSchedule();
	    buildDisplay();
	    
	    displaySurf.display(); // Makes the window appear
	    agentsAndGrassInSpace.display();
	}

	public String[] getInitParam() {
		String[] initParams = {"GridXSize","GridYSize","NumAgents","BirthThres","GrassGRate","FullSpeed","GrassEnergy","AgentEnergy"};
		return initParams;
	}

	public String getName() {
		return "RabbitsGrassSimulationModel";
	}

	public Schedule getSchedule() {
		return schedule;
	}

	public void setup() {
		// tears down model and resets all to null (called when pressing the double-curved arrows (setup))
		
		// Destroy existing agents
		agentList = new ArrayList<RabbitsGrassSimulationAgent>(); 
		
		// Destroy both agent and grass grids
		rgSpace = null;
		
		// Reset the schedule
		schedule = new Schedule(1);
		
		// Reset the display (tears it down)
		if(displaySurf != null)
			displaySurf.dispose();
		displaySurf = null;
		
		// Recreate a new display
		displaySurf = new DisplaySurface(this,"Rabbit Grass Simulation");
		registerDisplaySurface("Rabbit Grass Simulation",displaySurf);
		
		if(agentsAndGrassInSpace != null)
			agentsAndGrassInSpace.dispose();
		agentsAndGrassInSpace = new OpenSequenceGraph("Amount of Agents and Grass",this);
		this.registerMediaProducer("Plot", agentsAndGrassInSpace);
	}
	
	
	private void addNewAgent(int agentEnergy) {
		// Create a new agent and add it to the list and grid
		RabbitsGrassSimulationAgent agent = new RabbitsGrassSimulationAgent(agentEnergy);
		agentList.add(agent);
		rgSpace.addAgent(agent);
	}
	
	
	// Begin subroutines
	public void buildModel() {
		// Create instances of agents, prepare the world
		System.out.println("Building Model");
		
		rgSpace = new RabbitsGrassSimulationSpace(gridXSize,gridYSize);
		rgSpace.spreadGrass(grassEnergy);
		
		// Create a population of agents (not more than the grid can contain)
		if(numAgents > gridXSize*gridYSize)
			numAgents = gridXSize*gridYSize;
		for(int i=0;i<numAgents;i++)
		{
			addNewAgent(agentEnergy);
		}
		System.out.println(agentList.size() + " agents created");
	}
	public void buildSchedule() {
		// Define what action has to be taken at each time step or given interval
		System.out.println("Building Schedule");
		
		class ModelStep extends BasicAction{
			public void execute() {
				// Kill all agents that ran out of energy
				for(int i=(agentList.size()-1); i>=0 ; i--)
				{
					RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent)agentList.get(i);
					if(agent.mustDie())
					{
						rgSpace.removeAgentAt(agent.getX(), agent.getY());
						agentList.remove(i);
					}
				}
				
				// Let agents with sufficient energy reproduce
				for(int i=0; i<agentList.size(); i++)
				{
					RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent)agentList.get(i);
					if(agent.canReproduce(birthThres) && agentList.size() < gridXSize*gridYSize) // Avoid over-populating the grid
						addNewAgent(agentEnergy);
				}
				
				// Step all agents
				for(int i=0; i<agentList.size(); i++)
				{
					RabbitsGrassSimulationAgent agent = (RabbitsGrassSimulationAgent)agentList.get(i);
					agent.step();
					//System.out.println("Energy: "+agent.getEnergy());
				}
				
				// Make grass grow
				rgSpace.growGrass(grassGRate, grassEnergy);
				
				displaySurf.updateDisplay();
				
				// Add delay depending on parameter fullSpeed
				if(fullSpeed == false) {
					try {
						TimeUnit.SECONDS.sleep((long) TIME_DELAY);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println("Could not add delay.");
					}
				}
			}
		}
		schedule.scheduleActionBeginning(0, new ModelStep());
		
		class ActionToExecute extends BasicAction{
			public void execute() {
				agentsAndGrassInSpace.step();
			}
		}
		schedule.scheduleActionBeginning(0, new ActionToExecute());
	}
	public void buildDisplay() {
		// Prepare all windows that have to be displayed (gui part)
		System.out.println("Building Display");
		
		ColorMap map = new ColorMap();
		map.mapColor(grassEnergy, Color.green); // Grass is green (maps the value: 0=black,1=green)
		map.mapColor(0, Color.black); // Back is black
		
		Value2DDisplay displayGrass = new Value2DDisplay(rgSpace.getCurrentGrassSpace(),map);
		displaySurf.addDisplayableProbeable(displayGrass, "Grass");
		
		Object2DDisplay displayAgents = new Object2DDisplay(rgSpace.getCurrentAgentSpace());
		displayAgents.setObjectList(agentList);
		displaySurf.addDisplayableProbeable(displayAgents, "Rabbits");
		// Note: the objects need to implement Drawable to be displayed
		
		agentsAndGrassInSpace.addSequence("Grass", new grassInSpace());
		agentsAndGrassInSpace.addSequence("Rabbits", new agentsInSpace());
	}
	
	
	
	
	
	
	
	
	// Changeable parameters
	public int getGridXSize() {
		return gridXSize;
	}
	public void setGridXSize(int s) {
		gridXSize = s;
	}
	
	public int getGridYSize() {
		return gridYSize;
	}
	public void setGridYSize(int s) {
		gridYSize = s;
	}
	
	public int getNumAgents() {
		return numAgents;
	}
	public void setNumAgents(int n) {
		numAgents = n;
	}
	
	public int getAgentEnergy() {
		return agentEnergy;
	}
	public void setAgentEnergy(int e) {
		agentEnergy = e;
	}
	
	public int getBirthThres() {
		return birthThres;
	}
	public void setBirthThres(int t) {
		birthThres = t;
	}
	
	public int getGrassGRate() {
		return grassGRate;
	}
	public void setGrassGRate(int r) {
		grassGRate = r;
	}
	
	public int getGrassEnergy() {
		return grassEnergy;
	}
	public void setGrassEnergy(int e) {
		grassEnergy = e;
	}
	
	public boolean getFullSpeed() {
		return fullSpeed;
	}
	public void setFullSpeed(boolean choice) {
		fullSpeed = choice;
	}
}
