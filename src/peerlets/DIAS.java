/*
 * Copyright (C) 2015 Evangelos Pournaras
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package peerlets;

import actors.Aggregator;
import actors.Disseminator;
import dsutil.protopeer.FingerDescriptor;
import dsutil.protopeer.services.aggregation.AggregationFunction;
import aggregation.AggregationState;
import dsutil.protopeer.services.aggregation.AggregationType;
import communication.AggregationStrategy;
import communication.DIASMessType;
import communication.DIASMessage;
import communication.Pull;
import communication.PullPush;
import communication.Push;
import consistency.AggregationOutcome;
import consistency.AggregatorReport;
import consistency.BloomFilterParams;
import consistency.DisseminatorReport;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import peerlets.measurements.MeasurementTags;
import protocols.DIASApplicationInterface;
import protopeer.BasePeerlet;
import protopeer.Finger;
import protopeer.Peer;
import dsutil.generic.state.State;
import dsutil.protopeer.services.aggregation.AggregationInterface;
import protopeer.measurement.MeasurementFileDumper;
import protopeer.measurement.MeasurementLog;
import protopeer.measurement.MeasurementLoggerListener;
import protopeer.network.Message;
import protopeer.time.Timer;
import protopeer.time.TimerListener;
import protopeer.util.quantities.Time;

/**
 * This class is the core of DIAS, the Dynamic Efficient and Consistent
 * Aggregation System. DIAS provides aggregation services to different applications.
 * Local stated are aggregated and the following aggregation functions are
 * calculated: SUM, AVG, SUM_SQR, MAX, MIN, COUNT, STDEV. Local possibleStates can change
 * from a set of possible possibleStates. The possible possibleStates of all the peers and their
 * selections form an aggregation aggregationEpoch.
 * 
 * DIAS differs from other aggregation mechanisms as it is only based on the concept
 * of information dissemination. Double-counting elements and changing local values
 * are faced by a consistency mechanisms of mutual memberships checks. This
 * mechanisms is based on and implemented with bloom filters.
 *
 * Every DIAS host contains an aggregator and a disseminator. The disseminator
 * contains the possisble possibleStates and the selected selectedState. The aggregator keeps
 * the aggregate of the aggregationEpoch.The mutual membership model that DIAS exploits is
 * the following:
 *
 * 1. Aggregators have memberships disseminators and vise versa
 * 2. A possible selectedState has membership of aggregators and a disseminator has
 * memberships of aggregators.
 * 3. An aggregate of an aggregator has memberships of possibleStates and a disseminator
 * has possible possibleStates selected by aggregators
 *
 * Based on this mutual memberhsips, consistency checks are performed that
 * gurantee (probabilistically) that the aggregation is updated and is free of
 * double counts free.
 *
 * The application can change the selected selectedState on-the-fly or even start a new
 * aggregation aggregationEpoch by providing new possible possibleStates and a selected selectedState.
 *
 * DIAS hosts communicate to disseminate information. The disseminator of a host
 * contacts periodically the aggregator of another host and provides the required
 * information. The aggregator replies back to the disseminator. The roles
 * exchange in each host. The aggregator becomes disseminator and contacts the
 * previous disseminator that is an aggrgator in this phase. The communications
 * is realized in 3 messages:
 *
 * 1. Host A (Disseminator): Send PUSH to Host B (Aggregator)
 * 2. Host B (Aggregator): Send PULL_PUSH to Host A (Disseminator)
 * 3. Host A (Aggregator): Send PULL to Host B (disseminator)
 *
 * Based on three exchanged messages, nodes mutually update their aggregates.
 * Sampling of nodes is performed using the Peer Sampling Service:
 *
 * M. Jelasity, S. Voulgaris, R. Guerraoui, A.-M. Kermarrec, M. van Steen.,
 * Gossip-based peer sampling, ACM Transactions on Computer Systems, August 2007
 *
 * Other routing mechanisms can be used as well, e.g. random walks, flooding, etc.
 *
 * DIAS exploits routing mechanisms by employing aggregation strategies between
 * three type of neighbors:
 *
 * 1. Unexploited: These neighbors have not contacted yet for an aggregation
 * 2. Exploited: These neighbors have contacted for an aggrgation and contain
 * the most recent selected state
 * 3. Outdated: These neighbors have an old selected state.
 *
 * This information is maintained locally by the AMD and AMS bloom filters.
 *
 * Based on these three types of neighbors, 3 strategies are employed:
 *
 * 1. RANDOM: Either a outdated or an unexploited neighbor is selected for aggregation.
 * If there is no available neighbor in either of them, the second choice is tried
 * 2. EXPLOITATION: Unexploited neighbors are selected. If there is no any available,
 * an outdated is selected.
 * 3. UPDATE: An outdayed neighbor is selected. If there is no any available,
 * an unexploited is selected.
 *
 * @author Evangelos
 */
public class DIAS extends BasePeerlet implements DIASInterface, AggregationInterface, ActivationInterface{

    private boolean active;
    private int aggregationEpoch;
    private Aggregator aggregator;
    private Disseminator disseminator;
    private MeasurementFileDumper dumper;
    private String id;
    private final int Tdias;
    private final int Tsampling;
    private final int numOfSessions;
    private final AggregationStrategy strategy;
    private final int sampleSize;
    private final Map<BloomFilterParams, Object> bloomFilterParams;

    private int firstOutcomes=0;
    private int doubleOutcomes=0;
    private int replaceOutcomes=0;
    private int unsuccessfulOutcomes=0;

    private int numOfPushes=0;
    private int numOfPullPushes=0;
    private int numOfPulls=0;
    
    private boolean thirdMsgONOFF;
    
    private FDTypes type;
    private boolean Aggregator_ON = false;
    private boolean Disseminator_ON = false;
    private String name;

    /**
    * DIAS initialization
    *
    * @param id the local identifier of the experiment of a specific peer
    * @param Tdias the dissemination period
    * @param numOfSessions the number of sessions that can run periodically in DIAS
    * @param Tsampling the sampling period from Peer Sampling Service
    * @param sampleSize the sample size for selecting contact aggregators
    * @param strategy the strategy for the sampling selection of candidate aggregators
    * @param undiscoveredSize the size of the queue with the undiscovered neighbors
    * @param outdatedSize the size of the queue with the outdated neighbors
    * @param exploitedSize the size of the queue with the updated neighbors
    * @param bloomFilterParams the parameterization of the bloom filters in the
    * aggregator and disseminator
    */
    public DIAS(String name, int Tdias, int numOfSessions, int Tsampling, int sampleSize, AggregationStrategy.Strategy strategy, int unexploitedSize, int outdatedSize, int exploitedSize, Map<BloomFilterParams, Object> bloomFilterParams, boolean Aggregator_ON, boolean Disseminator_ON){
        this.id=name;
        this.Tdias=Tdias;
        this.numOfSessions=numOfSessions;
        this.Tsampling=Tsampling;
        this.sampleSize=sampleSize;
        this.strategy=new AggregationStrategy(strategy, unexploitedSize, outdatedSize, exploitedSize);
        this.bloomFilterParams=bloomFilterParams;
        this.active=false;
        this.thirdMsgONOFF = false;
        this.name = name;
        this.Aggregator_ON = Aggregator_ON;
        this.Disseminator_ON = Disseminator_ON;
    }

    /**
    * Intitializes the DIAS peerlet
    *
    * @param peer the local peer
    */
    @Override
    public void init(Peer peer){
        super.init(peer);
        this.id=getPeer().getIdentifier().toString();
        
    }

    /**
    * Starts the DIAS peerlet by sceduling the epoch measurements
    */
    @Override
    public void start(){
    	this.initDescriptor();
        this.scheduleMeasurements();
    }

    /**
    * Stops the DIAS peerlet
    */
    @Override
    public void stop(){

    }

    /**
     * Accesses the application that requests aggregation
     *
     * @return the application instance
     */
    private DIASApplicationInterface getApplication(){
        return (DIASApplicationInterface)this.getPeer().getPeerletOfType(DIASApplicationInterface.class);
    }

    /**
     * Accesses the peer sampling service
     *
     * @return the peer sampling service instance
     */
    private PeerSamplingService getPeerSamplingService(){
        return (PeerSamplingService)this.getPeer().getPeerletOfType(PeerSamplingService.class);
    }

    /**
     * The active selectedState of the DIAS peerlet is executed periodically. A
     * peer is selected and a PUSH message is potentially sent for each aggregation
     * aggregationEpoch.
     */
    private void runActiveState(){
        Timer diasTimer= getPeer().getClock().createNewTimer();
        diasTimer.addTimerListener(new TimerListener(){
            public void timerExpired(Timer timer){
                for(int i=0; i<numOfSessions; i++){
                    Finger aggregator=strategy.getSample();
//                    Finger aggregator=getPeerSamplingService().getRandomFinger();
                    if(aggregator!=null){
                        Push message=createPushMessage(aggregator);
                        //System.err.println("Sending PUSH: " + message.sender.getNetworkAddress() + " -> " + aggregator.getNetworkAddress());
                        getPeer().sendMessage(aggregator.getNetworkAddress(), message);
                        numOfPushes++;
                     }
                }
                runActiveState();
            }
        });
        diasTimer.schedule(Time.inMilliseconds(this.Tdias-((Math.random()-0.5)*this.Tdias)));
    }

    /**
     * A number of samples are collected and provided to the adaptation strategy.
     * The method also provides the pullPushAck of the check in the AMD and AMS bloom
     * filters given the collected sample and the selected state respectively.
     */
    public void collectSamples(){
        Timer sampleCollectionTimer= getPeer().getClock().createNewTimer();
        sampleCollectionTimer.addTimerListener(new TimerListener(){
            public void timerExpired(Timer timer){
                for(int i=0; i<sampleSize; i++){
                    //Finger sample=getPeerSamplingService().getRandomFinger();
                    /*
                    * SOMEWHERE HERE CHECKING SHOULD BE ADDED!!
                    * Instead of getting the finger, we should get FingerDescriptor, and then check if it has an aggregator. It can be added only if he has an aggregator
                    *
                    */
                	FingerDescriptor sampleDesc = getPeerSamplingService().getRandomFingerDescriptor();
                	if(isAggregatorActive(sampleDesc)) {
                		Finger sample = sampleDesc.getFinger();
                		if(!strategy.containsSample(sample)){
                            strategy.setSample(sample,
                                disseminator.checkAMDMembership(sample),
                                disseminator.checkAMSMembership(disseminator.getSelectedState(), sample));
                        }
                		//System.err.println("Dodao!");
                	}
                	else {
                		// this neighbor does not have aggregator, so I cannot speak with him
                		//System.err.println("--> Neighbor " + sampleDesc.getNetworkAddress() + " is AGGREGATORless <--");
                	}                    
                }
                collectSamples();
            }
        });
        sampleCollectionTimer.schedule(Time.inMilliseconds(this.Tsampling-((Math.random()-0.5)*this.Tsampling)));
    }

     /**
     * The passive selectedState defines the reaction of a DIAS peerlet to a received
     * DIAS message. A reaction is defined if and only if the two nodes
     * aggregate within the same aggregationEpoch. Reactions are as follows:
     *
     * 1. PUSH: The aggregator report is generated and the disseminator report
     * is potentially incorporated in the pull-push message.
     * 2. PULL_PUSH: The disseminator receives the final aggregator report. The
     * aggregator aggregates the dissemiantor report and a pull message is generated
     * for the disseminator. 
     * 3. PULL: The diseminator receives the aggregator report.
     * 
     * @author nikolijo: Since DIAS protocol with PULL_PUSH message was not correct
     * it has been decided to remove it. For that purpose, the thirdMessageONOFF parameter 
     * was introduced that is always false. That way, the algorithm bypasses branch for creating 
     * PULL_PUSH message, and creates only PULL message as response to PUSH message.
     *
     * @param message the received DIAS message
     */
    private void runPassiveState(DIASMessage message){
        if(message.aggregationEpoch==this.aggregationEpoch){
            switch(message.type){
                case PUSH:
                	if(isMyAggregatorActive()) {
                		Push push=(Push)message;
                        //System.err.println("Received PUSH: " + push.sender.getNetworkAddress() + " -> " + getPeer().getNetworkAddress());
                        boolean amdCheck=this.disseminator.checkAMDMembership(push.sender);
                        boolean amsCheck=this.disseminator.checkAMSMembership(this.disseminator.getSelectedState(), push.sender);
                        if(this.strategy.isPossibleAggregation(amdCheck, amsCheck) && this.thirdMsgONOFF){
                            this.strategy.removeNeighbor(push.sender);
                            PullPush plps=this.createPullPushMessage(push.sender, push.report);
                            //System.err.println("Sending (PUSH) PULL_PUSH: " + plps.sender.getNetworkAddress() + " -> " + push.sender.getNetworkAddress());
                            getPeer().sendMessage(push.sender.getNetworkAddress(), plps);
                            this.numOfPullPushes++;
                        }
                        else{
                        	
                            Pull pull=this.createPullMessage(push.sender, push.report);
                            //System.err.println("Sending (PUSH) PULL: " + pull.sender.getNetworkAddress() + " -> " + push.sender.getNetworkAddress());
                            getPeer().sendMessage(push.sender.getNetworkAddress(), pull);
                            this.numOfPulls++;
                        }
                	}
                	else {
                		System.err.println("~~ I shouldn't have come here. My aggregator is not active and I still received PUSH message!");
                	}                    
                    break;
                case PULL_PUSH:
                    PullPush pullPush=(PullPush)message;
                    //System.err.println("Received PULL_PUSH: " + pullPush.sender.getNetworkAddress() + " -> " + getPeer().getNetworkAddress());
                    boolean pullPushAck=disseminator.receiveAggregatorReport(pullPush.sender, pullPush.report);
                    if(pullPushAck){
                        this.strategy.importExploited(pullPush.sender);
                    }
                    Push ps=pullPush.push;
                    Pull pl=this.createPullMessage(ps.sender, ps.report);
                    //System.err.println("Sending (PULL_PUSH) PULL: " + pl.sender.getNetworkAddress() + " -> " + pullPush.sender.getNetworkAddress());
                    getPeer().sendMessage(pullPush.sender.getNetworkAddress(), pl);
                    this.numOfPulls++;
                    break;
                case PULL:
                    Pull pull1=(Pull)message;
                    boolean pullAck=this.disseminator.receiveAggregatorReport(pull1.sender, pull1.report);
                    if(pullAck){
                        this.strategy.importExploited(pull1.sender);
                    }
                    //System.err.println("Received PULL: " + pull1.sender.getNetworkAddress() + " -> " + getPeer().getNetworkAddress());
                    break;
                default:
                    //other type of DIAS message
            }
        }
        else{
            //ignore messages in different aggregation epoch
        }
    }
    
    /**
     * Handles incoming messages. Messages are processed if their type is DIAS
     * and the peerlet is active.
     *
     * @param message the incoming message
     */
    @Override
    public void handleIncomingMessage(Message message){
        if(message instanceof DIASMessage && active){
            this.runPassiveState((DIASMessage)message);
        }
    }

    /**
     * Creates a PUSH message by creating a dissemiantor report for a given
     * aggregator
     *
     * @param aggregator the aggregator for which the disseminator report is
     * generated
     *
     * @return a push message
     */
    private Push createPushMessage(Finger aggregator){
        Push push=new Push(this.aggregationEpoch);
        push.sender=getPeer().getFinger();
        push.report=this.disseminator.createDisseminatorReport(aggregator);
        return push;
    }

    /**
     * Creates a PULL message by creating an aggregator report based on a received
     * disseminator report
     *
     * @param disseminator the disseminator that contacted the local aggregator
     * @param disseminatorReport the report received by the disseminator
     *
     * @return a pull message
     */
    private Pull createPullMessage(Finger disseminator, HashMap<DisseminatorReport, Object> disseminatorReport){
        Pull pull=new Pull(this.aggregationEpoch);
        pull.sender=getPeer().getFinger();
        HashMap<AggregatorReport, Object> aggregatorReport=aggregator.receiveDisseminatorReport(disseminator, disseminatorReport);
        pull.report=aggregatorReport;
        this.countOutcome((AggregationOutcome)aggregatorReport.get(AggregatorReport.OUTCOME));
        return pull;
    }

    /**
     * Creates a PULL-PUSH message by replying to a disseminator with the
     * aggregator report and embeding a new PUSH message the disseminator report.
     * In this way the aggregation is performed in two ways.
     *
     * @param disseminator  the disseeminator that contacted the local aggregator.
     * Note that this is the case when the message is perceived as a PULL. In the
     * case of puss, it refers to the aggregator.
     * @param disseminatorReport the report received by the disseminator
     *
     * @return a pull-push message
     */
    private PullPush createPullPushMessage(Finger disseminator, HashMap<DisseminatorReport, Object> disseminatorReport){
        PullPush pullPush=new PullPush(this.aggregationEpoch);
        pullPush.sender=getPeer().getFinger();
        HashMap<AggregatorReport, Object> aggregatorReport=aggregator.receiveDisseminatorReport(disseminator, disseminatorReport);
        pullPush.report=aggregatorReport;
        this.countOutcome((AggregationOutcome)aggregatorReport.get(AggregatorReport.OUTCOME));
        Push push=this.createPushMessage(disseminator);
        pullPush.push=push;
        return pullPush;
    }
    
    /**
     * Returns the aggregate object of a given specific aggregation function.
     * 
     * @param function the aggregation function computed
     * @return the aggregate object of a specific type
     */
    public Object getAggregate(AggregationFunction function){
        if(active /*&& isMyAggregatorActive()*/){
            AggregationState aggregate=this.aggregator.getAggregationState();
            return aggregate.getAggregate(function);
        }
        return null;
    }
    
    /**
     * Performs local aggregation between the aggregator and the disseminator in
     * this peer.
    */
    private void aggregateLocally(){
    	//if(isMyAggregatorActive()) {
    		this.aggregator.addDMAMembership(getPeer().getFinger());
            this.aggregator.addSMAMembership(this.disseminator.getSelectedState());
            this.aggregator.addAggregationState(this.disseminator.getSelectedState());
    	//}        
        this.disseminator.addAMDMembership(getPeer().getFinger());
        this.disseminator.addAMSMemebership(this.disseminator.getSelectedState(), getPeer().getFinger());
    }

    /**
     * This is how an application requests a new aggregation aggregationEpoch of a set of
     * possible possibleStates a selected selectedState. If the DIAS peerlet is inactive,
     * it is activated.
     *
     * @param possibleStates the possible states of an application
     */
    public void requestAggregation(AggregationType type, Collection<State> possibleStates, State selectedState){
        if(!active){
            this.disseminator=new Disseminator(possibleStates, selectedState, this.bloomFilterParams);
            this.aggregator=new Aggregator(type, this.bloomFilterParams);
            this.aggregateLocally();
            this.aggregationEpoch=0;
            this.active=true;
            //this.initDescriptor();
            this.collectSamples();
            this.runActiveState();
        }
        else{
            this.strategy.clear();
            this.disseminator.clearMemberships();
            this.aggregator.clearAggregates();
            this.disseminator.setPossibleStates(possibleStates);
            this.disseminator.setSelectedState(selectedState);
            this.aggregateLocally();
            this.aggregationEpoch++;
        }
    }

    /**
     * Application can change the selected selectedState dynamically and on-the-fly.
     * The bloom filter memberships are adjusted to reflect the new local value
     * in the aggregate. The aggregation strategy also rearranges the exploited,
     * unexploited and outdated neighbors.
     *
     * @param selectedState the new selected state
     */
    public void changeSelectedState(State selectedState){
        if(active){
        	//if(isMyAggregatorActive()) {
        		this.aggregator.removeSMAMembership(this.disseminator.getSelectedState());
                this.aggregator.removeAggregationState(this.disseminator.getSelectedState());
                this.aggregator.addSMAMembership(selectedState);
                this.aggregator.addAggregationState(selectedState);
        	//}            
            this.disseminator.removeAMSMembership(this.disseminator.getSelectedState(), getPeer().getFinger());
            this.disseminator.addAMSMemebership(selectedState, getPeer().getFinger());
            this.disseminator.setSelectedState(selectedState);
            Collection<Finger> outdated=this.strategy.exportOutdated();
            for(Finger finger:outdated){
                if(this.disseminator.checkAMSMembership(selectedState, finger)){
                    this.strategy.importExploited(finger);
                }
                else{
                    this.strategy.importOutdated(finger);
                }
            }
        }
    }
    
    /**
     * Indicates if aggregation has been initialized.
     * 
     * @return boolean if it is active
     */
    public boolean isActive(){
        return this.active;
    }

    /**
     * Scheduling the measurements for DIAS
     */
    private void scheduleMeasurements(){
        dumper=new MeasurementFileDumper("peersLog/numOfAggregators/" +name+"/"+id);
        //System.out.println("FROM DIAS: peersLog/numOfAggregators/Experiment01"+"/"+id);
        getPeer().getMeasurementLogger().addMeasurementLoggerListener(new MeasurementLoggerListener(){
            public void measurementEpochEnded(MeasurementLog log, int epochNumber){
                if(getPeer().getNetworkAddress().toString().equals("10"))
                    System.out.println("Epoch: "+epochNumber);
                if(active){
                    log.log(epochNumber, MeasurementTags.EPOCH, aggregationEpoch);
//                    log.log(epochNumber, MeasurementTags.EXPLOITED_SIZE, strategy.getExploitedSize());
//                    log.log(epochNumber, MeasurementTags.UNEXPLOITED_SIZE, strategy.getExploitedSize());
//                    log.log(epochNumber, MeasurementTags.OUTDATED_SIZE, strategy.getOutdatedSize());
                    log.log(epochNumber, MeasurementTags.AMD_COUNTER, disseminator.getAMDCounter());
                    log.log(epochNumber, MeasurementTags.SMA_COUNTER, aggregator.getSMACounter());
                    log.log(epochNumber, MeasurementTags.DMA_COUNTER, aggregator.getDMACounter());
                    log.log(epochNumber, MeasurementTags.AMS_COUNTER, disseminator.getAMSAverageCounters());
                    log.log(epochNumber, MeasurementTags.AMD_FP, disseminator.getAMDFalsePositiveProbability());
                    log.log(epochNumber, MeasurementTags.SMA_FP, aggregator.getSMAFalsePositiveProbability());
                    log.log(epochNumber, MeasurementTags.DMA_FP, aggregator.getDMAFalsePositiveProbability());
                    log.log(epochNumber, MeasurementTags.AMS_FP, disseminator.getAMSAverageFalsePositiveProbabilities());
                    log.log(epochNumber, AggregationOutcome.FIRST, firstOutcomes);
                    log.log(epochNumber, AggregationOutcome.DOUBLE, doubleOutcomes);
                    log.log(epochNumber, AggregationOutcome.REPLACE, replaceOutcomes);
                    log.log(epochNumber, AggregationOutcome.UNSUCCESSFUL, unsuccessfulOutcomes);
                    firstOutcomes=0;
                    doubleOutcomes=0;
                    replaceOutcomes=0;
                    unsuccessfulOutcomes=0;
                    log.log(epochNumber, DIASMessType.PULL, numOfPulls);
                    log.log(epochNumber, DIASMessType.PULL_PUSH, numOfPullPushes);
                    log.log(epochNumber, DIASMessType.PUSH, numOfPushes);
                    numOfPushes=0;
                    numOfPullPushes=0;
                    numOfPulls=0;
                }
                dumper.measurementEpochEnded(log, epochNumber);
                log.shrink(epochNumber, epochNumber+1);
            }
        });
    }

    /**
     * Counts the number of the outcomes per type for logging the values via
     * the ProtoPeer measurements.
     *
     * @param outcome the aggregation outcome
     */
    private void countOutcome(AggregationOutcome outcome){
        switch(outcome){
            case FIRST:
                this.firstOutcomes++;
                break;
            case DOUBLE:
                this.doubleOutcomes++;
                break;
            case REPLACE:
                this.replaceOutcomes++;
                break;
            case UNSUCCESSFUL:
                this.unsuccessfulOutcomes++;
            default:
                //other introduced future outcomes
        }
    }
    
    //============================================================================================
    // by Jovan
    
    public FingerDescriptor getMyDescriptor() {
    	return getPeerSamplingService().getMyDescriptor();
    }
    
    @Override
    public void setAggregationActivation(boolean activate) {    	
    	getPeerSamplingService().registerDescriptor(type.ACTIVATE_AGGREGATOR, new Boolean(activate));
    }
    
    @Override
    public boolean isAggregatorActive(FingerDescriptor desc) {
    	
    	if(desc.hasDescriptor(type.ACTIVATE_AGGREGATOR)) {
    		//System.out.println("ima deskriptor");
    		return (Boolean)desc.getDescriptor(type.ACTIVATE_AGGREGATOR);
    	}
    	else {
    		//System.out.println("nema deskriptor");
    		return false;
    	}
    	//return true;
    }
    
    public void initDescriptor() {
    	//getMyDescriptor().addDescriptor(type.ACTIVATE_AGGREGATOR, new Boolean(false));
    	getPeerSamplingService().registerDescriptor(type.ACTIVATE_AGGREGATOR, new Boolean(Aggregator_ON));
    }
    
    @Override
    public boolean isMyAggregatorActive() {
//    	if(getMyDescriptor().hasDescriptor(type.ACTIVATE_AGGREGATOR)) {
//    		//System.out.println("Moj peerlet ima deskriptor");
//    		return (Boolean) getMyDescriptor().getDescriptor(type.ACTIVATE_AGGREGATOR);
//    	}
//    	else {
//    		//System.out.println("moj peerlet nema deskriptor");
//    		return false;
//    	}
    	return Aggregator_ON;
    }
    
    public void activate() {
    	System.out.println("\t\t Peer number " + getPeer().getNetworkAddress() + " has been activated!");
    	Aggregator_ON = true;
    	this.setAggregationActivation(true);
    }
    
    public void deactivate() {
    	System.out.println("\t\t Peer number " + getPeer().getNetworkAddress() + " has been deactivated!");
    	Aggregator_ON = false;
    	this.setAggregationActivation(false);
    }


}
