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
package protocols;

import dsutil.protopeer.services.aggregation.AggregationType;
import bloomfilter.CHashFactory;
import communication.AggregationStrategy;
import consistency.BloomFilterParams;
import consistency.BloomFilterType;
import enums.PeerSelectionPolicy;
import enums.ViewPropagationPolicy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import parser.EventFileGenerator;
import peerlets.DIAS;
import peerlets.PeerSamplingService;
import protopeer.Experiment;
import protopeer.NeighborManager;
import protopeer.Peer;
import protopeer.PeerFactory;
import protopeer.SimulatedExperiment;
import protopeer.network.NetworkInterfaceFactory;
import protopeer.network.delayloss.DelayLossNetworkInterfaceFactory;
import protopeer.network.delayloss.UniformDelayModel;
import protopeer.scenarios.Scenario;
import protopeer.scenarios.ScenarioExecutor;
import protopeer.scenarios.ScenarioParser;
import protopeer.servers.bootstrap.BootstrapClient;
import protopeer.servers.bootstrap.BootstrapServer;
import protopeer.servers.bootstrap.SimpleConnector;
import protopeer.servers.bootstrap.SimplePeerIdentifierGenerator;
import protopeer.util.quantities.Time;

/**
 *
 * @author Evangelos
 */
public class DIASApplExperiment extends SimulatedExperiment{
	
	/*
	 * 100:50:25:0:25:PUSHPull:r:250:1000:6000:1000:255:15:10:15:15:15:E:c:DOUBLE:16:24:DOUBLE_HASH:16:24:DOUBLE:16:24:DOUBLE:16:24:ARITHMETIC:15000:100000:5:0:1:1.0:1.0:200000:b:c:10:50
	 *  */

    private final static String expSeqNum="02";
    private final static String expID="Experiment"+expSeqNum;

    //Simulation Parameters
    public static int runDuration=800;		//duration of simulation in seconds
    private static int N=1500;					//number of nodes

    //Peer Sampling Service
    private static int c=50;		//length of the view (descriptor table) CHANGE THIS
    private static int H=0;		//healing parameter
    private static int S=50;		//swap parameter
    private static ViewPropagationPolicy viewPropagationPolicy=ViewPropagationPolicy.PUSHPULL;
    private static PeerSelectionPolicy peerSelectionPolicy=PeerSelectionPolicy.RAND;
    private static int Tpss=250;
    private static int A=1000;	//increment of age of descriptors
    private static int B=6000;	//bootstrapping initial period
    
    //DIAS Service Parameterization
    private static int Tdias=1000;		//the dissemination period - every Tdias miliseconds disseminator sends a request
    private static int Tsampling=250;		//the sampling period from Peer Sampling Service - every Tsampling miliseconds peers gossip
    private static int sampleSize=15;		// number of neighbors that DIAS takes from pss CHANGE THIS
    private static int numOfSessions=10;	//the number of sessions that can run periodically in DIAS CHANGE THIS!!
    private static int unexploitedSize=15; //
    private static int outdatedSize=15;
    private static int exploitedSize=15;
    private static AggregationStrategy.Strategy strategy=AggregationStrategy.Strategy.EXPLOITATION;
    private static BloomFilterType amsType=BloomFilterType.COUNTING;
    private static int amsHashType=CHashFactory.DOUBLE_HASH;
    private static int ams_m=16;
    private static int ams_k=24;
    private static int dmaHashType=CHashFactory.DOUBLE_HASH;
    private static int dma_m=16;
    private static int dma_k=24;
    private static int amdHashType=CHashFactory.DOUBLE_HASH;
    private static int amd_m=16;
    private static int amd_k=24;
    private static int smaHashType=CHashFactory.DOUBLE_HASH;
    private static int sma_m=16;
    private static int sma_k=24;
    private static Map<BloomFilterParams, Object> bfParams=new HashMap<BloomFilterParams, Object>();
    
    //DIAS Application Parameterization
    private static AggregationType type=AggregationType.ARITHMETIC;
    private static int Tboot=15000;				//a bootstrapping period before requesting an aggregation
    private static int Taggr=runDuration*1000;	//the period of aggregation request
    private static int k=5;						//the number of possible states
    private static double minValueDomain=0;
    private static double maxValueDomain=1;
    				// SYNCHRONUS SETTINGS: Pt = 1.0 and Ps = 1.0
    private static double Pt=1.0; //0.4
    private static double Ps=1.0; //0.7
    private static int t=200000;					//the period of evaluation for changing a selected state
    private static GenerationScheme genScheme=GenerationScheme.BETA;
    private static SelectionScheme selScheme=SelectionScheme.CYCLICAL;
    
    private static int numOfAggregatorsON = N;
    private static int numOfDisseminatorsON = N;
    private static final String SEPARATOR = ":";
    private static boolean scenarioFound = false;
    
    private static Vector<Boolean> activeAggregators;
    private static Vector<Boolean> activeDisseminators;
    

//     @Override
//	public NetworkInterfaceFactory createNetworkInterfaceFactory() {
//		return new DelayLossNetworkInterfaceFactory(getEventScheduler(),new UniformDelayModel(2,10));
//	}

    public static void main(String[] args) {

    	//1. run DIAS
    	DIASApplExperiment diasApp = new DIASApplExperiment();
    	diasApp.readArgumentsWrapper(args);
    	diasApp.initVectors();
    	makeDirectories();
    	printParameterFile("\r\n");
    	System.out.println("Parameters file printed!\n");
//		createScenario();
   		runDias(diasApp);
    		
    	//2. analyze DIAS and printout the report
   		System.out.println("\n\n");
    	DIASanalyzer analyzer = new DIASanalyzer(0, runDuration, numOfAggregatorsON, expSeqNum);
        analyzer.analyze();
    	
    }
    
    public static void runDias(DIASApplExperiment dias) {
    	System.out.println("DIAS Concurrent Experiment");
    	System.out.println(Runtime.getRuntime().maxMemory());
    	System.out.println("N = " + N);
        System.out.println(expID+"\n");
        Experiment.initEnvironment();
        //final DIASApplExperiment dias = new DIASApplExperiment();
        dias.init();
              
        ScenarioParser parser = new ScenarioParser();
        final Scenario scenario = parser.parseFile("scenarios/numOfAggregators/experiment" + expSeqNum + ".txt");
        if(scenario != null) {
        	System.out.println(parser.toString());
            System.out.println(scenario.dumpToStringBuffer().toString());
            scenarioFound = true;
        }
        else {
        	System.out.println("Couldn't find scenario file on the path: " + "scenarios/numOfAggregators/experiment" + expSeqNum + ".txt");
        	scenarioFound = false;
        }       
        
        PeerFactory peerFactory=new PeerFactory() {
            public Peer createPeer(int peerIndex, Experiment experiment) {
                Peer newPeer = new Peer(peerIndex);
                if (peerIndex == 0) {
                    newPeer.addPeerlet(new BootstrapServer());
                }
                newPeer.addPeerlet(new NeighborManager());
                newPeer.addPeerlet(new SimpleConnector());
                newPeer.addPeerlet(new BootstrapClient(Experiment.getSingleton().getAddressToBindTo(0), new SimplePeerIdentifierGenerator()));
                newPeer.addPeerlet(new PeerSamplingService(c, H, S, peerSelectionPolicy, viewPropagationPolicy, Tpss, A, B));
                newPeer.addPeerlet(new DIAS(expID, Tdias, numOfSessions, Tsampling, sampleSize, strategy, unexploitedSize, outdatedSize, exploitedSize, collectBloomFilterParams(), activeAggregators.get(peerIndex), activeDisseminators.get(peerIndex)));
                newPeer.addPeerlet(new SimpleDIASApplication(expID, Tboot, Taggr, k, minValueDomain, maxValueDomain, t, Pt, Ps, genScheme, selScheme, type));
                if(scenarioFound) {
                	ScenarioExecutor executor = new ScenarioExecutor(0);
                    executor.addScenario(scenario);
                    newPeer.addPeerlet(executor);
                }                
                return newPeer;
            }
        };
        dias.initPeers(0,N,peerFactory);
        dias.startPeers(0,N);

        //run the simulation
        dias.runSimulation(Time.inSeconds(runDuration));

        //AETOSLogReplayer replayer=new AETOSLogReplayer("peersLog/"+folder.getName()+"/", 0, 50);
    }

    private static Map<BloomFilterParams, Object> collectBloomFilterParams(){
        bfParams.put(BloomFilterParams.AMS_TYPE, amsType);
        bfParams.put(BloomFilterParams.AMS_HASH_TYPE, amsHashType);
        bfParams.put(BloomFilterParams.AMS_M, ams_m);
        bfParams.put(BloomFilterParams.AMS_K, ams_k);
        bfParams.put(BloomFilterParams.AMD_HASH_TYPE, amdHashType);
        bfParams.put(BloomFilterParams.AMD_M, amd_m);
        bfParams.put(BloomFilterParams.AMD_K, amd_k);
        bfParams.put(BloomFilterParams.DMA_HASH_TYPE, dmaHashType);
        bfParams.put(BloomFilterParams.DMA_M, dma_m);
        bfParams.put(BloomFilterParams.DMA_K, dma_k);
        bfParams.put(BloomFilterParams.SMA_HASH_TYPE, smaHashType);
        bfParams.put(BloomFilterParams.SMA_M, sma_m);
        bfParams.put(BloomFilterParams.SMA_K, sma_k);
        return bfParams;
    }
    
    public void initVectors() {
    	activeAggregators = new Vector<Boolean>();
    	activeDisseminators = new Vector<Boolean>();
    	for(int i = 0; i < N; i++) {
    		if(i<numOfAggregatorsON) {
    			activeAggregators.add(i, true);
    		}
    		else {
    			activeAggregators.add(i, false);
    		}
    		
    		if(i<numOfDisseminatorsON) {
    			activeDisseminators.add(i, true);
    		}
    		else {
    			activeDisseminators.add(i, false);
    		}
    	}
    }
    
    public void readArgumentsWrapper(String[] args) {
    	try {
    		readArguments(args);
    	}
    	catch(Exception e) {
    		// if command line arguments are badly formatted, or a letter is forwarded instead of a number
    		// use default value. Also, use default values for the rest of the parameters
    	}
    }
    
    private void readArguments(String[] args) {
    	// first command line argument is String with parameters separated by ':'.
    	if(args[0] == null) {
    		return;
    	}
    	StringTokenizer st = new StringTokenizer(args[0], SEPARATOR);
    	if(st.hasMoreTokens()) {
    		runDuration = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		N = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		c = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		H = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		S = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("PUSH".equalsIgnoreCase(token)) {
    			viewPropagationPolicy = ViewPropagationPolicy.PUSH;
    		}
    		else if("PUSHPULL".equalsIgnoreCase(token) || "PUSH_PULL".equalsIgnoreCase(token) || "PUSH-PULL".equalsIgnoreCase(token)) {
    			viewPropagationPolicy = ViewPropagationPolicy.PUSHPULL;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("RAND".equalsIgnoreCase(token) || "R".equalsIgnoreCase(token)) {
    			peerSelectionPolicy = PeerSelectionPolicy.RAND;
    		}
    		else if("OLD".equalsIgnoreCase(token) || "O".equalsIgnoreCase(token)) {
    			peerSelectionPolicy = PeerSelectionPolicy.OLD;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		Tpss = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		A = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		B = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		Tdias = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		Tsampling = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		sampleSize = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		numOfSessions = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		unexploitedSize = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		outdatedSize = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		exploitedSize = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("RANDOM".equalsIgnoreCase(token) || "RAND".equalsIgnoreCase(token) || "R".equalsIgnoreCase(token)) {
    			strategy=AggregationStrategy.Strategy.RANDOM;
    		}
    		else if("EXPLOITATION".equalsIgnoreCase(token) || "E".equalsIgnoreCase(token)) {
    			strategy=AggregationStrategy.Strategy.EXPLOITATION;
    		}
    		else if("UPDATE".equalsIgnoreCase(token) || "U".equalsIgnoreCase(token)) {
    			strategy=AggregationStrategy.Strategy.UPDATE;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("SIMPLE".equalsIgnoreCase(token) || "S".equalsIgnoreCase(token)) {
    			amsType = BloomFilterType.SIMPLE;
    		}
    		else if("COUNTING".equalsIgnoreCase(token) || "C".equalsIgnoreCase(token)) {
    			amsType = BloomFilterType.COUNTING;
    		}
    	}
    	
       
        if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("DOUBLE".equalsIgnoreCase(token) || "DOUBLE_HASH".equalsIgnoreCase(token) || "2".equalsIgnoreCase(token)) {
    			amsHashType = CHashFactory.DOUBLE_HASH;
    		}
    		else if("DEFAULT".equalsIgnoreCase(token) || "DEFAULT_HASH".equalsIgnoreCase(token)) {
    			amsHashType = CHashFactory.DEFAULT_HASH;
    		}
    		else if("TRIPLE".equalsIgnoreCase(token) || "TRIPLE_HASH".equalsIgnoreCase(token)) {
    			amsHashType = CHashFactory.TRIPLE_HASH;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		ams_m = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		ams_k = Integer.parseInt(st.nextToken());
    	}
    	
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("DOUBLE".equalsIgnoreCase(token) || "DOUBLE_HASH".equalsIgnoreCase(token) || "2".equalsIgnoreCase(token)) {
    			dmaHashType = CHashFactory.DOUBLE_HASH;
    		}
    		else if("DEFAULT".equalsIgnoreCase(token) || "DEFAULT_HASH".equalsIgnoreCase(token)) {
    			dmaHashType = CHashFactory.DEFAULT_HASH;
    		}
    		else if("TRIPLE".equalsIgnoreCase(token) || "TRIPLE_HASH".equalsIgnoreCase(token)) {
    			dmaHashType = CHashFactory.TRIPLE_HASH;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		dma_m = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		dma_k = Integer.parseInt(st.nextToken());
    	}
    	
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("DOUBLE".equalsIgnoreCase(token) || "DOUBLE_HASH".equalsIgnoreCase(token) || "2".equalsIgnoreCase(token)) {
    			amdHashType = CHashFactory.DOUBLE_HASH;
    		}
    		else if("DEFAULT".equalsIgnoreCase(token) || "DEFAULT_HASH".equalsIgnoreCase(token)) {
    			amdHashType = CHashFactory.DEFAULT_HASH;
    		}
    		else if("TRIPLE".equalsIgnoreCase(token) || "TRIPLE_HASH".equalsIgnoreCase(token)) {
    			amdHashType = CHashFactory.TRIPLE_HASH;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		amd_m = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		amd_k = Integer.parseInt(st.nextToken());
    	}
    	
    	if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("DOUBLE".equalsIgnoreCase(token) || "DOUBLE_HASH".equalsIgnoreCase(token) || "2".equalsIgnoreCase(token)) {
    			smaHashType = CHashFactory.DOUBLE_HASH;
    		}
    		else if("DEFAULT".equalsIgnoreCase(token) || "DEFAULT_HASH".equalsIgnoreCase(token)) {
    			smaHashType = CHashFactory.DEFAULT_HASH;
    		}
    		else if("TRIPLE".equalsIgnoreCase(token) || "TRIPLE_HASH".equalsIgnoreCase(token)) {
    			smaHashType = CHashFactory.TRIPLE_HASH;
    		}
    	}
    	if(st.hasMoreTokens()) {
    		sma_m = Integer.parseInt(st.nextToken());
    	}
    	if(st.hasMoreTokens()) {
    		sma_k = Integer.parseInt(st.nextToken());
    	}
        
        if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("ARITHMETIC".equalsIgnoreCase(token)) {
    			type = AggregationType.ARITHMETIC;
    		}
    		else if("ARITHMETIC_LIST".equalsIgnoreCase(token)) {
    			type = AggregationType.ARITHMETIC_LIST;
    		}
    	}
        if(st.hasMoreTokens()) {
    		Tboot = Integer.parseInt(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		Taggr = Integer.parseInt(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		k = Integer.parseInt(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		minValueDomain = Double.parseDouble(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		maxValueDomain = Double.parseDouble(st.nextToken());
    	}
        
        if(st.hasMoreTokens()) {
    		Pt = Double.parseDouble(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		Ps = Double.parseDouble(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		t = Integer.parseInt(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("RANDOM".equalsIgnoreCase(token) || "RAND".equalsIgnoreCase(token) || "R".equalsIgnoreCase(token)) {
    			genScheme=GenerationScheme.RANDOM;
    		}
    		else if("UNIFORM".equalsIgnoreCase(token) || "U".equalsIgnoreCase(token)) {
    			genScheme=GenerationScheme.UNIFORM;
    		}
    		else if("BETA".equalsIgnoreCase(token) || "B".equalsIgnoreCase(token)) {
    			genScheme=GenerationScheme.BETA;
    		}
    	}
        if(st.hasMoreTokens()) {
    		String token = st.nextToken();
    		if("RANDOM".equalsIgnoreCase(token) || "RAND".equalsIgnoreCase(token) || "R".equalsIgnoreCase(token)) {
    			selScheme=SelectionScheme.RANDOM;
    		}
    		else if("CYCLICAL".equalsIgnoreCase(token) || "C".equalsIgnoreCase(token)) {
    			selScheme=SelectionScheme.CYCLICAL;
    		}    		
    	}
        if(st.hasMoreTokens()) {
    		numOfAggregatorsON = Integer.parseInt(st.nextToken());
    	}
        if(st.hasMoreTokens()) {
    		numOfDisseminatorsON = Integer.parseInt(st.nextToken());
    	}   	
    }
    
    public static void makeDirectories() {  
    	final File peersLogDir = new File("peersLog");
    	peersLogDir.mkdir();
    	
    	final File reportDir = new File("reports");
    	reportDir.mkdir();
    	
    	final File numOfAggregatorsDir1 = new File("peersLog/numOfAggregators");
    	numOfAggregatorsDir1.mkdir();
    	
    	final File numOfAggregatorsDir2 = new File("reports/numOfAggregators");
    	numOfAggregatorsDir2.mkdir();
    	
    	final File experimentDir = new File("peersLog/numOfAggregators/Experiment" + expSeqNum);
    	experimentDir.mkdir();
    	
    	/*final File scenariosDir = new File("scenarios");
    	scenariosDir.mkdir();
    	
    	final File numOfAggregatorsDir3 = new File("scenarios/numOfAggregators");
    	numOfAggregatorsDir3.mkdir(); */  	
    }
    
    public static void createScenario() {
    	EventFileGenerator efgen = new EventFileGenerator("scenarios/numOfAggregators/experiment" + expSeqNum + ".txt", "scenarios/numOfAggregators/experiment" + expSeqNum + ".txt");
    	efgen.addLineEnd(0, 9, "3e3", "peerlets.DIAS.activate()");
    }
    
  
    
    public static void printParameterFile(String delimiter) {
    	StringBuilder sb = new StringBuilder();
    	
    	sb.append("# Simulation Parameters" + delimiter);    	
    	sb.append("Runtime [ms]=" + runDuration*1000 + delimiter);
    	sb.append("Number of nodes=" + N + delimiter);
    	sb.append(delimiter);
    	
    	sb.append("# Peer sampling Service Parameters" + delimiter);
    	sb.append("View length=" + c + delimiter);
    	sb.append("Healing parameter=" + H + delimiter);
    	sb.append("Swap parameter=" + S + delimiter);
    	String policy="";
    	if(viewPropagationPolicy.equals(ViewPropagationPolicy.PUSH)) {
    		policy = "PUSH";
    	}
    	else if(viewPropagationPolicy.equals(ViewPropagationPolicy.PUSHPULL)){
    		policy = "PUSH-PULL";
    	}
    	sb.append("View propagation policy=" + policy + delimiter);
    	sb.append("Peer selection policy=" + peerSelectionPolicy.toString() + delimiter);
    	sb.append("PSS Communication period [ms]=" + Tpss + delimiter);
    	sb.append("Age increment [ms]=" + A + delimiter);
    	sb.append("Bootstrap time [ms]=" + B + delimiter);
    	sb.append(delimiter);
    	
    	sb.append("# DIAS Service Parameterization" + delimiter);
    	sb.append("DIAS Communication period [ms]=" + Tdias + delimiter);
    	sb.append("Sampling period [ms]=" + Tsampling + delimiter);
    	sb.append("Sampling size=" + sampleSize + delimiter);
    	sb.append("Number of sessions=" + numOfSessions + delimiter);
    	sb.append("Unexploited buffer size=" + unexploitedSize + delimiter);
    	sb.append("Outdated buffer size=" + outdatedSize + delimiter);
    	sb.append("Exploited buffer size=" + exploitedSize + delimiter);
    	sb.append("Aggregation strategy=" + strategy.toString() + delimiter);
    	sb.append("AMS type=" + amsType.toString() + delimiter);
    	String hashType = "";
    	switch(amsHashType) {
    	case 1 :
    		hashType = "DEFAULT_HASH";
    		break;
    	case 2:
    		hashType = "DOUBLE_HASH";
    		break;
    	case 3:
    		hashType = "TRIPLE_HASH";
    		break;    		
    	}
    	sb.append("AMS hashing type=" + hashType + delimiter);
    	sb.append("AMS bits number=" + ams_m + delimiter);
    	sb.append("AMS hash functions number=" + ams_k + delimiter);
    	switch(dmaHashType) {
    	case 1 :
    		hashType = "DEFAULT_HASH";
    		break;
    	case 2:
    		hashType = "DOUBLE_HASH";
    		break;
    	case 3:
    		hashType = "TRIPLE_HASH";
    		break;    		
    	}
    	sb.append("DMA type=" + hashType + delimiter);
    	sb.append("DMA bits number=" + dma_m + delimiter);
    	sb.append("DMA hash function number=" + dma_k + delimiter);
    	switch(amdHashType) {
    	case 1 :
    		hashType = "DEFAULT_HASH";
    		break;
    	case 2:
    		hashType = "DOUBLE_HASH";
    		break;
    	case 3:
    		hashType = "TRIPLE_HASH";
    		break;    		
    	}
    	sb.append("AMD type=" + hashType + delimiter);
    	sb.append("AMD bits number=" + amd_m + delimiter);
    	sb.append("AMD hash function number=" + amd_k + delimiter);
    	switch(smaHashType) {
    	case 1 :
    		hashType = "DEFAULT_HASH";
    		break;
    	case 2:
    		hashType = "DOUBLE_HASH";
    		break;
    	case 3:
    		hashType = "TRIPLE_HASH";
    		break;    		
    	}
    	sb.append("SMA type=" + hashType + delimiter);
    	sb.append("SMA bits number=" + sma_m + delimiter);
    	sb.append("SMA hash function number=" + sma_k + delimiter);
    	sb.append(delimiter);
    	
    	sb.append("# DIAS application Parameterization" + delimiter);
        sb.append("Aggregation type=" + type.toString() + delimiter);
        sb.append("Application bootstrap time=" + Tboot + delimiter);
        sb.append("Aggregation period [ms]=" + Taggr + delimiter);
        sb.append("Number of possible states=" + k + delimiter);
        sb.append("Minimal input value=" + minValueDomain + delimiter);
        sb.append("Maximal input value=" + maxValueDomain + delimiter);
        sb.append("Time transition probability=" + Pt + delimiter);
    	sb.append("Parameter transition probability=" + Ps + delimiter);
        sb.append("State transitions period [ms]=" + t + delimiter);
    	
    	sb.append("Generation scheme=" + genScheme.toString() + delimiter);
    	sb.append("Selection scheme=" + selScheme.toString() + delimiter);
    	sb.append(delimiter);
    	
    	sb.append("Active aggregator number=" + numOfAggregatorsON + delimiter);
    	sb.append("Active disseminator number=" + numOfDisseminatorsON + delimiter);
    	
    	PrintWriter out;
    	try {
			out = new PrintWriter(new BufferedWriter(new FileWriter("reports/numOfAggregators/paramsFile" + expSeqNum + ".txt", false)));
			out.print(sb.toString());
	    	out.flush();
	    	out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}    	
    }


}
