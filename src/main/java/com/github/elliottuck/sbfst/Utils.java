package com.github.elliottuck.sbfst;

import com.github.steveash.jopenfst.Fst;
import com.github.steveash.jopenfst.State;
import com.github.steveash.jopenfst.Arc;
import com.github.steveash.jopenfst.SymbolTable;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Utils {

	private static final String DELIMITER = ",";

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

}