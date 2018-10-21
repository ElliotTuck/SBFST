package com.github.elliottuck.sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;

/**
 * Main class.
 */
public class App {
    public static void main(String[] args) {
	MutableFst fst = new MutableFst();
	fst.useStateSymbols();
	
	MutableState startState = fst.newStartState("<start>");
	
	fst.newState("</s>").setFinalWeight(0.0);
	
	int symbolId = fst.getInputSymbols().getOrAdd("<eps>");
	fst.getOutputSymbols().getOrAdd("<eps>");
	
	fst.addArc("state1", "inA", "outA", "state2", 1.0);
	fst.addArc(startState, "inC", "outD", fst.getOrNewState("state3"), 123.0);

	Convert.export(fst, "src/main/resources/test");
    }
}
