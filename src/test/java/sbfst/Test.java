package sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.Convert;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class Test extends TestCase {
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public Test(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static junit.framework.Test suite() {
        return new TestSuite(Test.class);
    }

    public void test() {
        Convert.setRegexToSplitOn("\\s+");
        MutableFst originalFst = Convert.importFst("tomita2");
        System.out.println(originalFst);
        MutableFst fst = new MutableFst();
        // by default states are only identified by indexes assigned by the FST, if you want to instead
        // identify your states with symbols (and read/write a state symbol table) then call this before
        // adding any states to the FST
        fst.useStateSymbols();
        MutableState startState = fst.newStartState("<start>");
        // setting a final weight makes this state an eligible final state
        fst.newState("</s>").setFinalWeight(0.0);

        // you can add symbols manually to the symbol table
        int symbolId = fst.getInputSymbols().getOrAdd("<eps>");
        fst.getOutputSymbols().getOrAdd("<eps>");

        // add arcs on the MutableState instances directly or using convenience methods on the fst instance
        // if using state labels you can pass the labels (if they dont exist, new states will be created)
        // params are inputSatate, inputLabel, outputLabel, outputState, arcWeight
        fst.addArc("state1", "inA", "outA", "state2", 1.0);

        // alternatively (or if no state symbols) you can use the state instances
        fst.addArc(startState, "inC", "outD", fst.getOrNewState("state3"), 123.0);

        //1. convert from text file to fst
        //2. fst.getStateSymbols
        //3. create new muttable fst
        //4. create name for start state, (concatinate all state symbols)
        // String newState = "";
        // List of currentStates;
        // List nextStates;
        // Stack states to process (newState)
        // List processedStates
        // while (statesToProcess != empty) {
        //5. foreach state in currentStates {
        //arcs = fst.getState(stateSymbol).getArcs()

        //foreach arc in arcs
        // if arc.getILabel() == 1
        // newState.concat(arc.getNextState().getStateSymbol??())
        // nextStates.add(arc.getNextState())
        //}
        // if (processedStates(newState.name) == null)
        // statesToProcess.add(newState.name)

        // 6.
    }
}
