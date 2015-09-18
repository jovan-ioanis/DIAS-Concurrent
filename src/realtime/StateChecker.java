package realtime;

import java.util.ArrayList;
import java.util.List;

import dsutil.generic.state.ArithmeticState;
import dsutil.generic.state.State;
import dsutil.protopeer.services.aggregation.AggregationType;

public class StateChecker extends Thread {
	
	private RealTimeListener listener;
	private ArrayList<dsutil.generic.state.State> possibleStates;
	private AggregationType aggregationType;
	private String dirName;
	private String txtName;
	private int startingID;
	private String fullPath;
	private int iterator;
	
	public StateChecker(RealTimeListener listener, String dirName, String txtName, int startingID, AggregationType aggregationType, ArrayList<dsutil.generic.state.State> possibleStates) {
		this.listener = listener;
		this.dirName = dirName;
		this.txtName = txtName;
		this.startingID = startingID;
		this.aggregationType = aggregationType;
		this.possibleStates = possibleStates;
		this.fullPath = "data/" + dirName + "/" + txtName;
		this.iterator = startingID;
	}
	
	public boolean checkState(double value) {
		boolean flag = false;
		if(possibleStates != null && !possibleStates.isEmpty()) {
			for(int i = 0; i < possibleStates.size(); i++) {
				if(aggregationType.equals(AggregationType.ARITHMETIC)) {
					dsutil.generic.state.State s = possibleStates.get(i);					
					if(s instanceof ArithmeticState) {
						ArithmeticState as = (ArithmeticState) s;
						if(value == as.getValue()) {
							flag = true;
							break;
						}
					}
				}
			}
		}
		return flag;
	}
	
	public boolean readFile() {
		BufferedReader in;
		String path = fullPath + iterator + ".txt";
		try {
			in = new BufferedReader(new FileReader());
			
		}
	}
	
	public void run() {
		
	}
	
}
