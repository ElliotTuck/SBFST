package com.github.elliottuck.sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;
import java.io.File;
import java.util.ArrayList;
import java.util.*;


/**
 * Main class.
 */
public class App {
    public static void main(String[] args) {
      File inputFile = new File("src/main/java/com/github/elliottuck/sbfst/tomita2.fst.txt");
      Convert.setRegexToSplitOn("\\s+");
      MutableFst originalFst = Convert.importFst(inputFile);

      // will need to generalize this stuff, need to extract this info from the fst object
      MutableSymbolTable symbolTable = new MutableSymbolTable();
      symbolTable.put("q", 0);
      symbolTable.put("r", 1);
      symbolTable.put("p", 2);
      originalFst.useStateSymbols(symbolTable);
      WriteableSymbolTable states = originalFst.getStateSymbols();

      MutableFst synMonoid = new MutableFst();

      String newStateName = "qrp";

      synMonoid.useStateSymbols();
      //((MutableSymbolTable)synMonoid.getStateSymbols()).getOrAdd(newStateName);
      //System.out.println((MutableSymbolTable)synMonoid.getStateSymbols());

      MutableState newState = synMonoid.getOrNewState(newStateName);

      // currentStates is the list of the states (in order) we are testing the outgoing transitions of (from originalFst)
      ArrayList<MutableState> currentStates = new ArrayList<MutableState>();

      // nextStates is where we build up the next state we will see (could be a state we have already seen)
      // based on the states from original Fst
      ArrayList<MutableState> nextStates = new ArrayList<MutableState>();

      // statesToProcess is the list of states (for new fst) we haven't processed yet/are currently processing
      ArrayList<MutableState> statesToProcess = new ArrayList<MutableState>();

      // processedStates is the list of states (for new fst) that have been completely processed
      ArrayList<MutableState> processedStates = new ArrayList<MutableState>();

      for (int i = 0; i < originalFst.getStateCount(); i++){
        currentStates.add(originalFst.getState(i));
      }

      statesToProcess.add(newState);

      // outline of algorithm to implement
      //while(statesToProcess.size() != 0){
      //    for (MutableState curState: currentStates){
      //      List<MutableArc> arcs = curState.getArcs();
      //      for (MutableArc arc: arcs){
              // follow a transition, see where it goes, add that state to nextStates
      //      }
      //      statesToProcess.remove(curState);
      //    }
          // create a new state based on the names of the states we added to nextStates
          // add this state is not in processedStates, add to statesToProcess if it's not already there
          // add this state to our synMonoid
      //}

// old testing stuff
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
