package parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Parser {
	
	private BufferedReader in;
	private PrintWriter out;
	
	public Parser() {
		try {
			in = new BufferedReader(new FileReader("report2.txt"));
			out = new PrintWriter(new BufferedWriter(new FileWriter("report3.txt", false)));			
		} catch (FileNotFoundException e) {			
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String args[]) {
		Parser parser = new Parser();
		parser.replace();
		parser.printOut();
	}
	
	public void replace() {
		try {
			String line;
			int i = 0;
			while((line=in.readLine()) != null) {
				if(i != 0) {
					String newLine = line.replace("\t", ",");
					String newLine1 = newLine.replace(",,,", ",");
					String newLine2 = newLine1.replace(",,", ",");
					out.println(newLine2);
				}
				else {
					out.println(line);
				}
				i++;
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
	public void printOut() {
		out.flush();
		out.close();
		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
