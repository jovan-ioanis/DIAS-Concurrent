package peerlets;

import dsutil.protopeer.FingerDescriptor;

public interface ActivationInterface {
	
	public void setAggregationActivation(boolean activate);
	
	public boolean isAggregatorActive(FingerDescriptor descriptor);
	
	public boolean isMyAggregatorActive();

}
