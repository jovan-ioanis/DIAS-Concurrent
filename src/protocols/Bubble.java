package protocols;

public class Bubble {
	
	/*
	 * Bubble saves information about one node in one epoch
	 */
	
	//current selected state
	public double currentSelectedState;
	
	//aggregation functions
	public double sum;
	public double avg;
	public double sumSqr;
	public double max;
	public double min;
	public double stDev;
	public double count;
	
	//aggregator activated or not
	public double on_off;
	
	//num of messages
	public double numPushMsg;
	public double numPullMsg;
	public double totalMsg;
	
	public double numUpdates;
	public double numExploited;
	public double numDuplicates;
	public double numInconsistencies;

}
