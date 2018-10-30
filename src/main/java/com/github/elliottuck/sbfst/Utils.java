package com.github.elliottuck.sbfst;

import com.github.steveash.jopenfst.Fst;
import com.github.steveash.jopenfst.State;
import com.github.steveash.jopenfst.Arc;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class Utils {

	/**
	 * Determine if the given syntactic monoid is aperiodic or not.
	 * @param sm The syntactic monoid to check.
	 * @return true if fst is aperiodic, false otherwise.
	 */
	public static boolean isAperiodic(Fst sm) {
		// get a map from state to a shortest sequence of input symbols
		// that can be used to represent it
		Map<State, String> shortestStateLabels = getShortestStateLabels(sm);

		return false;   // dummy return to shut up compiler
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
	private static Map<State, String> getShortestStateLabels(Fst sm) {
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
					String newLabel = ans.get(curState) + "," + 
						arc.getIlabel();
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