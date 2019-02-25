package sbfst;

import com.github.steveash.jopenfst.*;
import java.util.*;

public class Utils {

	public static final String DELIMITER = ",";

	public static final String UNUSED_SYMBOL = "*";

	/**
	 * Determine if the given syntactic monoid is aperiodic or not.
	 * Indicate aperiodicity by returning -1, otherwise return the
	 * period.
	 * @param sm The syntactic monoid to check.
	 * @return -1 if sm is aperiodic, otherwise the period.
	 */
	public static int isAperiodic(Fst sm) {
		// get a map from state to a shortest sequence of input symbols
		// that can be used to represent it
		Map<State, String> shortestStateLabels = getShortestStateLabels(sm);

		// associate each state label with a unique integer index
		Map<String, Integer> stateLabelIndicies = new HashMap<>();
		int index = 0;
		for (String label : shortestStateLabels.values())
			stateLabelIndicies.put(label, index++);

		// get the matrix representation of the syntactic monoid
		int[][] smMatrix = getSMMatrix(sm, stateLabelIndicies, 
			shortestStateLabels);

		// check the matrix for aperiodicity
		Map<Integer, Integer> symbolIndicies = new HashMap<>();
		List<Integer> periods = new ArrayList<>();
		for (int i = 0; i < sm.getStateCount(); i++) {
			symbolIndicies.clear();
			int prev = i;
			for (index = 0; index < sm.getStateCount(); index++) {
				symbolIndicies.put(prev, index);
				int next = smMatrix[i][prev];
				if (next == prev) {
					break;
				}
				if (symbolIndicies.containsKey(next)) {
					// we have seen a symbol we already saw before
					// and it wasn't the previous symbol
					periods.add(index - symbolIndicies.get(next) + 1);
					break;
				}
				prev = next;
			}
		}
		return periods.isEmpty() ? -1 : Collections.max(periods);
	}

	/**
	 * Given a syntactic monoid and the mapping from state labels to
	 * associated index values, as well as the mapping from states to
	 * their shortest input string representations, return a 2d int
	 * array that represents the syntactic monoid.
	 * @param sm The syntactic monoid.
	 * @param labelIndicies The mapping from state labels to their
	                        associated index values.
	 * @param stateLabels The mapping from states to their shortest
	 *                    labels.
	 * @return A 2d string array representation of the SM from which
	 *         stateLabels was obtained.
	 */
	private static int[][] getSMMatrix(Fst sm, 
		Map<String, Integer> labelIndicies,
		Map<State, String> stateLabels) {
		// the inverted input symbol table of the syntactic monoid
		SymbolTable.InvertedSymbolTable rSTable = 
			sm.getInputSymbols().invert();
		// the reverse map from indicies to labels
		Map<Integer, String> rLabelIndicies = new HashMap<>();
		for(Map.Entry<String, Integer> entry : labelIndicies.entrySet())
		    rLabelIndicies.put(entry.getValue(), entry.getKey());
		// the reverse map from labels to states
		Map<String, State> rStateLabels = new HashMap<>();
		for(Map.Entry<State, String> entry : stateLabels.entrySet())
		    rStateLabels.put(entry.getValue(), entry.getKey());
		// the matrix representation of the syntactic monoid
		int[][] ans = new int[sm.getStateCount()][sm.getStateCount()];

		// populate the table
		for (int i = 0; i < sm.getStateCount(); i++) {
			for (int j = 0; j < sm.getStateCount(); j++) {
				State columnState = rStateLabels.get(rLabelIndicies.get(j));
				String rowLabel = rLabelIndicies.get(i);
				String[] arcSymbolSequence = rowLabel.split(DELIMITER);
				State entryState = columnState;
				for (String s : arcSymbolSequence)
					for (Arc arc : entryState.getArcs())
						if (rSTable.keyForId(arc.getIlabel()).equals(s))
							entryState = arc.getNextState();
				String entryLabel = stateLabels.get(entryState);
				int entry = labelIndicies.get(entryLabel);
				ans[i][j] = entry;
			}
		}
		return ans;
	}

	/**
	 * Given a syntactic monoid, find the shortest path from the start 
	 * state to each other state (where distance is computed assuming 
	 * each edge has the same weight). Return a map from state to a 
	 * shortest label that could be used to represent it (where labels 
	 * are constructed as comma-separated lists of the input symbols 
	 * along the shortest path from the root to the state).
	 * @param sm The syntactic monoid to operate on.
	 * @return A map from state to (one of) its "shortest state labels".
	 */
	public static Map<State, String> getShortestStateLabels(Fst sm) {
		// the inverted input symbol table of the syntactic monoid
		SymbolTable.InvertedSymbolTable rSTable = 
			sm.getInputSymbols().invert();
		// the map to return, with the empty string as the 
		// initial label for start state
		Map<State, String> ans = new HashMap<>();
		ans.put(sm.getStartState(), Fst.EPS);

		/* perform a breadth-first search starting at the start state
		   to discover the shortest paths to each state */

		// map to keep track of whether a given state is undiscovered,
		// discovered, or processed (initially all states undiscovered)
		Map<State, StateState> stateStates = new HashMap<>();
		for (int i = 0; i < sm.getStateCount(); i++)
			stateStates.put(sm.getState(i), StateState.UNDISCOVERED);
		// queue of states to visit (initially add the start state)
		List<State> queue = new ArrayList<>();
		queue.add(sm.getStartState());
		stateStates.put(sm.getStartState(), StateState.DISCOVERED);
		// keep checking states until they have all been processed
		while (!queue.isEmpty()) {
			State curState = queue.remove(0);
			for (Arc arc : curState.getArcs()) {
				State potentialNewState = arc.getNextState();
				if (stateStates.get(potentialNewState) == 
					StateState.UNDISCOVERED) {   // discovered new state
					String newLabel = ans.get(curState) + DELIMITER + 
						rSTable.keyForId(arc.getIlabel());
					ans.put(potentialNewState, newLabel);
					stateStates.put(potentialNewState,
						StateState.DISCOVERED);
					queue.add(potentialNewState);
				}
			}
			stateStates.put(curState, StateState.PROCESSED);
		}
		return ans;
	}

	// enumeration of the possible states a given state can be in
	// during a breadth-first search
	enum StateState {
		UNDISCOVERED, DISCOVERED, PROCESSED;
	}

	/**
	 * Given a complete and deterministic FST, return its syntacic
	 * monoid.
	 * @param originalFst The input FST.
	 * @return The syntactic monoid.
	 */
	public static Fst getSM(MutableFst originalFst) {
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
	      return synMonoid;
	}

	/**
	 * Get the pair graph of a given DFA and subsets of states. Note that
	 * a DFA is represented here as an FST with identical input/output
	 * labels for a given edge and equal edge weights throughout the graph.
	 * Also note that this operation assumes that no state is labeled with
	 * the symbol '*'.
	 * TODO: come up with better system than assuming that '*' is an unused
	 * state symbol (e.g. using concatenation of all existing state symbols
	 * as the unused symbol)
	 * @param fst The input FST.
	 * @param q1 The first subset of states.
	 * @param q2 The second subset of states.
	 * @return The pair graph of dfa.
	 */
	public static Fst getPairGraph(Fst dfa, Set<State> q1, Set<State> q2) {
		// TODO: check that the given fst is in fact a DFA
		// TODO: check that q1/q2 are actually subsets of dfa's states

		MutableFst pairGraph = new MutableFst();

		// create the states of the pair graph
		SymbolTable stateSymbolTable = dfa.getStateSymbols();
		pairGraph.useStateSymbols();
		for (State p : q1) {
			for (State q : q2) {
				String pSymbol = stateSymbolTable.invert().keyForId(p.getId());
				String qSymbol = stateSymbolTable.invert().keyForId(q.getId());
				String newStateSymbol = pSymbol + DELIMITER + qSymbol;
				pairGraph.addState(new MutableState(), newStateSymbol);
			}
		}
		for (State p : q1) {
			String pSymbol = stateSymbolTable.invert().keyForId(p.getId());
			String newStateSymbol = pSymbol + UNUSED_SYMBOL;
			pairGraph.addState(new MutableState(), newStateSymbol);
		}
		for (State q : q2) {
			String qSymbol = stateSymbolTable.invert().keyForId(q.getId());
			String newStateSymbol = UNUSED_SYMBOL + qSymbol;
			pairGraph.addState(new MutableState(), newStateSymbol);
		}

		// create the edges of the pair graph


		return pairGraph;
	}

	/**
	 * The delta_i transition function as described in Kim, McNaughton, McCloskey
	 * 1991 (A polynomial time algorithm for the local...).
	 * @param dfa The DFA to use as a basis for looking up transitions.
	 * @param q_i A subset of states from dfa.
	 * @param p The state symbol for a state from q_i.
	 * @param a The transition symbol from the alphabet of dfa.
	 * @return The state symbol for state q resulting from the transition 
	 * delta(p, a) if q is an element of q_i, otherwise the unused symbol '*'.
	 */
	public static String deltaI(Fst dfa, Set<State> q_i, String p, String a) {
		SymbolTable.InvertedSymbolTable inverseStateSymbols = dfa.getStateSymbols().invert();
		SymbolTable.InvertedSymbolTable inverseInputSymbols = dfa.getInputSymbols().invert();
		// get the state with label p
		State stateP = dfa.getState(p);
		// look through stateP's arcs to find one with label a
		for (Arc arc : stateP.getArcs()) {
			if (inverseInputSymbols.keyForId(arc.getIlabel()).equals(a)) {
				State stateQ = arc.getNextState();
				if (q_i.contains(stateQ)) {
					return inverseStateSymbols.keyForId(stateQ.getId());
				} else {
					return UNUSED_SYMBOL;
				}
			}
		}
		return UNUSED_SYMBOL;
	}


}