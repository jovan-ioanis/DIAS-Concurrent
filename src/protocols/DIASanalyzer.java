package protocols;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import communication.DIASMessType;
import consistency.AggregationOutcome;
import dsutil.protopeer.services.aggregation.AggregationFunction;
import protopeer.measurement.LogReplayer;
import protopeer.measurement.MeasurementLog;

public class DIASanalyzer {
	
	//private static final String expSeqNum="02";
    private static final String expID="Experiment";
    private static final String path = "reports/numOfAggregators/";
    private static String name = "Experiment";
    private static final int numOfEpochs = 800;
    
    private LogReplayer replayer;
    private int minEpochNum;
    private int maxEpochNum;
    private File[] listOfFiles;
    //private int numOfEpochsToConverge = 0;
    //private double stdDeviationConvergence = 0.0;
    
    private PrintWriter out;
    //private boolean startingPrinted = false;
    
    //Set<Integer> setofAggregatorsON = new HashSet<Integer>();
    ArrayList<ArrayList<Bubble>> list = new ArrayList<ArrayList<Bubble>>();
    
    public static final int SUM = 0;
    public static final int AVG = 1;
    public static final int MIN = 2;
    public static final int MAX = 3;
    public static final int COUNT = 4;
    public static final int SUM_SQR = 5;
    public static final int STD_DEV = 6;
    
    
    public static final int UPDATES = 0;
    public static final int EXPLOITATION = 1;
    public static final int DUPLICATES = 2;
    public static final int INCONSISTENCIES = 3;
    
    public static final int PUSH_NUM = 0;
    public static final int PULL_NUM = 1;
    public static final int TOTAL = 2;    
    
    public static final String DELIMITER = ",";//System.getProperty("line.separator");    
    
    private double[] actualValues = new double[7];
    private double[] estimatedValues = new double[7];
    private double[] messages = new double[3];
    private double[] counters = new double[4];
    private double[] stdDevs = new double[7];
    private double[] accuracies = new double[7];
    private int currentAggregatorON = 0;
    private static final double maxError = 1.0;
    private String expSeqNum;
    private String finalPath;
    
    
    public DIASanalyzer(int minEpochNum, int maxEpochNum, int numOfAggregatorsON, String name) {
    	this.minEpochNum = minEpochNum;
    	this.maxEpochNum = maxEpochNum;
    	this.replayer = new LogReplayer();
    	this.name = name;
    	this.finalPath = "peersLog/numOfAggregators/" + name + "/";
    	try {
			out = new PrintWriter(new BufferedWriter(new FileWriter("reports/numOfAggregators/" + this.name + ".txt", false)));
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
//    public static void main(String args[]) {
//    	DIASanalyzer analyzer = new DIASanalyzer(0, numOfEpochs);
//    	analyzer.loadFiles();
//    	System.out.println("Loaded Files!");
//    	analyzer.analyze();
//    }
    
    public void analyze() {
    	loadFiles();
    	System.out.println("Files Loaded!");
    	printStarting();
    	analyzeList();
    }
   
    public void loadFiles() {
    	try {
    		File folder = new File(finalPath);
    		listOfFiles = folder.listFiles();
    		//System.out.println("Number of files: " + listOfFiles.length);
    		loadList();
    	}
    	catch(Exception e) {
    		System.out.println("None files were found on path: " + finalPath);
    	}
    }
    
    public void analyzeList() {
    	analyzeByEpoch();
    	close();
    }
    
//    public void printOut() {
//    	for(int i = 0; i < listOfFiles.length; i++) {
//    		try {
//    			
//				MeasurementLog loadedLog=replayer.loadLogFromFile(path+listOfFiles[i].getName());
//				StringBuilder sb = new StringBuilder();
//				sb.append("AVG: " + loadedLog.getAggregate(AggregationFunction.AVG).getAverage());
//				sb.append("\t");
//				sb.append("MAX: " + loadedLog.getAggregate(AggregationFunction.MAX).getAverage());
//				System.out.println(sb.toString());
//				
//			} catch (ClassNotFoundException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//    	}    	
//    }
    
    public void analyzeByEpoch() {
    	for(int epoch = minEpochNum; epoch < maxEpochNum; epoch++) {
    		calculateActual(epoch);
    		calculateEstimated(epoch);
    		printToFile(epoch, DIASanalyzer.DELIMITER);
    		printToConsole(epoch, "\t");
    		for(int i = 0; i < 7; i++) {
    			if(i < 3) {
    				messages[i] = -1.0;
    			}
    			if(i < 4) {
    				counters[i] = -1.0;
    			}
    			stdDevs[i] = -1.0;
    			actualValues[i] = -1.0;
    			estimatedValues[i] = -1.0;
    			accuracies[i] = -1.0;
    		}
    		currentAggregatorON = -1;
    	}
    }
    
    public void loadList() {
    	for(int node = 0; node < listOfFiles.length; node++) {
    		list.add(node, new ArrayList<Bubble>());
    		//System.out.println("FILENAME: " + listOfFiles[node].getName());
    		try {
    			MeasurementLog loadedLog=replayer.loadLogFromFile(finalPath+listOfFiles[node].getName());
    			for(int epoch = minEpochNum; epoch < maxEpochNum; epoch++) {
    			
    				Bubble b = new Bubble();					
					
					b.avg = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.AVG).getSum();
					b.count = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.COUNT).getAverage();
					b.max = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.MAX).getSum();
					b.min = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.MIN).getMin();
					b.sum = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.SUM).getAverage();
					b.sumSqr = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.SUM_SQR).getMin();
					b.stDev = loadedLog.getAggregateByEpochNumber(epoch, AggregationFunction.STDEV).getAverage();
					
					b.numDuplicates = loadedLog.getAggregateByEpochNumber(epoch, AggregationOutcome.DOUBLE).getSum();
					b.numExploited = loadedLog.getAggregateByEpochNumber(epoch, AggregationOutcome.FIRST).getSum();
					b.numUpdates = loadedLog.getAggregateByEpochNumber(epoch, AggregationOutcome.REPLACE).getSum();
					b.numInconsistencies = loadedLog.getAggregateByEpochNumber(epoch, AggregationOutcome.UNSUCCESSFUL).getSum();
					
					b.numPushMsg = loadedLog.getAggregateByEpochNumber(epoch, DIASMessType.PUSH).getSum();
					b.numPullMsg = loadedLog.getAggregateByEpochNumber(epoch, DIASMessType.PULL).getSum();
					b.totalMsg = b.numPushMsg + b.numPullMsg;
					
					b.on_off = loadedLog.getAggregateByEpochNumber(epoch, "ON_OFF").getAverage();
					
					b.currentSelectedState = loadedLog.getAggregateByEpochNumber(epoch, "SELECTION").getAverage();
					
					list.get(node).add(epoch, b);				
				
    			}
    		}
    		catch (ClassNotFoundException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    }
    
       
    public void calculateActual(int epoch) {
    	double actualSum = 0.0;
    	double actualSumSqr = 0.0;
    	double actualMax = -1.0;
    	double actualMin = 2.0;
    	
    	double sumDuplicates = 0.0;
    	double sumUpdates = 0.0;
    	double sumExploitation = 0.0;
    	double sumInconsistencies = 0.0;
    	
    	double sumPush = 0.0;
    	double sumPull = 0.0;
    	double sumTotal = 0.0;
    	
    	for(int node = 0; node < listOfFiles.length; node++) {
    			double currentValue = list.get(node).get(epoch).currentSelectedState;
    			
    			actualSum += currentValue;
    			actualSumSqr += Math.pow(currentValue, 2);
    			if(currentValue < actualMin) {
    				actualMin = currentValue;
    			}
    			if(currentValue > actualMax) {
    				actualMax = currentValue;
    			}
    			
    			sumDuplicates += list.get(node).get(epoch).numDuplicates;
    			sumUpdates += list.get(node).get(epoch).numUpdates;
    			sumExploitation += list.get(node).get(epoch).numExploited;
    			sumInconsistencies += list.get(node).get(epoch).numInconsistencies;
    			
    			sumPush += list.get(node).get(epoch).numPushMsg;
    			sumPull += list.get(node).get(epoch).numPullMsg;
    			sumTotal += list.get(node).get(epoch).totalMsg;
    			
    	}
    	double actualAverage = actualSum/listOfFiles.length;
    	double actualSumDiffSqr = 0.0;
    	for(int node = 0; node < listOfFiles.length; node++) {
    		actualSumDiffSqr += Math.pow(list.get(node).get(epoch).currentSelectedState - actualAverage, 2);
    	}
    	double actualStdDeviation = Math.sqrt(actualSumDiffSqr/actualSum); 
    	actualValues[DIASanalyzer.SUM] = actualSum;
    	actualValues[DIASanalyzer.AVG] = actualAverage;
    	actualValues[DIASanalyzer.MAX] = actualMax;
    	actualValues[DIASanalyzer.MIN] = actualMin;
    	actualValues[DIASanalyzer.SUM_SQR] = actualSumSqr;
    	actualValues[DIASanalyzer.STD_DEV] = actualStdDeviation;
    	actualValues[DIASanalyzer.COUNT] = listOfFiles.length;
    	
    	counters[DIASanalyzer.DUPLICATES] = sumDuplicates;
    	counters[DIASanalyzer.UPDATES] = sumUpdates;
    	counters[DIASanalyzer.EXPLOITATION] = sumExploitation;
    	counters[DIASanalyzer.INCONSISTENCIES] = sumInconsistencies;
    	
    	messages[DIASanalyzer.PULL_NUM] = sumPull;
    	messages[DIASanalyzer.PUSH_NUM] = sumPush;
    	messages[DIASanalyzer.TOTAL] = sumTotal;
    	
    }
    
    public void calculateEstimated(int epoch) {
    	double estimSum = 0.0;
    	double estimAverage = 0.0;
    	double estimMin = 0.0;
    	double estimMax = 0.0;
    	double estimCount = 0.0;
    	double estimSumSqr = 0.0;
    	double estimStdDev = 0.0;
    	
    	int numAggregatorsON = 0;
    	
    	for(int node = 0; node < listOfFiles.length; node++) {
    		if(list.get(node).get(epoch).on_off == 1.0) {
    			numAggregatorsON++;
    			
    			estimAverage += list.get(node).get(epoch).avg;
    			estimCount += list.get(node).get(epoch).count;
    			estimMax += list.get(node).get(epoch).max;
    			estimMin += list.get(node).get(epoch).min;
    			estimStdDev += list.get(node).get(epoch).stDev;
    			estimSum += list.get(node).get(epoch).sum;
    			estimSumSqr += list.get(node).get(epoch).sumSqr;
    			
    		}    		
    	}
    	// Estimated values is average of all aggregate functions
    	estimatedValues[DIASanalyzer.SUM_SQR] = estimSumSqr/numAggregatorsON;
    	estimatedValues[DIASanalyzer.AVG] = estimAverage/numAggregatorsON;
    	estimatedValues[DIASanalyzer.COUNT] = estimCount/numAggregatorsON;
    	estimatedValues[DIASanalyzer.MAX] = estimMax/numAggregatorsON;
    	estimatedValues[DIASanalyzer.MIN] = estimMin/numAggregatorsON;
    	estimatedValues[DIASanalyzer.STD_DEV] = estimStdDev/numAggregatorsON;
    	estimatedValues[DIASanalyzer.SUM] = estimSum/numAggregatorsON;
    	 
    	currentAggregatorON = numAggregatorsON;
    	
    	double sumsqrAvg = 0.0;
    	double sumsqrCount = 0.0;
    	double sumsqrMax = 0.0;
    	double sumsqrMin = 0.0;
    	double sumsqrStdDev = 0.0;
    	double sumsqrSum = 0.0;
    	double sumsqrSumSqr = 0.0;
    	
    	for(int node = 0; node < listOfFiles.length; node++) {
    		if(list.get(node).get(epoch).on_off == 1.0) {    			
    			sumsqrAvg += Math.pow(list.get(node).get(epoch).avg - estimatedValues[DIASanalyzer.AVG], 2);
    			sumsqrCount += Math.pow(list.get(node).get(epoch).count - estimatedValues[DIASanalyzer.COUNT], 2);
    			sumsqrMax += Math.pow(list.get(node).get(epoch).max - estimatedValues[DIASanalyzer.MAX], 2);
    			sumsqrMin += Math.pow(list.get(node).get(epoch).min - estimatedValues[DIASanalyzer.MIN], 2);
    			sumsqrStdDev += Math.pow(list.get(node).get(epoch).stDev - estimatedValues[DIASanalyzer.STD_DEV], 2);
    			sumsqrSum += Math.pow(list.get(node).get(epoch).sum - estimatedValues[DIASanalyzer.SUM], 2);
    			sumsqrSumSqr += Math.pow(list.get(node).get(epoch).sumSqr - estimatedValues[DIASanalyzer.SUM_SQR], 2);
    		} 
    	}
    	
    	stdDevs[DIASanalyzer.AVG] = Math.sqrt(sumsqrAvg/estimAverage);
    	stdDevs[DIASanalyzer.COUNT] = Math.sqrt(sumsqrCount/estimCount);
    	stdDevs[DIASanalyzer.MAX] = Math.sqrt(sumsqrMax/estimMax);
    	stdDevs[DIASanalyzer.MIN] = Math.sqrt(sumsqrMin/estimMin);
    	stdDevs[DIASanalyzer.STD_DEV] = Math.sqrt(sumsqrStdDev/estimStdDev);
    	stdDevs[DIASanalyzer.SUM] = Math.sqrt(sumsqrSum/estimSum);
    	stdDevs[DIASanalyzer.SUM_SQR] = Math.sqrt(sumsqrSumSqr/estimSumSqr);    
    	
    	// Accuracy is calculated as accuracy = 1 - error/max error. Max error is the largest difference between possible values
    	// for the uniformly distributed values between 0 and 1, max error is 1.
    	
    	accuracies[DIASanalyzer.SUM_SQR] = 1 - Math.abs(actualValues[DIASanalyzer.SUM_SQR] - estimatedValues[DIASanalyzer.SUM_SQR])/getMaxError();
    	accuracies[DIASanalyzer.AVG] = 1 - Math.abs(actualValues[DIASanalyzer.AVG] - estimatedValues[DIASanalyzer.AVG])/getMaxError();
    	accuracies[DIASanalyzer.COUNT] = 1 - Math.abs(actualValues[DIASanalyzer.COUNT] - estimatedValues[DIASanalyzer.COUNT])/getMaxError();
    	accuracies[DIASanalyzer.MAX] = 1 - Math.abs(actualValues[DIASanalyzer.MAX] - estimatedValues[DIASanalyzer.MAX])/getMaxError();
    	accuracies[DIASanalyzer.MIN] = 1 - Math.abs(actualValues[DIASanalyzer.MIN] - estimatedValues[DIASanalyzer.MIN])/getMaxError();
    	accuracies[DIASanalyzer.STD_DEV] = 1 - Math.abs(actualValues[DIASanalyzer.STD_DEV] - estimatedValues[DIASanalyzer.STD_DEV])/getMaxError();
    	accuracies[DIASanalyzer.SUM] = 1 - Math.abs(actualValues[DIASanalyzer.SUM] - estimatedValues[DIASanalyzer.SUM])/getMaxError();
    	accuracies[DIASanalyzer.SUM_SQR] = 1 - Math.abs(actualValues[DIASanalyzer.SUM_SQR] - estimatedValues[DIASanalyzer.SUM_SQR])/getMaxError();
    	
    }
    
    public double getMaxError() {
    	return maxError;
    }
    
    public void printToFile(int epoch, String delimiter) {
    	StringBuilder sb = new StringBuilder();
    	sb.append(epoch + delimiter);
    	sb.append(listOfFiles.length + delimiter);		// total number of nodes
    	sb.append(currentAggregatorON + delimiter);		// number of active aggregators
    	sb.append(listOfFiles.length + delimiter);		// number of (active) disseminators
    	for(int i = 0; i < 7; i++) {
    		sb.append(roundDecimals(stdDevs[i]) + delimiter + roundDecimals(accuracies[i]) + delimiter);
    	}
    	for(int i = 0; i < 4; i++) {
    		sb.append(counters[i] + delimiter);
    	}
    	sb.append(messages[DIASanalyzer.PUSH_NUM] + delimiter + messages[DIASanalyzer.PULL_NUM] + delimiter + messages[DIASanalyzer.TOTAL]);
    	//System.out.println(sb.toString());
    	out.println(sb.toString());
    	out.flush();
    }
    
    public void printToConsole(int epoch, String delimiter) {
    	StringBuilder sb = new StringBuilder();
    	sb.append(epoch + delimiter);
    	sb.append(listOfFiles.length + delimiter);		// total number of nodes
    	sb.append(currentAggregatorON + delimiter);		// number of active aggregators
    	sb.append(listOfFiles.length + delimiter);		// number of (active) disseminators
    	for(int i = 0; i < 7; i++) {
    		if(i == DIASanalyzer.SUM_SQR) {
    			sb.append(delimiter + roundDecimals(stdDevs[i]) + delimiter + roundDecimals(accuracies[i]) + delimiter);    			
    		}
    		else {
    			sb.append(delimiter + roundDecimals(stdDevs[i]) + delimiter + roundDecimals(accuracies[i]) + delimiter);
    		}
    	}
    	sb.append(delimiter);
    	for(int i = 0; i < 4; i++) {
    		sb.append(counters[i] + delimiter);
    	}
    	sb.append(delimiter);
    	sb.append(messages[DIASanalyzer.PUSH_NUM] + delimiter + messages[DIASanalyzer.PULL_NUM] + delimiter + messages[DIASanalyzer.TOTAL]);
    	System.out.println(sb.toString());
    }
    
    public void close() {
    	out.flush();
    	out.close();
    }
    
    public String roundDecimals(Double decimal) {
    	if(decimal.equals(Double.NaN)) {
    		return "-";
    	}
    	DecimalFormat myFormatter = new DecimalFormat("###.####");
    	String output = myFormatter.format(decimal);
    	return output;
//    	if(decimal.equals(Double.NaN)) {
//    		return decimal;
//    	}
//    	BigDecimal bd = new BigDecimal(decimal);
//        bd = bd.setScale(5, BigDecimal.ROUND_UP);
//        return bd.doubleValue();
    }
    
//    public static final int SUM = 0;
//    public static final int AVG = 1;
//    public static final int MIN = 2;
//    public static final int MAX = 3;
//    public static final int COUNT = 4;
//    public static final int SUM_SQR = 5;
//    public static final int STD_DEV = 6;
//    
//    
//    public static final int UPDATES = 0;
//    public static final int EXPLOITATION = 1;
//    public static final int DUPLICATES = 2;
//    public static final int INCONSISTENCIES = 3;
//    
//    public static final int PUSH_NUM = 0;
//    public static final int PULL_NUM = 1;
//    public static final int TOTAL = 2; 
    
    public void printStarting() {
    	StringBuilder sb = new StringBuilder("# ");
    	sb.append("Number of Epochs,Number of Nodes,Number of Aggregators,Number of Disseminators,");
    	sb.append("Standard Deviation (SUM),Accuracy (SUM),");
    	sb.append("Standard Deviation (AVG),Accuracy (AVG),");
    	sb.append("Standard Deviation (MIN),Accuracy (MIN),");
    	sb.append("Standard Deviation (MAX),Accuracy (MAX),");
    	sb.append("Standard Deviation (COUNT),Accuracy (COUNT),");
    	sb.append("Standard Deviation (SUMSQR),Accuracy (SUMSQR),");
    	sb.append("Standard Deviation (STDDEV),Accuracy (STDDEV),");
    	sb.append("Number of Updates,Number of Exploitations,Number of Duplicates,Number of Inconsistencies,");
    	sb.append("Number of PUSH Messages,Num of PULL Messages,Number of Messages");
    	System.out.println(sb.toString());
    	out.println(sb.toString());
    	out.flush();
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    /*
    private void calculateStdDeviation(Object tag) {
    	int numOfNodes = listOfFiles.length;
    	ArrayList<Double> averageList = new ArrayList<Double>();
    	ArrayList<Double> sumList = new ArrayList<Double>();
    	ArrayList<Double> sumSquaredDiffList = new ArrayList<Double>();
    	ArrayList<ArrayList<Double>> valuesList = new ArrayList<ArrayList<Double>>();
    	ArrayList<Double> stdDeviation = new ArrayList<Double>();
    	
    	/*for(int i = 0; i < maxEpochNum; i++) {
    		valuesList.add(new ArrayList<Double>());
    	}
    	
    	for(int i = 0; i < numOfNodes; i++) {
    		for(int j = 0; j < maxEpochNum; j++) {
    			try {
    				
					MeasurementLog loadedLog=replayer.loadLogFromFile(path+listOfFiles[i].getName());
					double value = loadedLog.getAggregateByEpochNumber(j, tag).getSum();
					valuesList.get(j).add(i, value);
					
				} catch (ClassNotFoundException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    	}
    	
    	for(int j = minEpochNum; j < maxEpochNum; j++) {
    		// Calculating results per epoch
    		valuesList.add(j, new ArrayList<Double>());
    		double sum = 0;
    		for(int i = 0; i < numOfNodes; i++) {
    			// for each node get values for current epoch
    			try {   
    				// getting value from the file
    				MeasurementLog loadedLog=replayer.loadLogFromFile(path+listOfFiles[i].getName());
    				//System.err.println("path was ok!");
    				double value = loadedLog.getAggregateByEpochNumber(j, tag).getSum();
    				//System.out.println("reading was ok");
    				// calculate sum of all values 
    				//if(!valuesList.get(j).isEmpty()) {
    					sum = sum + value;
    				//}
    				if(valuesList.get(j) == null) {
    					valuesList.add(new ArrayList<Double>());
    				}
    				// store values
    				valuesList.get(j).add(value);
    			}
    			catch (Exception e) {
    				System.out.println("Exception while reading file!!");
    				e.printStackTrace();
    			}
    		}
    		// store sum 
    		sumList.add(j, sum);
    		// store avg
    		averageList.add(j, sumList.get(j)/numOfNodes);
    		ArrayList<Double> epochValues  = valuesList.get(j);
    		//if(!epochValues.isEmpty()) {
    			double sumSqDiff = 0;
        		for(int k = 0; k < numOfNodes; k++) {
        			double val1 = epochValues.get(k) - averageList.get(j);
        			double val = Math.pow(val1, 2);
        			sumSqDiff += val;
        		}
        		sumSquaredDiffList.add(j, sumSqDiff/sumList.get(j));
    		//}
    		//else {
    			//sumSquaredDiffList.add(j, 0.0);
    		//}
    		
    		stdDeviation.add(j, Math.sqrt(sumSquaredDiffList.get(j)/sumList.get(j)));
    	}
    	
    	System.out.println("\nResults of standard deviation of " +  tag +  " value of all nodes per epoch number!\n");
    	printValues(stdDeviation);    	
    }
    
    public void printValues(ArrayList list) {
    	for(int i = 0; i < list.size(); i++) {
    		System.out.println("epoch num: " + i + "\tStd Deviation: " + list.get(i));
    	}
    }
    
    public void calculateStdDeviation1(Object tag) {
    	
    	ArrayList<ArrayList<Double>> valueList = new ArrayList<ArrayList<Double>>();
    	// each entry is information about each epoch
    	ArrayList<Double> sumList = new ArrayList<Double>();
    	ArrayList<Double> avgList = new ArrayList<Double>();
    	ArrayList<Double> sumSqrDiffList = new ArrayList<Double>();
    	ArrayList<Double> stdDeviationList = new ArrayList<Double>();
    	ArrayList<Double> activeAggregatorCountList = new ArrayList<Double>();
    	ArrayList<ArrayList<Boolean>> isAggregatorActivePerEpoch = new ArrayList<ArrayList<Boolean>>();
    	
    	for(int k = minEpochNum; k < maxEpochNum; k++) {
    		activeAggregatorCountList.add(k, 0.0);
    	}
    	
    	for(int i = 0; i < listOfFiles.length; i++) {
    		valueList.add(i, new ArrayList<Double>());
    		isAggregatorActivePerEpoch.add(i, new ArrayList<Boolean>());
    		
    		try {
    			
				MeasurementLog loadedLog=replayer.loadLogFromFile(path+listOfFiles[i].getName());
				ArrayList<Double> list = valueList.get(i);
				for(int j = minEpochNum; j < maxEpochNum; j++) {
					double value = loadedLog.getAggregateByEpochNumber(j, tag).getSum();
					double on_off = loadedLog.getAggregateByEpochNumber(j, "ON_OFF").getSum();
					//System.out.println(on_off);
					if(on_off == 1.0) {
						double count = activeAggregatorCountList.remove(j);
						count +=1;
						activeAggregatorCountList.add(j, count);
						setofAggregatorsON.add(i);
						isAggregatorActivePerEpoch.get(i).add(j, true);
					}
					else /*if(on_off == -1.0) {
						isAggregatorActivePerEpoch.get(i).add(j, false);
					}
					list.add(j, value);
				}
				
			} catch (ClassNotFoundException e) {				
				e.printStackTrace();
			} catch (IOException e) {				
				e.printStackTrace();
			}    		
    	}
    	
    	numOfEpochsToConverge = 0;
    	stdDeviationConvergence = 0.0;
    	
    	for(int i = minEpochNum; i < maxEpochNum; i++) {
    		double sum = 0;
    		for(int j = 0; j < listOfFiles.length; j++) {
    			if(isAggregatorActivePerEpoch.get(j).get(i) == true) {
    				//System.out.println("USAO");
    				sum += valueList.get(j).get(i);
    			}    			
    		}
    		sumList.add(i, sum);
    		avgList.add(i, sumList.get(i)/activeAggregatorCountList.get(i));
    		double sumDiffSqr = 0;
    		for(int j = 0; j < listOfFiles.length; j++) {
    			if(isAggregatorActivePerEpoch.get(j).get(i) == true) {
    				sumDiffSqr += Math.pow(valueList.get(j).get(i) - avgList.get(i), 2);
    			}
    		}
    		sumSqrDiffList.add(i, sumDiffSqr);
    		stdDeviationList.add(i, Math.sqrt(sumSqrDiffList.get(i)/sumList.get(i)));
    		System.out.println("epoch " + i + ":\t" + stdDeviationList.get(i));
    		
    		if(i > minEpochNum) {
    			if(!stdDeviationList.get(i-1).equals(Double.NaN) && !stdDeviationList.get(i).equals(Double.NaN) && !stdDeviationList.get(i-1).equals(stdDeviationList.get(i))) {
    				//System.out.println("prev: " + stdDeviationList.get(i-1) + " current: " + stdDeviationList.get(i));
    				numOfEpochsToConverge++;
    				stdDeviationConvergence = stdDeviationList.get(i);
    			}
    		}    		
    	}
    	System.out.println("Number of epochs necessary for system to converge is: " + numOfEpochsToConverge);
    	System.out.println("Number of Aggregators that were turned on is: " + setofAggregatorsON.size());
    	printStarting();
    	addToReport();
    }
    
    public void countMessages() {
    	ArrayList<ArrayList<Double>> pushMsgList = new ArrayList<ArrayList<Double>>();
    	ArrayList<ArrayList<Double>> pullMsgList = new ArrayList<ArrayList<Double>>();
    	ArrayList<Double> sumPushMsg = new ArrayList<Double>();
    	ArrayList<Double> sumPullMsg = new ArrayList<Double>();
    	
    	for(int i = 0; i < listOfFiles.length; i++) { 
    		pushMsgList.add(i, new ArrayList<Double>());
    		pullMsgList.add(i, new ArrayList<Double>());
    		try {    			
    			MeasurementLog loadedLog=replayer.loadLogFromFile(path+listOfFiles[i].getName());
    			for(int j = minEpochNum; j < maxEpochNum; j++) {
    				pushMsgList.get(i).add(j, loadedLog.getAggregateByEpochNumber(j, DIASMessType.PUSH).getSum());
    				pullMsgList.get(i).add(j, loadedLog.getAggregateByEpochNumber(j, DIASMessType.PULL).getSum());
    			} 
    			
			} catch (ClassNotFoundException e) {					
				e.printStackTrace();
			} catch (IOException e) {					
				e.printStackTrace();
			}
    	}
    	double sumPushAll = 0.0;
    	double sumPullAll = 0.0;
    	
    	for(int i = minEpochNum; i < maxEpochNum; i++) {
    		double sumPush = 0.0;
    		double sumPull = 0.0;
    		
    		for(int j = 0; j < listOfFiles.length; j++) {
    			sumPush += pushMsgList.get(j).get(i);
    			sumPull += pullMsgList.get(j).get(i);
    		}
    		sumPushMsg.add(i, sumPush);
    		sumPullMsg.add(i, sumPull);
    		System.out.println("epoch: " + i + "\tsumPushMsg: " + sumPush + "\tsumPullMsg: " + sumPull);
    		sumPushAll += sumPush;
    		sumPullAll += sumPull;
    	}
    	
    	out.print(sumPushAll + "\t\t" + sumPullAll + "");      	
    }
    
    public void addToReport() {    	
    	out.print(numOfEpochsToConverge + "\t\t\t" + stdDeviationConvergence + "\t\t");
    }
    
    public void printStarting() {
    	if(!startingPrinted) {
    		out.print("" + setofAggregatorsON.size() + "\t\t\t");
    		startingPrinted = true;
    	}    	
    }
    
    public void printOut() {
    	out.println("");
    	out.flush();
    	out.close();
    }
    */

}
