package com.github.elliottuck.sbfst;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;

import java.util.*;

/**
 * Unit tests for Utils.java.
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
}
