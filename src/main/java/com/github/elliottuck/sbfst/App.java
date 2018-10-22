package com.github.elliottuck.sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;
import java.io.File;
import java.util.ArrayList;
import java.util.*;
import java.util.HashMap;


/**
 * Main class.
 */
public class App {
    public static void main(String[] args) {
      File inputFile = new File("src/main/java/com/github/elliottuck/sbfst/tomita2.fst.txt");
      Convert.setRegexToSplitOn("\\s+");
      MutableFst originalFst = Convert.importFst(inputFile);

      HashMap<Integer, MutableState> idToStateOrig = new HashMap<Integer, MutableState>();

      String newStateName = "";
      for (int i = 0; i < originalFst.getStateCount(); i++){
        int id = originalFst.getState(i).getId();
        idToStateOrig.put(id, originalFst.getState(i));
        newStateName = newStateName.concat(Integer.toString(id));
      }
      // will need to generalize this stuff, need to extract this info from the fst object
      //MutableSymbolTable symbolTable = new MutableSymbolTable();
      //symbolTable.put("q", 0);
      //symbolTable.put("r", 1);
      //symbolTable.put("p", 2);
      //originalFst.useStateSymbols(symbolTable);
      //WriteableSymbolTable states = originalFst.getStateSymbols();

      MutableFst synMonoid = new MutableFst();

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

      HashMap<Integer, String> idToLabel = new HashMap<Integer, String>();
      idToLabel.put(newState.getId(), newStateName);



      //for (int i = 0; i < originalFst.getStateCount(); i++){
      //  currentStates.add(originalFst.getState(i));
      //}

      statesToProcess.add(newState);
      synMonoid.setStart(newState);
      // outline of algorithm to implement
      while(statesToProcess.size() != 0){
          MutableState stateToProcess = statesToProcess.get(0);
          //System.out.println(stateToProcess.getId());
          String label = idToLabel.get(stateToProcess.getId());
          System.out.println(label);
          ArrayList<String> newStates = new ArrayList<String>();
          currentStates.clear();
          for (int i = 0; i < label.length(); i++){
            String s = Character.toString(label.charAt(i));
            currentStates.add(idToStateOrig.get(Integer.parseInt(s)));
          }
          //System.out.println("Current States: " + currentStates);
          for (MutableState curState: currentStates){
            List<MutableArc> arcs = curState.getArcs();
            //System.out.println(arcs.size());
            if (newStates.size() != arcs.size()){
              for (int i = 0; i < arcs.size(); i++){
                newStates.add("");
              }
            }

            for (int i = 0; i < arcs.size(); i++){
              MutableArc arc = arcs.get(i);
              String creatingState = newStates.get(i).concat(Integer.toString(arc.getNextState().getId()));
              newStates.remove(i);
              newStates.add(i, creatingState);
              // follow a transition, see where it goes, add that state to nextStates
            }
          }
          processedStates.add(stateToProcess);
          statesToProcess.remove(stateToProcess);
          for (String nStateName: newStates){
            //System.out.println("Found State: " + nStateName);
            MutableState nState = synMonoid.getOrNewState(nStateName);
            boolean foundProcessed = false;
            boolean foundToProcess = false;
            for (MutableState s: processedStates){
              if (idToLabel.get(s.getId()).equals(nStateName)){
                foundProcessed = true;
                //System.out.println("Is processed");
                break;
              }
            }
            if (!foundProcessed){
              for (MutableState s: statesToProcess){
                if (idToLabel.get(s.getId()).equals(nStateName)){
                  //System.out.println("Is already in statesToProcess");
                  foundToProcess = true;
                  break;
                }
              }
            }
            if (!foundProcessed && !foundToProcess){
              statesToProcess.add(nState);
              idToLabel.put(nState.getId(), nStateName);
            }
          }
          // create a new state based on the names of the states we added to nextStates
          // add this state is not in processedStates, add to statesToProcess if it's not already there
          // add this state to our synMonoid

      }
      System.out.println(synMonoid);

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
