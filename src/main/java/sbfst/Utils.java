package sbfst;

import com.github.steveash.jopenfst.*;

import java.util.*;

public class Utils {

    // TODO: comment about this
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

        // check that graph is TS-local
        while (true) {
            ArrayList<ArrayList<State>> graphSCCs = getSCCs(dfa);
            if (graphSCCs.size() == 0) {
                return true;
            }
            for (ArrayList<State> graphSCC : graphSCCs) {
                // TODO: comment about always having an SCC with no descendants
                if (hasDescendants(graphSCC, dfa)) {
                    continue;
                }
                ArrayList<State> m0 = getM0(graphSCC, dfa);
                Fst pairGraph = getPairGraph(dfa, new HashSet<>(m0), new HashSet<>(graphSCC));
                ((MutableFst) pairGraph).setStart(((MutableFst) pairGraph).getState(0));
                if (isTSLocalWRT(graphSCC, dfa)) {
                    // if about to delete all the state, just return true (because there will be no SCCs in the next round)
                    if (dfa.getStateCount() == graphSCC.size()) {
                        return true;
                    }
                    // JOpenFST prevents deleting start state, so change the start state to a state that isn't going to be deleted
                    if (graphSCC.contains(dfa.getStartState())) {
                        for (int i = 0; i < dfa.getStateCount(); i++) {
                            State s = dfa.getState(i);
                            if (!graphSCC.contains(s)) {
                                ((MutableFst) dfa).setStart((MutableState) s);
                                break;
                            }
                        }
                    }
                    ((MutableFst) dfa).deleteStates((Collection) graphSCC);
                    break;
                } else {
                    return false;
                }
            }
        }
    }

    /**
     * Determine if the given FST is piecewise testable or not. For now it is assumed that the input automaton is a
     * minimized DFA although future versions could be altered to allows fro a broader set of inputs.
     * @param dfa The input DFA to test, assumed to be minimized.
     * @return true if dfa is piecewise testable, false otherwise
     */
    public static boolean isPiecewiseTestable(Fst dfa) {
        // NOTE: it seemed wrong to care about self-loops when checking acyclicity here
//        if (!isAcyclic(dfa)) {
//            return false;
//        }
        if (!isAcyclic(withoutSelfLoops(dfa))) {
            return false;
        }
        for (int i = 0; i < dfa.getStateCount(); i++) {
            State p = dfa.getState(i);
            Set<Integer> stabilizer = computeStabilizer(p);
            Fst stabilizerGraph = computeStabilizerFst(dfa, stabilizer);
            Fst N = nonOrientedCopy(stabilizerGraph);
            ArrayList<ArrayList<State>> nonOrientedSCCs = getSCCs(N);
            ArrayList<State> C = null;
            for (ArrayList<State> SCC : nonOrientedSCCs) {
                for (State s : SCC) {
                    if (s.getId() == p.getId()) {
                        C = SCC;
                        break;
                    }
                }
            }
            for (State rInC : C) {
                if (rInC.getId() == p.getId()) {
                    continue;
                }
                State rInN = N.getState(rInC.getId());
                boolean checkNextState = false;
                for (Arc rArc : rInN.getArcs()) {
                    if (!rArc.getNextState().equals(rInN)) {
                        checkNextState = true;
                        break;
                    }
                }
                if (!checkNextState) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Determine if the given FST is locally threshold testable. It is assumed that the input automaton is a minimized
     * DFA.
     * @param dfa The input DFA test test, assumed to be minimized.
     * @return true if dfa is locally threshold testable, false otherwise
     */
    public static boolean isLocallyThresholdTestable(Fst dfa) {
        // get direct product graphs
        Fst gamma2 = directProduct(dfa, 2);
        Fst gamma3 = directProduct(dfa, 3);

        // get SCCs of Γ
        ArrayList<ArrayList<State>> gammaSCCs = getSCCs(dfa);

        // mark SCC nodes in Γ, Γ², Γ³
        boolean[] gammaSCCNodes = markSCCNodes(dfa);
        boolean[] gamma2SCCNodes = markSCCNodes(gamma2);
        boolean[] gamma3SCCNodes = markSCCNodes(gamma3);

        // generate reachability matrices for Γ and Γ²
        boolean[][] gammaReachabilityMatrix = getReachabilityMatrix(dfa);
        boolean[][] gamma2ReachabilityMatrix = getReachabilityMatrix(gamma2);

        // check lemma 12
        if (!checkPQReachability(dfa, gamma2, gammaReachabilityMatrix, gamma2SCCNodes)) {
            return false;
        }

        // debugging printout
        System.out.println("passed lemma 12");

        // check definition 15
        if (!checkDefinition15(dfa, gamma2, gamma3, gammaReachabilityMatrix, gamma2ReachabilityMatrix, gamma2SCCNodes,
                gamma3SCCNodes, gammaSCCs)) {
            return false;
        }

        // debugging printout
        System.out.println("passed definition 15");

        // check theorem 16
        if (!checkTheorem16(dfa, gamma2, gamma3, gammaReachabilityMatrix, gamma2ReachabilityMatrix, gamma2SCCNodes,
                gamma3SCCNodes, gammaSCCs)) {
            return false;
        }

        // debugging printout
        System.out.println("passed theorem 16");

        return true;
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
            ArrayList<String> newStates = new ArrayList<String>();
            currentStates.clear();
            for (String s : label.split(",")) {
                if (s == null) {
                    continue;
                } else {
                    currentStates.add(idToStateOrig.get(Integer.parseInt(s)));
                }

            }
            for (MutableState curState : currentStates) {
                List<MutableArc> arcs = curState.getArcs();
                if (newStates.size() != arcs.size()) {
                    for (int i = 0; i < arcs.size(); i++) {
                        newStates.add("");
                    }
                }

                for (int i = 0; i < arcs.size(); i++) {
                    MutableArc arc = arcs.get(i);
                    int pos = arc.getIlabel() - 1;
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

                MutableState nState = synMonoid.getOrNewState(nStateName);

                synMonoid.addArc(stateToProcess, "" + (i + 1), "" + (i + 1), nState, 0);

                boolean foundProcessed = false;
                boolean foundToProcess = false;
                for (MutableState s : processedStates) {
                    if (idToLabel.get(s.getId()).equals(nStateName)) {
                        foundProcessed = true;
                        break;
                    }
                }
                if (!foundProcessed) {
                    for (MutableState s : statesToProcess) {
                        if (idToLabel.get(s.getId()).equals(nStateName)) {
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

        // extra check: ensure that any SCCs of size 1 actually consist of a state with a self-loop
        ArrayList<ArrayList<State>> tmp = new ArrayList<>();
        for (ArrayList<State> SCC : SCCs) {
            if (SCC.size() == 1) {
                State s = SCC.get(0);
                boolean hasSelfLoop = false;
                for (Arc arc : s.getArcs()) {
                    if (arc.getNextState().equals(s)) {
                        hasSelfLoop = true;
                        break;
                    }
                }
                if (hasSelfLoop) {
                    tmp.add(SCC);
                }
            } else {
                tmp.add(SCC);
            }
        }

//        return SCCs;
        return tmp;
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

    /**
     * Get the states of a pair graph of the form (t,*) or (*,t).
     * @param pairGraph The given pair graph
     * @return a list of states in pairGraph of the form (t,*) or (*,t)
     */
    public static ArrayList<State> getAsteriskStates(Fst pairGraph) {
        ArrayList<State> asteriskStates = new ArrayList<>();
        for (int i = 0; i < pairGraph.getStateCount(); i++) {
            State s = pairGraph.getState(i);
            String sName = pairGraph.getStateSymbols().invert().keyForId(i);
            String[] sLabels = sName.split(DELIMITER);
            if (sLabels[0].equals(UNUSED_SYMBOL) || sLabels[1].equals(UNUSED_SYMBOL)) {
                asteriskStates.add(s);
            }
        }
        return asteriskStates;
    }

    /**
     * Check if there is a path from a given component to a state of the form (t,*) or (*,t) in a given pair graph.
     * @param comp The given component
     * @param pairGraph The given pair graph
     * @return true if there is a path, false otherwise
     */
    public static boolean isPathFromComponentToAsteriskState(ArrayList<State> comp, Fst pairGraph) {
        ArrayList<State> asteriskStates = getAsteriskStates(pairGraph);
        for (State componentState : comp) {
            for (State asteriskState : asteriskStates) {
                if (isPath(componentState, asteriskState, pairGraph)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a state transition graph is TS-local with respect to a given SCC.
     * @param scc The given SCC
     * @param dfa The state transition graph (represented here as a DFA)
     * @return true if dfa is TS-local w.r.t. scc, false otherwise
     */
    public static boolean isTSLocalWRT(ArrayList<State> scc, Fst dfa) {
        // find m0
        ArrayList<State> m0 = getM0(scc, dfa);

        // find the pair graph on m0's set of states and scc's set of states
        Fst pairGraph = getPairGraph(dfa, new HashSet<>(m0), new HashSet<>(scc));

        // get SCCs of pairGraph
        ((MutableFst) pairGraph).setStart(((MutableFst) pairGraph).getState(0));   // this is a bit of a hack
        ArrayList<ArrayList<State>> pairGraphSCCs = getSCCs(pairGraph);

        // check if there is a path from an SCC in the pair graph to a state of the form (t,*) or (*,t)
        for (ArrayList<State> pairGraphSCC : pairGraphSCCs) {
            if (pairGraphSCC.size() == 1) {
                State s = pairGraphSCC.get(0);
                for (Arc arc : s.getArcs()) {
                    if (arc.getNextState().equals(s)) {
                        if (isPathFromComponentToAsteriskState(pairGraphSCC, pairGraph)) {
                            return false;
                        }
                    }
                }
            }
            if (pairGraphSCC.size() > 1 && isPathFromComponentToAsteriskState(pairGraphSCC, pairGraph)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compute the stabilizer of a given state
     * i.e., the set of states that bring us from state p back to p
     * @param state The given state
     * @return set of symbols in the stabilizer
     */
    public static Set<Integer> computeStabilizer(State state){

        Set<Integer> stabilizer = new HashSet<>();

        for (Arc arc : state.getArcs()){
          if (arc.getNextState().equals(state)){
            stabilizer.add(arc.getIlabel());
          }
        }
        return stabilizer;
    }


    /**
     * Compute the fst of the stabilizer
     * i.e., the fst containing only arcs with those labels
     * @param fst The given fst
     * @param stabilizer given set of labels to include in final fst
     * @return fst with only arcs with labels in stabilizer set
     */
    public static Fst computeStabilizerFst(Fst fst, Set<Integer> stabilizer){
      // creates deep clone of fst
      MutableFst copyFst = MutableFst.copyFrom(fst);

      for (int i = 0; i < fst.getStateCount(); i++){
        MutableState state = copyFst.getState(i);
        List<MutableArc> outgoingArcs = state.getArcs();
        List<MutableArc> arcsToRemove = new ArrayList<>();
        for (MutableArc arc: outgoingArcs){
          if (!stabilizer.contains(arc.getIlabel())){
//            outgoingArcs.remove(arc);
              arcsToRemove.add(arc);
          }
        }
        for (MutableArc arc : arcsToRemove) {
            outgoingArcs.remove(arc);
        }
      }
      return copyFst;
    }

    /**
     * Create a non oriented copy,
     * i.e., a copy of the fst where for every arc from a to b in fst,
     * there is an arc from a to b and from b to a in the copy
     * @param fst The given fst to copy
     * @return non oriented copy of fst
     */
    public static Fst nonOrientedCopy(Fst fst){
      MutableFst nonOrientedFst = MutableFst.copyFrom(fst);

      //return nonOrientedFst;
      for (int i = 0; i < nonOrientedFst.getStateCount(); i++){
        MutableState state = nonOrientedFst.getState(i);
        List<MutableArc> outgoingArcs = state.getArcs();
        for (MutableArc arc: outgoingArcs){
          MutableState nextState = arc.getNextState();
          boolean alreadySet = false;
          for (MutableArc nextStateArc : nextState.getArcs()){
            if (nextStateArc.getNextState().equals(state)){
              alreadySet = true;
              break;
            }
          }

          // create a new arc to state and add it to nextState's arc list
          if (!alreadySet){
            int label = arc.getIlabel();
            MutableArc newArc = new MutableArc(label, label, 0, state);
            nextState.getArcs().add(newArc);
          }
        }
      }

      return nonOrientedFst;
    }

    /**
     * Get a copy of the input FST with any self-loops removed.
     * @param fst The FST to remove self-loops from.
     * @return A copy of fst with self-loops removed.
     */
    public static Fst withoutSelfLoops(Fst fst) {
        MutableFst copy = MutableFst.emptyWithCopyOfSymbols(fst);
        for (int i = 0; i < fst.getStateCount(); i++) {
            State state = fst.getState(i);
            copy.addState(new MutableState(state.getFinalWeight()), fst.getStateSymbols().invert().keyForId(i));
        }
        for (int i = 0; i < fst.getStateCount(); i++) {
            State state = fst.getState(i);
            MutableState copyState = copy.getState(i);
            for (Arc arc : state.getArcs()) {
                if (!arc.getNextState().equals(state)) {
                    copy.addArc(copyState, arc.getIlabel(), arc.getOlabel(), copy.getState(arc.getNextState().getId()), arc.getNextState().getFinalWeight());
                }
            }
        }
        return copy;
    }

    /**
     * Get the reachability matrix for all pairs of states in the given DFA. A state q with index j is reachable from a
     * state p with index i if and only if reachabilityMatrix[i][j] == true.
     * @param dfa The DFA to find the reachability matrix for.
     * @return The reachability matrix for dfa, as a 2D array
     */
    public static boolean[][] getReachabilityMatrix(Fst dfa) {
        int n = dfa.getStateCount();
        boolean[][] reachabilityMatrix = new boolean[n][n];

        // fill in the matrix with the proper values
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    reachabilityMatrix[i][j] = true;
                    continue;
                }
                State p = dfa.getState(i);
                State q = dfa.getState(j);
                reachabilityMatrix[i][j] = isPath(p, q, dfa);
            }
        }

        return reachabilityMatrix;
    }

    /**
     * Get the direct product of a state transition graph with itself i-1 times.
     * @param gamma The input state transition graph.
     * @param i The number of copies of dfa to include in the product.
     * @return The direct product of dfa with itself i-1 times.
     */
    public static Fst directProduct(Fst gamma, int i) {
        if (i < 1) {
            return null;   // TODO: handle the invalid input in a better way
        }

        if (i == 1) {
            return gamma;
        }

        // create an empty product graph
        MutableFst productGraph = new MutableFst();
        productGraph.useStateSymbols();

        // get the new state symbols of the product graph
        Iterable<String> gammaStateSyms = gamma.getStateSymbols().symbols();
        List<String> gammaStateSymsList = new ArrayList<>();
        for (String stateSym : gammaStateSyms) {
            gammaStateSymsList.add(stateSym);
        }
        List<String> productGraphStateSymbols = getProductGraphStateSymbols(gammaStateSymsList, i);
        for (String stateSym : productGraphStateSymbols) {
            productGraph.addState(new MutableState(), stateSym);
        }

        // add transitions to the product graph by adding the appropriate transitions between each pair of states
        SymbolTable.InvertedSymbolTable gammaInputSyms = gamma.getInputSymbols().invert();
        SymbolTable.InvertedSymbolTable productGraphStateSyms = productGraph.getStateSymbols().invert();
        for (int j = 0; j < productGraph.getStateCount(); j++) {
            for (int k = 0; k < productGraph.getStateCount(); k++) {
                String startStateSym = productGraphStateSyms.keyForId(j);
                String[] originalStartStateSyms = startStateSym.split(DELIMITER);

                String endStateSym = productGraphStateSyms.keyForId(k);
                String[] originalEndStateSyms = endStateSym.split(DELIMITER);

                // for the current two product graph states (p1,p2,...,pn) and (q1,q2,...,qn), add a transition from
                // (p1,p2,...,pn) -> (q1,q2,...,qn) labeled by sigma iff the transitions p1 -> q1, p2 -> q2, ...,
                // pn -> qn all exist in the original state transition graph and are labeled by sigma
                for (String transitionSym : gamma.getInputSymbols().symbols()) {
                    boolean shouldAddArc = true;
                    for (int m = 0; m < i; m++) {
                        State originalGraphP = gamma.getState(originalStartStateSyms[m]);
                        State originalGraphQ = gamma.getState(originalEndStateSyms[m]);

                        boolean foundArcWithTransitionSym = false;
                        for (Arc arc : originalGraphP.getArcs()) {
                            if (arc.getNextState().equals(originalGraphQ) &&
                                    gammaInputSyms.keyForId(arc.getIlabel()).equals(transitionSym)) {
                                foundArcWithTransitionSym = true;
                                break;
                            }
                        }

                        if (!foundArcWithTransitionSym) {
                            // did not find transition p_i -> q_i labeled by transitionSym, so we should
                            // not add the corresponding arc in the product graph
                            shouldAddArc = false;
                            break;
                        }
                    }
                    if (shouldAddArc) {
                        productGraph.addArc(productGraph.getState(j), transitionSym, transitionSym,
                                productGraph.getState(k), 0);
                    }
                }
            }
        }

        // here's that hack again
        productGraph.setStart(productGraph.getState(0));

        return productGraph;
    }

    /**
     * Get the states of the product graph given the original graph state symbols and the number of copies of the graph
     * to include in the product.
     * @param stateSyms The state symbols of the original graph.
     * @param i The number of copies of the original graph to include in the product.
     * @return A list of the new state symbols for the product graph.
     */
    private static List<String> getProductGraphStateSymbols(List<String> stateSyms, int i) {
        // base case
        if (i <= 1) {
            return stateSyms;
        }

        // recursive case
        List<String> ans = new ArrayList<>();
        for (String stateSym : stateSyms) {
            List<String> allRemaining = getProductGraphStateSymbols(stateSyms, i - 1);
            for (String remaining : allRemaining) {
                ans.add(stateSym + DELIMITER + remaining);
            }
        }
        return ans;
    }

    /**
     * Check if for every state (p, q) of Γ^2 if p~q in Γ
     * if we find such a p,q and p != q, the we return false (is not LTT)
     * @param gamma The original state transition graph
     * @param gamma2 FST of Γ^2
     * @param g1Reachability reachability matrix for Γ
     * @param gamma2SCCNodes Marks which nodes in Γ² are SCC nodes
     * @return true if no such p and q are found, false otherwise
     */
    public static boolean checkPQReachability(Fst gamma, Fst gamma2, boolean[][] g1Reachability, boolean[] gamma2SCCNodes){
        SymbolTable gamma2SymbolTable = gamma2.getStateSymbols();

        for (int i = 0; i < gamma2.getStateCount(); i++){
            String pqStateSymbol = gamma2SymbolTable.invert().keyForId(i);
            String[] pAndq = pqStateSymbol.split(DELIMITER);
            String pSymbol = pAndq[0];
            String qSymbol = pAndq[1];

            // if (p,q) is not an SCC node or if p == q, move on to next state in gamma2
            if (gamma2SCCNodes[i] && !(pSymbol.equals(qSymbol))){
                int pId = gamma.getState(pSymbol).getId();
                int qId = gamma.getState(qSymbol).getId();

                boolean pathFromPtoQ = g1Reachability[pId][qId];
                boolean pathFromQtoP = g1Reachability[qId][pId];
                if (pathFromPtoQ && pathFromQtoP) {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * For every four nodes p, q, r, r1 of gamma, check
     * r1 reachable from r reachable from p and q reachable from p
     * if true, make TSCC(p, q, r, r1)
     * if TSCC is not well defined return false
     * @param gamma FST of Γ
     * @param gamma2 FST of Γ​​²
     * @param gamma3 FST of Γ³
     * @param g1Reachability reachability matrix for Γ
     * @param g2Reachability reachability matrix for Γ²
     * @param gamma2SCCNodes Marks which nodes in Γ² are SCC nodes
     * @param gamma3SCCNodes Marks which nodes in Γ³ are SCC nodes
     * @param gammaSCCs The SCCs of Γ
     * @return true if we find no reason to return false as defined above, false otherwise
     */
    private static boolean checkDefinition15(Fst gamma, Fst gamma2, Fst gamma3, boolean[][] g1Reachability,
                                             boolean[][] g2Reachability, boolean[] gamma2SCCNodes,
                                             boolean[] gamma3SCCNodes, ArrayList<ArrayList<State>> gammaSCCs){
        final int n = gamma.getStateCount();
        for (int p = 0; p < n; p++){
            for (int q = 0; q < n; q++){
                for (int r = 0; r < n; r++){
                    for (int r1 = 0; r1 < n; r1++){
                        // ensure reachability from p to r, r to r1, and p to q before finding TSCC(p,q,r,r1)
                        boolean pathFromPtoR = g1Reachability[p][r];
                        boolean pathFromRtoR1 = g1Reachability[r][r1];
                        boolean pathFromPtoQ = g1Reachability[p][q];
                        if (!pathFromPtoR || !pathFromRtoR1 || !pathFromPtoQ) {
                            continue;
                        }

                        // ensure (p,r1) and (q,r) are SCC-nodes
                        SymbolTable.InvertedSymbolTable gammaStateSyms = gamma.getStateSymbols().invert();
                        String pAndr1Sym = gammaStateSyms.keyForId(p) + DELIMITER + gammaStateSyms.keyForId(r1);
                        String qAndrSym = gammaStateSyms.keyForId(q) + DELIMITER + gammaStateSyms.keyForId(r);
                        int pAndr1Index = gamma2.getState(pAndr1Sym).getId();
                        int qAndrIndex = gamma2.getState(qAndrSym).getId();
                        if (!gamma2SCCNodes[pAndr1Index] || !gamma2SCCNodes[qAndrIndex]) {
                            continue;
                        }

                        // find TSCC(p,q,r,r1) and ensure that it is well defined
                        ArrayList<State> TSCC = getTSCC(p, q, r, r1, gamma, gamma2, gamma3, g1Reachability,
                                g2Reachability, gamma2SCCNodes, gamma3SCCNodes, gammaSCCs);
                        if (TSCC == null) {
                            return false;
                        }

                        // check the next set of states
                    }
                }
            }
        }

        return true;
    }

    /**
     * Check second condition of Theorem 16. For every 5 nodes p, q, r, q1, r1 of Γ, the three conditions that
     * TSCC(p,q,r,r1) and TSCC(p,r,q,q1) are non-empty, (p,q1,r1) is an SCC node, and (q1,r1) is reachable from (q,r)
     * imply that TSCC(p,q,r,r1) = TSCC(p,r,q,q1).
     * @param gamma The FST of Γ
     * @param gamma2 The FST of Γ²
     * @param gamma3 The FST of Γ³
     * @param g1Reachability The reachability matrix of Γ
     * @param g2Reachability The reachability matrix of Γ²
     * @param gamma2SCCNodes Marks which nodes in Γ² are SCC nodes
     * @param gamma3SCCNodes Marks which nodes in Γ³ are SCC nodes
     * @param gammaSCCs The SCCs of Γ
     * @return true if the second condition of Theorem 16 holds, false otherwise
     */
    private static boolean checkTheorem16(Fst gamma, Fst gamma2, Fst gamma3, boolean[][] g1Reachability,
                                          boolean[][] g2Reachability, boolean[] gamma2SCCNodes,
                                          boolean[] gamma3SCCNodes, ArrayList<ArrayList<State>> gammaSCCs) {
        int n = gamma.getStateCount();
        for (int p = 0; p < n; p++) {
            for (int q = 0; q < n; q++) {
                for (int r = 0; r < n; r++) {
                    for (int q1 = 0; q1 < n; q1++) {
                        for (int r1 = 0; r1 < n; r1++) {
                            // ensure reachability from p to r, r to r1, and p to q (necessary for TSCC(p,q,r,r1))
                            boolean pathFromPtoR = g1Reachability[p][r];
                            boolean pathFromRtoR1 = g1Reachability[r][r1];
                            boolean pathFromPtoQ = g1Reachability[p][q];
                            if (!pathFromPtoR || !pathFromRtoR1 || !pathFromPtoQ) {
                                continue;
                            }

                            // ensure reachability from p to q, q to q1, and p to r (necessary for TSCC(p,r,q,q1))
                            boolean pathFromQtoQ1 = g1Reachability[q][q1];
                            if (!pathFromPtoQ || !pathFromQtoQ1 || !pathFromPtoR) {
                                continue;
                            }

                            // make sure (p,r1) and (q,r) are SCC-nodes
                            SymbolTable.InvertedSymbolTable gammaStateSyms = gamma.getStateSymbols().invert();
                            String pAndr1Sym = gammaStateSyms.keyForId(p) + DELIMITER + gammaStateSyms.keyForId(r1);
                            String qAndrSym_test = gammaStateSyms.keyForId(q) + DELIMITER + gammaStateSyms.keyForId(r);
                            int pAndr1Index = gamma2.getState(pAndr1Sym).getId();
                            int qAndrIndex = gamma2.getState(qAndrSym_test).getId();
                            if (!gamma2SCCNodes[pAndr1Index] || !gamma2SCCNodes[qAndrIndex]) {
                                continue;
                            }

                            // make sure (p,q1) and (r,q) are SCC-nodes
                            String pAndq1Sym = gammaStateSyms.keyForId(p) + DELIMITER + gammaStateSyms.keyForId(q1);
                            String rAndqSym = gammaStateSyms.keyForId(r) + DELIMITER + gammaStateSyms.keyForId(q);
                            int pAndq1Index = gamma2.getState(pAndq1Sym).getId();
                            int rAndqIndex = gamma2.getState(rAndqSym).getId();
                            if (!gamma2SCCNodes[pAndq1Index] || !gamma2SCCNodes[rAndqIndex]) {
                                continue;
                            }

                            // only continue checking if TSCC(p,q,r,r1) and TSCC(p,r,q,q1) are well defined
                            ArrayList<State> TSCCpqrr1 = getTSCC(p, q, r, r1, gamma, gamma2, gamma3, g1Reachability,
                                    g2Reachability, gamma2SCCNodes, gamma3SCCNodes, gammaSCCs);
                            ArrayList<State> TSCCprqq1 = getTSCC(p, r, q, q1, gamma, gamma2, gamma3, g1Reachability,
                                    g2Reachability, gamma2SCCNodes, gamma3SCCNodes, gammaSCCs);
                            if (TSCCpqrr1 == null || TSCCprqq1 == null) {
                                // debugging printout
                                System.out.println("TSCC(p,q,r,r1) and/or TSCC(p,r,q,q1) is null");

                                continue;
                            }

                            // only continue checking if (p,q1,r1) is an SCC-node
                            String pAndq1Andr1Sym = gammaStateSyms.keyForId(p) + DELIMITER +
                                    gammaStateSyms.keyForId(q1) + DELIMITER + gammaStateSyms.keyForId(r1);
                            int pAndq1Andr1Index = gamma3.getState(pAndq1Andr1Sym).getId();
                            if (!gamma3SCCNodes[pAndq1Andr1Index]) {
                                // debugging printout
                                System.out.println("(p,q,r1) is not an SCC-node");

                                continue;
                            }

                            // only continue checking if (q1,r1) is reachable from (q,r)
                            String q1Andr1Sym = gammaStateSyms.keyForId(q1) + DELIMITER + gammaStateSyms.keyForId(r1);
                            int q1Andr1Index = gamma2.getState(q1Andr1Sym).getId();
                            if (!g2Reachability[qAndrIndex][q1Andr1Index]) {
                                // debugging printout
                                System.out.println("(q1,r1) is not reachable from (q,r)");

                                continue;
                            }

                            // in the case that the above three conditions hold, check that TSCC(p,q,r,r1) = TSCC(p,r,q,q1)
                            Set<State> TSCCpqrr1Set = new HashSet<>(TSCCpqrr1);
                            Set<State> TSCCprqq1Set = new HashSet<>(TSCCprqq1);
                            if (!TSCCpqrr1Set.equals(TSCCprqq1Set)) {
                                // debugging printout
                                System.out.println("TSCC(p,q,r,r1) != TSCC(p,r,q,q1)");

                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * Make TSCC of all nodes t in gamma such that
     * (q, t) is reachable from (p,r1) and (q, r, t) and (p, r1) are SCC nodes
     * @param p Index of state p
     * @param q Index of state q
     * @param r Index of state r
     * @param r1 Index of state r1
     * @param gamma FST of Γ
     * @param gamma2 FST of Γ²
     * @param gamma3 FST of Γ³
     * @param g1Reachability Reachability matrix for Γ
     * @param g2Reachability Reachability matrix for Γ²
     * @param gamma2SCCNodes Marks which nodes in Γ² are SCC-nodes
     * @param gamma3SCCNodes Marks which nodes in Γ³ are SCC-nodes
     * @param gammaSCCs The SCCs of Γ
     * @return TSCC(p,q,r,r1) as a list of states, or null if it is not well defined
     */
    private static ArrayList<State> getTSCC(int p, int q, int r, int r1, Fst gamma, Fst gamma2, Fst gamma3,
                                            boolean[][] g1Reachability, boolean[][] g2Reachability,
                                            boolean[] gamma2SCCNodes, boolean[] gamma3SCCNodes,
                                            ArrayList<ArrayList<State>> gammaSCCs){
        // ensure reachability from p to r, r to r1, and p to q
        boolean pathFromPtoR = g1Reachability[p][r];
        boolean pathFromRtoR1 = g1Reachability[r][r1];
        boolean pathFromPtoQ = g1Reachability[p][q];
        if (!pathFromPtoR || !pathFromRtoR1 || !pathFromPtoQ) {
            // debugging printout
            System.out.println("reachability condition failed in getTSCC");

            return null;
        }

        // make sure (p,r1) and (q,r) are SCC-nodes
        SymbolTable.InvertedSymbolTable gammaStateSyms = gamma.getStateSymbols().invert();
        String pAndr1Sym = gammaStateSyms.keyForId(p) + DELIMITER + gammaStateSyms.keyForId(r1);
        String qAndrSym = gammaStateSyms.keyForId(q) + DELIMITER + gammaStateSyms.keyForId(r);
        int pAndr1Index = gamma2.getState(pAndr1Sym).getId();
        int qAndrIndex = gamma2.getState(qAndrSym).getId();
        if (!gamma2SCCNodes[pAndr1Index] || !gamma2SCCNodes[qAndrIndex]) {
            // debugging printout
            System.out.println("SCC-node condition failed in getTSCC");
            System.out.println("p: " + p);
            System.out.println("q: " + q);
            System.out.println("r: " + r);
            System.out.println("r1: " + r1);
            System.out.println("(p,r1): " + pAndr1Sym);
            System.out.println("(q,r): " + qAndrSym);

            return null;
        }

        // find the valid states t s.t. (p,r1) -> (q,t) and (q,r,t) is an SCC-node
        Set<State> validTStates = new HashSet<>();
        for (int t = 0; t < gamma.getStateCount(); t++){
            // id1 =  q,t id from statesymbols
            // id2 =  p,r1 id from statesymbols
            String gamma2StartStateSym = gammaStateSyms.keyForId(p) + DELIMITER + gammaStateSyms.keyForId(r1);
            String gamma2EndStateSym = gammaStateSyms.keyForId(q) + DELIMITER + gammaStateSyms.keyForId(t);
            int id2 = gamma2.getState(gamma2StartStateSym).getId();
            int id1 = gamma2.getState(gamma2EndStateSym).getId();

            String gamma3StateSym = gammaStateSyms.keyForId(q) + DELIMITER + gammaStateSyms.keyForId(r) + DELIMITER +
                    gammaStateSyms.keyForId(t);
            int gamma3StateIndex = gamma3.getState(gamma3StateSym).getId();

            if (g2Reachability[id2][id1] && gamma3SCCNodes[gamma3StateIndex]) {
                validTStates.add(gamma.getState(t));
            }
        }

        // check if there is an SCC of Γ that contains all states t computed above
        for (ArrayList<State> gammaSCC : gammaSCCs) {
            if (gammaSCC.containsAll(validTStates)) {
                return gammaSCC;
            }
        }

        System.out.println("couldn't find SCC that contained all states t in getTSCC");

        return null;
    }

    /**
     * Mark nodes as being SCC nodes or not.
     * @param dfa The input DFA to check the nodes of.
     * @return A boolean array where each element indicates whether the node with that index in dfa is an SCC node.
     */
    public static boolean[] markSCCNodes(Fst dfa) {
        boolean[] ans = new boolean[dfa.getStateCount()];

        // start by assuming none of the nodes are SCC-nodes
        for (int i = 0; i < ans.length; i++) {
            ans[i] = false;
        }

        // figure out which nodes actually are SCC-nodes
        ArrayList<ArrayList<State>> SCCs = getSCCs(dfa);
        for (int i = 0; i < ans.length; i++) {
            State s = dfa.getState(i);

            for (ArrayList<State> SCC : SCCs) {
                if (SCC.contains(s)) {
                    ans[i] = true;
                    break;
                }
            }
        }
        return ans;
    }

}
