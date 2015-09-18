package parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class EventFileGenerator {
	
	private BufferedReader in;
	private PrintWriter out;
	private String filenameIN;
	private String filenameOUT;
	
	public EventFileGenerator(String filenameIN, String filenameOUT) {
		this.filenameIN = filenameIN;
		this.filenameOUT = filenameOUT;
	}
	
	public boolean openFile() {
		boolean flag = true;
		try {
			in = new BufferedReader(new FileReader(filenameIN));
		} catch (FileNotFoundException e) {
			//flag = false;
			//e.printStackTrace();
			PrintWriter out1 = new PrintWriter(new BufferedWriter(new FileWriter(filenameIN, false)));
			out1.flush();
			out1.close();
			in = new BufferedReader(new FileReader(filenameIN));
		} finally {
			return flag;
		}
	}
	
	public boolean readFile(ArrayList<String> list) {	
		boolean flag = true;
		try {
			String line;
			while((line=in.readLine()) != null) {
				list.add(line);
			}
		} catch (IOException e) {
			flag = false;
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return flag;
		}
	}
	
	public boolean writeToFile(ArrayList<String> list) {
		boolean flag = true;
		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(filenameOUT, false)));
			for(int i = 0; i < list.size(); i++) {
				out.println(list.get(i));
			}
		} catch (IOException e) {
			flag = false;
			e.printStackTrace();
		} finally {
			out.flush();
			out.close();
			return flag;
		}
		
	}
	
	public boolean changePeers(int lineNumber, int peerNum) {
		if(lineNumber < 0 || peerNum < 0) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				String line = lineList.remove(lineNumber);
				String[] tokens = line.split("\t");
				tokens[0] = "" + peerNum;
				StringBuilder sb = new StringBuilder();
				sb.append(tokens[0] + "\t" + tokens[1] + "\t" + tokens[2]);
				lineList.add(lineNumber, sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}			
	}
	
	public boolean changePeers(int lineNumber, int startPeerNum, int endPeerNum) {
		if(lineNumber < 0 || startPeerNum < 0 || endPeerNum < 0 || startPeerNum >= endPeerNum) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				String line = lineList.remove(lineNumber);
				String[] tokens = line.split("\t");
				tokens[0] = "" + startPeerNum + "-" + endPeerNum;
				StringBuilder sb = new StringBuilder();
				sb.append(tokens[0] + "\t" + tokens[1] + "\t" + tokens[2]);
				lineList.add(lineNumber, sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	public boolean changeTime(int lineNumber, String newTime) {
		if(lineNumber < 0) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				String line = lineList.remove(lineNumber);
				String[] tokens = line.split("\t");
				tokens[1] = "" + newTime;
				StringBuilder sb = new StringBuilder();
				sb.append(tokens[0] + "\t" + tokens[1] + "\t" + tokens[2]);
				lineList.add(lineNumber, sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	public boolean changeMethod(int lineNumber, String fullMethodName) {
		if(lineNumber < 0) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				String line = lineList.remove(lineNumber);
				String[] tokens = line.split("\t");
				tokens[2] = "" + fullMethodName;
				StringBuilder sb = new StringBuilder();
				sb.append(tokens[0] + "\t" + tokens[1] + "\t" + tokens[2]);
				lineList.add(lineNumber, sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	
	public boolean insertLine(int lineNumber, int peerNum, String time, String fullMethodName) {
		if(lineNumber < 0 || peerNum < 0) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				StringBuilder sb = new StringBuilder();
				sb.append(peerNum + "\t" + time + "\t" + fullMethodName);
				lineList.add(lineNumber, sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	
	public boolean insertLine(int lineNumber, int startPeerNum, int endPeerNum, String time, String fullMethodName) {
		if(lineNumber < 0 || startPeerNum < 0 || endPeerNum < 0 || startPeerNum >= endPeerNum) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				StringBuilder sb = new StringBuilder();
				sb.append(startPeerNum + "-" + endPeerNum + "\t" + time + "\t" + fullMethodName);
				lineList.add(lineNumber, sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	
	public boolean removeLine(int lineNumber) {
		if(lineNumber < 0) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {
				if(lineList.size() <= lineNumber) {
					return false;
				}
				lineList.remove(lineNumber);
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	public boolean addLineEnd(int peerNum, String time, String fullMethodName) {
		if(peerNum < 0) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {				
				StringBuilder sb = new StringBuilder();
				sb.append(peerNum + "\t" + time + "\t" + fullMethodName);
				lineList.add(sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	public boolean addLineEnd(int startPeerNum, int endPeerNum, String time, String fullMethodName) {
		if(startPeerNum < 0 || endPeerNum < 0 || startPeerNum >= endPeerNum) {
			return false;
		}
		ArrayList<String> lineList = new ArrayList<String>();
		if(openFile()) {
			if(readFile(lineList)) {				
				StringBuilder sb = new StringBuilder();
				sb.append(startPeerNum + "-" + endPeerNum + "\t" + time + "\t" + fullMethodName);
				lineList.add(sb.toString());
				if(writeToFile(lineList)) {
					return true;
				}
				else {
					return false;
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
	
	public static void numOfAggregatorsExperiment(String baseName) {
		final File scenariosDir = new File("scenarios");
    	scenariosDir.mkdir();
    	
    	final File numOfAggregatorsDir3 = new File("scenarios/numOfAggregators");
    	numOfAggregatorsDir3.mkdir();
    	
    	String path = "scenarios/numOfAggregators/";
    	int numOfAggregators = 19;
    	
		for(int i = 0; i < 75; i++) {
			EventFileGenerator efgen = new EventFileGenerator(path + baseName + i + ".txt", path + baseName + i + ".txt");
			efgen.addLineEnd(0, numOfAggregators, "3e3", "peerlets.DIAS.activate()");
			System.out.println("Scenario file created at: " + "scenarios/numOfAggregators/" + baseName + i + ".txt");
			numOfAggregators+=20;
		}
		
	}
		
	public static void main(String args[]) {
		numOfAggregatorsExperiment("dias_delays");
	}

}
