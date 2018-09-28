# Notes about how to implement different functionalities of Repast

## Implement a 2D grid
```Java
import uchicago.src.sim.space.Object2DGrid;

public class space {
  private Object2DGrid agentSpace;
  
  public space(){
    space = new Object2DGrid(xSize,ySize);
    
  for(int i=0; i<xSize; i++)
  {
    for(int j=0; j<ySize; j++)
    {
      space.putObjectAt(i, j, new Integer(0)); 
      // For agents: agentSpace.putObjectAt(x,y,agent)
    }
  }
}
```
```Java
Object2DGrid methods:

.getSizeX() and .getSizeY()
.putObjectAt(x,y,object);
(ObjectType) space.getObjectAt(x,y)
```
## Implement display of 2D grid
```Java
import java.awt.Color;

import uchicago.src.sim.gui.DisplaySurface; // GUI Display
import uchicago.src.sim.gui.ColorMap;		// GUI
import uchicago.src.sim.gui.Value2DDisplay; // GUI -> Displays values
import uchicago.src.sim.gui.Object2DDisplay;// GUI -> Displays Objects

private DisplaySurface displaySurf;

public void begin(){
  buildModel();
  buildSchedule();
  buildDisplay();
     
  displaySurf.display();
}

public void setup(){
  ...
  // Reset the display (tears it down)
	if(displaySurf != null)
		displaySurf.dispose();
  displaySurf = null;
	// Recreate a new display
	displaySurf = new DisplaySurface(this,"Rabbit Grass Simulation");
	registerDisplaySurface("Rabbit Grass Simulation",displaySurf);
}

public void buildSchedule(){
  class ModelStep extends BasicAction{
    public void execute() {
      ...
      displaySurf.updateDisplay();
    }
  }
  schedule.scheduleActionBeginning(0, new ModelStep());
}

public void buildDisplay(){
  ColorMap map = new ColorMap();
	map.mapColor(1, Color.green); // Grass is green (maps the value: 0=black,1=green)
	map.mapColor(0, Color.black); // Back is black
		
	Value2DDisplay displayGrass = new Value2DDisplay(rgSpace.getCurrentGrassSpace(),map);
	displaySurf.addDisplayableProbeable(displayGrass, "Grass");
		
	Object2DDisplay displayAgents = new Object2DDisplay(rgSpace.getCurrentAgentSpace());
	displayAgents.setObjectList(agentList);
	displaySurf.addDisplayableProbeable(displayAgents, "Rabbits");
	// Note: the objects need to implement Drawable to be displayed
}
```

## Implement plots of varying data
```Java
import uchicago.src.sim.analysis.DataSource;		    //Used for charts
import uchicago.src.sim.analysis.OpenSequenceGraph; //Used for charts
import uchicago.src.sim.analysis.Sequence;			    //Used for charts (population plot)

public class MyModel extends SimModelImpl{
  // 1 (declare graph instance)
  private OpenSequenceGraph agentsAndGrassInSpace;
  
  // 2.1 (class for first variable)
	class grassInSpace implements DataSource,Sequence{
	  public Object execute() {
	    return new Double(getSValue());
	  }
	  public double getSValue() {
	    return (double)rgSpace.getTotalGrass();
	  }
	}
	// 2.2 (class for second variable)
	class agentsInSpace implements DataSource,Sequence{
    public Object execute() {
	    return new Double(getSValue());
	  }
	  public double getSValue() {
	    return (double)agentList.size();
	  }
	}
  
  // 3 (show display of graph)
  public void begin(){
    buildModel();
	  buildSchedule();
	  buildDisplay();
    ...
    agentsAndGrassInSpace.display();
  }
  
  // 4 (Tear down display of graph)
  public void setup(){
    ...
    if(agentsAndGrassInSpace != null)
			agentsAndGrassInSpace.dispose();
		agentsAndGrassInSpace = new OpenSequenceGraph("Amount of Agents and Grass",this);
		this.registerMediaProducer("Plot", agentsAndGrassInSpace);
  }
  
  // 5 (Tell schedule when to update the graph)
  public void buildSchedule(){
    ...
    class ActionToExecute extends BasicAction{
			public void execute() {
			  agentsAndGrassInSpace.step();
			}
		}
		schedule.scheduleActionBeginning(0, new ActionToExecute());
  }
  
  // 6 (Build display of graph)
  public void buildDisplay(){
    ...
    agentsAndGrassInSpace.addSequence("Grass", new grassInSpace());
		agentsAndGrassInSpace.addSequence("Rabbits", new agentsInSpace());
  }
}
```
