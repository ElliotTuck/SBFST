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
  /*
  *****************************
  * IMPORTANT!!!! will need to change
  * synMonoid state labels to comma separated
  * list to accomodate 2+ digit state labels
  * add determinization/minimization
  *********************************
  */
    public static void main(String[] args) {
      String fileName = args[0];
      File inputFile = new File(fileName);
      Convert.setRegexToSplitOn("\\s+");
      MutableFst originalFst = Convert.importFst(inputFile);

      // System.out.println(Utils.isAperiodic(originalFst));

      HashMap<Integer, MutableState> idToStateOrig = new HashMap<Integer, MutableState>();

      String newStateName = "";
      for (int i = 0; i < originalFst.getStateCount(); i++){
        int id = originalFst.getState(i).getId();
        idToStateOrig.put(id, originalFst.getState(i));
        newStateName = newStateName.concat(Integer.toString(id) + ",");
      }

      MutableFst synMonoid = new MutableFst();

      synMonoid.useStateSymbols();

      MutableState newState = synMonoid.getOrNewState(newStateName);

      // currentStates is the list of the states (in order) we are testing the outgoing transitions of (from originalFst)
      ArrayList<MutableState> currentStates = new ArrayList<MutableState>();

      // statesToProcess is the list of states (for new fst) we haven't processed yet/are currently processing
      ArrayList<MutableState> statesToProcess = new ArrayList<MutableState>();

      // processedStates is the list of states (for new fst) that have been completely processed
      ArrayList<MutableState> processedStates = new ArrayList<MutableState>();

      HashMap<Integer, String> idToLabel = new HashMap<Integer, String>();
      idToLabel.put(newState.getId(), newStateName);

      statesToProcess.add(newState);
      synMonoid.setStart(newState);
      // outline of algorithm to implement
      while(statesToProcess.size() != 0){
          MutableState stateToProcess = statesToProcess.get(0);
          String label = idToLabel.get(stateToProcess.getId());
          System.out.println(label);
          ArrayList<String> newStates = new ArrayList<String>();
          currentStates.clear();
          for (String s: label.split(",")){
            if(s == null){
              continue;
            } else{
              currentStates.add(idToStateOrig.get(Integer.parseInt(s)));
            }

          }
          //System.out.println("Current States: " + currentStates);
          for (MutableState curState: currentStates){
            System.out.println(curState);
            List<MutableArc> arcs = curState.getArcs();
            //System.out.println(arcs.size());
            if (newStates.size() != arcs.size()){
              for (int i = 0; i < arcs.size(); i++){
                newStates.add("");
              }
            }

            for (int i = 0; i < arcs.size(); i++){
              MutableArc arc = arcs.get(i);
              int pos = arc.getIlabel() - 1;
              //System.out.println("ilabel: " + pos);
              String creatingState = newStates.get(pos).concat(Integer.toString(arc.getNextState().getId()));
              creatingState = creatingState.concat(",");
              newStates.remove(pos);
              newStates.add(pos, creatingState);
              // follow a transition, see where it goes, add that state to nextStates
            }
          }
          processedStates.add(stateToProcess);
          statesToProcess.remove(stateToProcess);
          for (int i = 0; i < newStates.size(); i++){
            String nStateName = newStates.get(i);
            System.out.println("Found State: " + nStateName);

            MutableState nState = synMonoid.getOrNewState(nStateName);

            synMonoid.addArc(stateToProcess, ""+(i+1), ""+(i+1), nState, 0);

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
      Convert.setUseSymbolIdsInText(true);
      int index = fileName.indexOf(".fst");
      String exportFileName = fileName.substring(0, index);
      Convert.export(synMonoid, exportFileName + "SyntacticMonoid");

      int period = Utils.isAperiodic(synMonoid);
      if (period == -1) {
        System.out.println("The input language is aperiodic.");
      } else {
        System.out.println("The input language is periodic with period "
          + period + ".");
      }
    }
}
