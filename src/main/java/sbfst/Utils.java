package sbfst;

import com.github.steveash.jopenfst.*;

import java.util.*;

public class Utils {

    public static final String DELIMITER = ",";

    public static final String UNUSED_SYMBOL = "*";

    public static final String FINAL_SYMBOL = ".";

    /**
     * Determine if the given syntactic monoid is aperiodic or not.
     * Indicate aperiodicity by returning -1, otherwise return the
     * period.
     *
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
     * Determine if the given fst is locally testable or not. For now it is assumed that the input automaton is a dfa,
     * although future versions could be altered to allow for a broader set of inputs.
     *
     * @param dfa The input dfa to test, assumed to be minimized.
     * @return true if dfa is locally testable, false otherwise.
     */
    public static boolean isLocallyTestable(Fst dfa) {
        // check s-locality of dfa's SCCs
        ArrayList<ArrayList<State>> SCCs = getSCCs(dfa);
        for (ArrayList<State> SCC : SCCs) {
            Set<State> component = new HashSet<>(SCC);
            if (!isPairwiseSLocal(dfa, component, component)) {
                return false;
            }
        }

        // TODO: check that graph is TS-local
        return true;   // dummy return
    }

    /**
     * Given a syntactic monoid and the mapping from state labels to
     * associated index values, as well as the mapping from states to
     * their shortest input string representations, return a 2d int
     * array that represents the syntactic monoid.
     *
     * @param sm            The syntactic monoid.
     * @param labelIndicies The mapping from state labels to their
     *                      associated index values.
     * @param stateLabels   The mapping from states to their shortest
     *                      labels.
     * @return A 2d string array representation of the SM from which
     * stateLabels was obtained.
     */
    private static int[][] getSMMatrix(Fst sm,
                                       Map<String, Integer> labelIndicies,
                                       Map<State, String> stateLabels) {
        // the inverted input symbol table of the syntactic monoid
        SymbolTable.InvertedSymbolTable rSTable =
                sm.getInputSymbols().invert();
        // the reverse map from indicies to labels
        Map<Integer, String> rLabelIndicies = new HashMap<>();
        for (Map.Entry<String, Integer> entry : labelIndicies.entrySet())
            rLabelIndicies.put(entry.getValue(), entry.getKey());
        // the reverse map from labels to states
        Map<String, State> rStateLabels = new HashMap<>();
        for (Map.Entry<State, String> entry : stateLabels.entrySet())
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
     *
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
     *
     * @param originalFst The input FST.
     * @return The syntactic monoid.
     */
    public static Fst getSM(MutableFst originalFst) {
        HashMap<Integer, MutableState> idToStateOrig = new HashMap<Integer, MutableState>();

        String newStateName = "";
        for (int i = 0; i < originalFst.getStateCount(); i++) {
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
        while (statesToProcess.size() != 0) {
            MutableState stateToProcess = statesToProcess.get(0);
            String label = idToLabel.get(stateToProcess.getId());
            System.out.println(label);
            ArrayList<String> newStates = new ArrayList<String>();
            currentStates.clear();
            for (String s : label.split(",")) {
                if (s == null) {
                    continue;
                } else {
                    currentStates.add(idToStateOrig.get(Integer.parseInt(s)));
                }

            }
            //System.out.println("Current States: " + currentStates);
            for (MutableState curState : currentStates) {
                System.out.println(curState);
                List<MutableArc> arcs = curState.getArcs();
                //System.out.println(arcs.size());
                if (newStates.size() != arcs.size()) {
                    for (int i = 0; i < arcs.size(); i++) {
                        newStates.add("");
                    }
                }

                for (int i = 0; i < arcs.size(); i++) {
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
            for (int i = 0; i < newStates.size(); i++) {
                String nStateName = newStates.get(i);
                System.out.println("Found State: " + nStateName);

                MutableState nState = synMonoid.getOrNewState(nStateName);

                synMonoid.addArc(stateToProcess, "" + (i + 1), "" + (i + 1), nState, 0);

                boolean foundProcessed = false;
                boolean foundToProcess = false;
                for (MutableState s : processedStates) {
                    if (idToLabel.get(s.getId()).equals(nStateName)) {
                        foundProcessed = true;
                        //System.out.println("Is processed");
                        break;
                    }
                }
                if (!foundProcessed) {
                    for (MutableState s : statesToProcess) {
                        if (idToLabel.get(s.getId()).equals(nStateName)) {
                            //System.out.println("Is already in statesToProcess");
                            foundToProcess = true;
                            break;
                        }
                    }
                }
                if (!foundProcessed && !foundToProcess) {
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
     * Determine if the given components of a state transition graph are pairwise s-local (or just s-local if both
     * components are the same component).
     *
     * @param dfa The dfa to base the s-locality off of.
     * @param m1  The first component.
     * @param m2  The second component.
     * @return true if m1 and m2 are pairwise s-local, false otherwise.
     */
    public static boolean isPairwiseSLocal(Fst dfa, Set<State> m1, Set<State> m2) {
        // construct the pair graph on m1 and m2
        Fst pairGraph = getPairGraph(dfa, m1, m2);

        // check if the pair graph is acyclic
        return isAcyclic(pairGraph);
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
     *
     * @param dfa The input FST.
     * @param q1  The first subset of states.
     * @param q2  The second subset of states.
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
            String newStateSymbol = pSymbol + DELIMITER + UNUSED_SYMBOL;
            pairGraph.addState(new MutableState(), newStateSymbol);
        }
        for (State q : q2) {
            String qSymbol = stateSymbolTable.invert().keyForId(q.getId());
            String newStateSymbol = UNUSED_SYMBOL + DELIMITER + qSymbol;
            pairGraph.addState(new MutableState(), newStateSymbol);
        }

        // create the edges of the pair graph
        for (String stateName : pairGraph.getStateSymbols().symbols()) {
            String[] substateNames = stateName.split(DELIMITER);
            String pName = substateNames[0];
            String qName = substateNames[1];
            if (pName.equals(UNUSED_SYMBOL) || qName.equals(UNUSED_SYMBOL)) {
                continue;
            }
            State p = dfa.getState(pName);
            State q = dfa.getState(qName);

            if (!p.equals(q) && q1.contains(p) && q2.contains(q)) {
                for (String a : dfa.getInputSymbols().symbols()) {
                    String r = deltaI(dfa, q1, pName, a);
                    String s = deltaI(dfa, q2, qName, a);
                    if (r != UNUSED_SYMBOL || s != UNUSED_SYMBOL) { // valid transition in pair graph
                        pairGraph.addArc(stateName, a, a, r + DELIMITER + s, 0);
                    }
                }
            }
        }

        return pairGraph;
    }

    /**
     * The delta_i transition function as described in Kim, McNaughton, McCloskey
     * 1991 (A polynomial time algorithm for the local...).
     *
     * @param dfa The DFA to use as a basis for looking up transitions.
     * @param q_i A subset of states from dfa.
     * @param p   The state symbol for a state from q_i.
     * @param a   The transition symbol from the alphabet of dfa.
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

    /**
     * Determine if the given dfa is acyclic.
     *
     * @param dfa The graph to check.
     * @return true if dfa is acyclic, false otherwise.
     */
    public static boolean isAcyclic(Fst dfa) {
        // perform DFS and check for back edges
        StateState[] stateStates = new StateState[dfa.getStateCount()];
        for (int i = 0; i < dfa.getStateCount(); i++) {
            stateStates[i] = StateState.UNDISCOVERED;
        }
        for (int i = 0; i < dfa.getStateCount(); i++) {
            if (stateStates[i] == StateState.UNDISCOVERED) {
                if (!isAcyclicHelper(dfa.getState(i), stateStates)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isAcyclicHelper(State s, StateState[] stateStates) {
        stateStates[s.getId()] = StateState.DISCOVERED;
        for (Arc a : s.getArcs()) {
            System.out.println("next state: " + a.getNextState().getId());
            State t = a.getNextState();
            if (stateStates[t.getId()] == StateState.DISCOVERED) {   // found a back edge, thus there is a cycle
                return false;
            } else if (stateStates[t.getId()] == StateState.UNDISCOVERED) {
                if (!isAcyclicHelper(t, stateStates)) {
                    return false;
                }
            }
        }
        stateStates[s.getId()] = StateState.PROCESSED;
        return true;
    }

    /**
     * Find all maximal strongly connected components of the given dfa
     * using Kosaraju's algorithm
     *
     * @param dfa The DFA to find the SCCs for
     * @return An arrayList containing lists of states, where each
     * list contains all of the states from a single SCC
     */
    public static ArrayList<ArrayList<State>> getSCCs(Fst dfa) {
        // stack to hold order that states were finished being
        // processed in DFS
        Stack stack = new Stack();

        // arraylist of SCCs that we will be building up to return
        ArrayList<ArrayList<State>> SCCs = new ArrayList<>();

        // initialize visited array
        boolean[] visited = new boolean[dfa.getStateCount()];
        for (int i = 0; i < dfa.getStateCount(); i++) {
            visited[i] = false;
        }

        // while there are states that still haven't been seen by DFS,
        // start a DFS from unvisited state
        for (int i = 0; i < dfa.getStateCount(); i++) {
            if (visited[i] == false) {
                stack = orderProcessed(dfa.getState(i), visited, stack);
            }

        }

        // transpose dfa
        Fst transposed = Reverse2.reverse(dfa);

        // reset visited array to do dfs from each state of transposed dfa
        for (int i = 0; i < dfa.getStateCount(); i++) {
            visited[i] = false;
        }

        // Now process all states in order defined by Stack
        while (stack.empty() == false) {
            // Pop a vertex from stack
            State state = (State) stack.pop();
            State tState = null;
            for (int i = 0; i < transposed.getStateCount(); i++) {
                tState = transposed.getState(i);
                if (tState.getId() == state.getId()) {
                    break;
                }
            }
            ArrayList<State> result = new ArrayList<>();

            // Print Strongly connected component of the popped vertex
            if (visited[state.getId()] == false) {
                SCCs.add(DFS(tState, visited, result));
            }

            // states returned in SCC were from the transposed dfa
            // this will exchanged the states from the transposed dfa
            // for their original state from the original dfa
            for (ArrayList<State> scc : SCCs) {
                for (int i = 0; i < scc.size(); i++) {
                    scc.set(i, dfa.getState(scc.get(i).getId()));
                }
            }
        }

        return SCCs;
    }

    /**
     * Do a DFS of the given dfa starting at state "start"
     *
     * @param startState The state to start the DFS from
     * @param visited    array to keep track of the states we've seen so far
     * @param result     arraylist to add found states to
     * @return An arrayList containing the states reached from the DFA,
     * in order of discovery
     */
    public static ArrayList<State> DFS(State startState, boolean visited[], ArrayList<State> result) {
        // Mark the current node as visited and print it
        visited[startState.getId()] = true;
        result.add(startState);

        // Recur for all the vertices adjacent to this vertex
        for (Arc arc : startState.getArcs()) {
            State adjState = arc.getNextState();
            System.out.println("State " + startState.getId() + " is connected to " + adjState.getId());
            if (!visited[adjState.getId()]) {
                DFS(adjState, visited, result);
            }
        }
        return result;
    }


    /**
     * Do a DFS of the given dfa starting at state "start",
     * but we are adding states to our result stack only
     * once all states reachable from that state are processed
     *
     * @param startState The state to start the DFS from
     * @param visited    array to keep track of the states we've seen so far
     * @param stack      stack of states in order of when processing of the reachable
     *                   states from that state is finished
     * @return An arrayList containing the states reached from the DFA,
     * in order of discovery
     */
    private static Stack orderProcessed(State startState, boolean visited[], Stack stack) {

        // Mark the current state as visited
        visited[startState.getId()] = true;

        // Recur for all the states adjacent to this vertex
        for (Arc arc : startState.getArcs()) {
            State adjState = arc.getNextState();
            if (!visited[adjState.getId()]) {
                stack = orderProcessed(adjState, visited, stack);
            }
        }

        // All vertices reachable from v are processed by now,
        // push state to Stack
        stack.push(startState);
        return stack;
    }

    /**
     * find the ancestors of each SCC in the given array of SCCs
     *
     * @param SCCs SCCs to look for ancestors of
     * @param dfa  dfa the states belong to
     * @return An arrayList of arrayLists of the ancestors of a given SCC
     * note: the i-th list corresponds to the ancestors of the i-th SCC
     * listed in the SCCs array (the actual values in the ancestors list
     * correspond to the index of the ancestor in the SCCs array)
     */
    public static ArrayList<ArrayList<Integer>> getAncestors(ArrayList<ArrayList<State>> SCCs, Fst dfa) {
        ArrayList<ArrayList<Integer>> solution = new ArrayList<>();

        for (int i = 0; i < SCCs.size(); i++) {
            ArrayList<Integer> ancestors = new ArrayList<>();
            for (int j = 0; j < SCCs.size(); j++) {
                if (i == j) {
                    continue;
                }
                if (isPath(SCCs.get(j).get(0), SCCs.get(i).get(0), dfa)) {
                    ancestors.add(j);
                }
            }
            solution.add(ancestors);
        }

        return solution;
    }

    /**
     * Do a bidirectional BFS from startState and endState looking for
     * a collision in the search (return true if found)
     *
     * @param startState the state to start the search from
     * @param endState   the state to look for a path to
     * @return true if there exists a path from startState to endState,
     * false otherwise
     */
    public static boolean isPath(State startState, State endState, Fst dfa) {
        Fst transposed = Reverse2.reverse(dfa);

        int endId = endState.getId();
        State endStateTransposed = transposed.getState(endId);

        boolean[] sIsVisited = new boolean[dfa.getStateCount()];
        boolean[] eIsVisited = new boolean[dfa.getStateCount()];

        ArrayList<State> sQueue = new ArrayList<>();
        ArrayList<State> eQueue = new ArrayList<>();

        int collision = -1;

        sQueue.add(startState);
        eQueue.add(endStateTransposed);

        sIsVisited[startState.getId()] = true;
        eIsVisited[endStateTransposed.getId()] = true;

        while ((sQueue.size() != 0) && (eQueue.size() != 0)) {
            BFS(sQueue, sIsVisited, dfa);
            BFS(eQueue, eIsVisited, transposed);

            collision = isCollision(sIsVisited, eIsVisited);

            if (collision != -1) {
                return true;
            }

        }
        return false;
    }

    /**
     * Do one step of a BFS (add the not yet visited neighbors of the
     * first state on our queue to our queue and mark them as visited)
     *
     * @param queue     the current BFS queue
     * @param isVisited boolean array representing the states that have been
     *                  visited so far
     * @param dfa       dfa we are traversing
     * @return void
     */
    private static void BFS(ArrayList<State> queue, boolean[] isVisited, Fst dfa) {
        State state = queue.get(0);
        queue.remove(state);

        for (Arc arc : state.getArcs()) {
            State adjState = arc.getNextState();
            if (!isVisited[adjState.getId()]) {
                queue.add(adjState);
                isVisited[adjState.getId()] = true;
            }
        }
    }

    /**
     * Check if there is an index from array1 == array2 == 1
     *
     * @param array1 first array to check
     * @param array2 array to check against
     * @return returns index of collision if occurred, -1 otherwise
     */
    private static int isCollision(boolean[] array1, boolean[] array2) {
        for (int i = 0; i < array1.length; i++) {
            if (array1[i] == true && array2[i] == true) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if an SCC has any descendant SCCs in a given DFA.
     *
     * @param scc The SCC to check for descendant SCCs
     * @param dfa The DFA
     * @return true if scc has descendants, false otherwise
     * @throws Exception if scc is not an SCC
     */
    public static boolean hasDescendants(ArrayList<State> scc, Fst dfa) {
        // get the SCCs of dfa
        ArrayList<ArrayList<State>> SCCs = getSCCs(dfa);

//        // TODO: make sure scc is an actual SCC
//        if (!SCCs.contains(scc)) {
//            throw new Exception("Given component is not an SCC!");
//        }

        // get all ancestors SCCs of all SCCs in dfa
        Set<Integer> allAncestors = new HashSet<>();
        ArrayList<ArrayList<Integer>> ancestors = getAncestors(SCCs, dfa);
        for (ArrayList<Integer> ancestorIndices : ancestors) {
            for (Integer i : ancestorIndices) {
                allAncestors.add(i);
            }
        }

        // check if scc is contained in the set of all ancestors
        for (Integer i : allAncestors) {
            if (scc.equals(SCCs.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a given component is reachable from a given state in a given DFA.
     * @param s The given state
     * @param comp The given component
     * @param dfa The given DFA
     * @return true if comp is reachable from s, false otherwise
     */
    public static boolean componentIsReachable(State s, ArrayList<State> comp, Fst dfa) {
        for (State q : comp) {
            if (isPath(s, q, dfa)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the component m0 (defined in Part IV of Kim, McNaughton, McCloskey 1991) given a DFA and an SCC in the DFA.
     * @param scc The given scc
     * @param dfa The given DFA
     * @return m0
     */
    public static ArrayList<State> getM0(ArrayList<State> scc, Fst dfa) {
        ArrayList<State> m0 = new ArrayList<>();
        for (int i = 0; i < dfa.getStateCount(); i++) {
            State s = dfa.getState(i);
            if (componentIsReachable(s, scc, dfa)) {
                m0.add(s);
            }
        }
        return m0;
    }

}
