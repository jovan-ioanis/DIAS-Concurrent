package realtime;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import dsutil.generic.state.ArithmeticState;
import dsutil.generic.state.State;
import dsutil.protopeer.services.aggregation.AggregationInterface;
import dsutil.protopeer.services.aggregation.AggregationType;
import peerlets.DIASInterface;
import protopeer.BasePeerlet;
import protopeer.Peer;
import protopeer.time.Timer;
import protopeer.time.TimerListener;
import protopeer.util.quantities.Time;

public class RealTimeDiasApp extends BasePeerlet implements RealTimeListener{
	
	private int Tboot;
	private AggregationType aggregationType;
    private State selectedState;
    private ArrayList<State> possibleStates;
    private ArrayList<State> historyOfStates;
    private String id;
    private Time randomInterval;
    private String fullPath;
    
    private static final String possibleStatePath = "possibleStates/node";
    
    
    //TODO 
    /*
     * 
     * #3 create thread that keeps track of read files, and the new ones
     * 		Name of files must increment (fileName_i, and i++)
     * #4 when change happens, read the file, and send new state to DIAS
     */
	
	public RealTimeDiasApp(int Tboot, AggregationType type) {
		this.Tboot = Tboot;
		this.aggregationType=type;
		this.randomInterval=Time.inMilliseconds(1000);
		this.id = "0";
		this.fullPath = possibleStatePath + id + ".txt";
		this.possibleStates = new ArrayList<State>();
		this.historyOfStates = new ArrayList<State>();		
	}
	
	@Override
	public void init(Peer peer) {
		super.init(peer);
		this.id = getPeer().getIdentifier().toString();
	}
	
	@Override
	public void start() {
		
	}
	
	@Override
	public void stop() {
		
	}
	
	/**
     * Accesses the DIAS aggregation service
    */
    private AggregationInterface getAggregationInterface(){
        return (AggregationInterface)getPeer().getPeerletOfType(DIASInterface.class);
    }
    
    private double getRandomInterval(Time seed){
        return (Math.random()-0.5)*Time.inMilliseconds(seed);
    }
	
	/**
     * Bootstraps the aggregation requests at Tboot.
    */
    private void runBootstrap(){
        Timer bootstrapTimer= getPeer().getClock().createNewTimer();
        bootstrapTimer.addTimerListener(new TimerListener(){
            public void timerExpired(Timer timer){
                getAggregationInterface().requestAggregation(aggregationType, possibleStates, selectedState);
                //runAggregation();
            }
        });
        bootstrapTimer.schedule(Time.inMilliseconds(this.Tboot-this.getRandomInterval(randomInterval)));
    }
    
    private void createThread() {
    	
    }
    
    public void readPossibleStates() {
    	BufferedReader in = null;
    	try {
    		in = new BufferedReader(new FileReader(fullPath));
    		String line;
    		while((line=in.readLine()) != null) {
    			StringTokenizer st = new StringTokenizer(line,"\t");
    			String id;
    			Double value;
    			if(st.hasMoreTokens()) {
    				id = st.nextToken();
    			}
    			if(st.hasMoreTokens()) {
    				value = Double.parseDouble(st.nextToken());
    				possibleStates.add(new ArithmeticState(value));
    			}    			
    		}
    		
    	} catch (FileNotFoundException e) {
    		System.err.println("ERROR: Node " + id + " --> File not found on path:" + fullPath + "\n\tEvery node needs its own possible state file!");
    		e.printStackTrace();
    	} catch (IOException e) {
    		e.printStackTrace();
    	} catch (NumberFormatException e) {
    		System.err.println("ERROR: Node " + id + " --> Value not a number!");
    		e.printStackTrace();
    	} finally {
    		try {
    			if(in != null) {
    				in.close();
    			}				
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public void printOutStates() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("Possible values for node " + this.id + " are: \n");
    	for(int i = 0; i < possibleStates.size(); i++) {
    		State state = possibleStates.get(i);
    		if(state instanceof ArithmeticState) {
    			ArithmeticState astate = (ArithmeticState) state;
    			sb.append(astate.getValue() + "\n");
    		}
    	}
    	System.out.println(sb.toString());
    }
    
    public State initState() {
    	if(!possibleStates.isEmpty()) {
    		this.selectedState = possibleStates.get(0);
    	}
    	else {
    		this.selectedState = new ArithmeticState(0.0);
    	}
    	historyOfStates.add(selectedState);
    	return selectedState;
    }

    @Override
	public void changeState(double value) {
		// TODO Auto-generated method stub
	
	}
    
    public static void main(String args[]) {
    	RealTimeDiasApp rtApp = new RealTimeDiasApp(100, AggregationType.ARITHMETIC);
    	rtApp.readPossibleStates();
    	rtApp.printOutStates();
    }

}
