package sbfst;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;

import java.util.*;

/**
 * Unit tests for sbfst.Utils.java.
 */
public class UtilsTest extends TestCase {
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public UtilsTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( UtilsTest.class );
    }

    /**
     * Test that the pair graph has the correct number of states.
     */
    public void testPairGraphStates()
    {
        // import test dfa
        Convert.setRegexToSplitOn("\\s+");
        Fst dfa = Convert.importFst("test_pairgraph_1");

        // get subsets of states from test dfa
        Set<State> q1 = new HashSet<>();
        q1.add(dfa.getState(0));
        q1.add(dfa.getState(1));
        q1.add(dfa.getState(2));
        q1.add(dfa.getState(3));
        q1.add(dfa.getState(4));
        Set<State> q2 = new HashSet<>();
        q2.add(dfa.getState(2));
        q2.add(dfa.getState(3));
        q2.add(dfa.getState(4));

        // get the pair graph of the test dfa using the state subsets above
        Fst pairGraph = Utils.getPairGraph(dfa, q1, q2);

        assertTrue( dfa.getStateCount() == 5 );
    }

    /**
     * Test that the delta_i transition function works.
     */
    public void testDeltaI() {
        // import test dfa
        Convert.setRegexToSplitOn("\\s+");
        Fst dfa = Convert.importFst("test_pairgraph_1");

        // get subsets of states from test dfa
        Set<State> q1 = new HashSet<>();
        q1.add(dfa.getState(0));
        q1.add(dfa.getState(1));
        q1.add(dfa.getState(2));
        q1.add(dfa.getState(3));
        q1.add(dfa.getState(4));
        Set<State> q2 = new HashSet<>();
        q2.add(dfa.getState(2));
        q2.add(dfa.getState(3));
        q2.add(dfa.getState(4));

        // test transition function delta_1
        assertTrue(Utils.deltaI(dfa, q1, "1", "a").equals("2"));
        assertTrue(Utils.deltaI(dfa, q1, "1", "b").equals(Utils.UNUSED_SYMBOL));
        assertTrue(Utils.deltaI(dfa, q1, "1", "c").equals(Utils.UNUSED_SYMBOL));

        // test transition function delta_2
        assertTrue(Utils.deltaI(dfa, q2, "3", "a").equals("4"));
        assertTrue(Utils.deltaI(dfa, q1, "3", "b").equals(Utils.UNUSED_SYMBOL));
        assertTrue(Utils.deltaI(dfa, q1, "3", "c").equals(Utils.UNUSED_SYMBOL));
    }

    /**
     * Test pair graph creation.
     */
    public void testPairGraph() {
        // import test dfa
        Convert.setRegexToSplitOn("\\s+");
        Fst dfa = Convert.importFst("test_pairgraph_1");

        // get subsets of states from test dfa
        Set<State> q1 = new HashSet<>();
        q1.add(dfa.getState(0));
        q1.add(dfa.getState(1));
        q1.add(dfa.getState(2));
        q1.add(dfa.getState(3));
        q1.add(dfa.getState(4));
        Set<State> q2 = new HashSet<>();
        q2.add(dfa.getState(2));
        q2.add(dfa.getState(3));
        q2.add(dfa.getState(4));

        // get the pair graph of the test dfa using the state subsets above
        Fst pairGraph = Utils.getPairGraph(dfa, q1, q2);
        System.out.println(pairGraph);
    }

    /**
     * Test checking for cycles in graphs.
     */
    public void testAcyclicChecking() {
        // import test dfa
        Convert.setRegexToSplitOn("\\s+");
        Fst dfa = Convert.importFst("test_acyclic_1");

        // this graph is cyclic, so isAcyclic should return false
        assertTrue(!Utils.isAcyclic(dfa));

        // the pair graph is cyclic too
        Set<State> q1 = new HashSet<>();
        q1.add(dfa.getState(0));
        q1.add(dfa.getState(1));
        q1.add(dfa.getState(2));
        q1.add(dfa.getState(3));
        q1.add(dfa.getState(4));
        Set<State> q2 = new HashSet<>();
        q2.add(dfa.getState(2));
        q2.add(dfa.getState(3));
        q2.add(dfa.getState(4));
        assertTrue(!Utils.isAcyclic(Utils.getPairGraph(dfa, q1, q2)));

        // import another test dfa
        Convert.setRegexToSplitOn("\\s+");
        dfa = Convert.importFst("test_acyclic_2");

        // this graph is acyclic, so isAcyclic should return true
        assertTrue(Utils.isAcyclic(dfa));
    }
}
