package sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.assertTrue;

/**
 * Unit tests for sbfst.Utils.java.
 */
public class UtilsTest {

    Fst fig3A;
    Set<State> fig3AQ1;
    Set<State> fig3AQ2;

    Fst acyclic1;

    Fst fig1M2;
    Set<State> fig1M2Q1;
    Set<State> fig1M2Q2;

    Fst fig1M1;
    Set<State> fig1M1Q1;
    Set<State> fig1M1Q2;

    Fst fig1M1NoSyms;
    Set<State> fig1M1Q1NoSyms;
    Set<State> fig1M1Q2NoSyms;

    Fst fig2M2_altered;

    /**
     * Run before each test case to initialize the testing environment.
     */
    @Before
    public void initialize() {
        Convert.setRegexToSplitOn("\\s+");
        fig3A = Convert.importFst("test_pairgraph_1");
        fig3AQ1 = new HashSet<>();
        fig3AQ1.add(fig3A.getState("1"));
        fig3AQ1.add(fig3A.getState("2"));
        fig3AQ1.add(fig3A.getState("3"));
        fig3AQ1.add(fig3A.getState("4"));
        fig3AQ1.add(fig3A.getState("5"));
        fig3AQ2 = new HashSet<>();
        fig3AQ2.add(fig3A.getState("3"));
        fig3AQ2.add(fig3A.getState("4"));
        fig3AQ2.add(fig3A.getState("5"));
        acyclic1 = Convert.importFst("test_acyclic_1");
        fig1M2 = Convert.importFst("fig1M2");
        fig1M2Q1 = new HashSet<>();
        fig1M2Q1.add(fig1M2.getState(0));
        fig1M2Q1.add(fig1M2.getState(1));
        fig1M2Q2 = new HashSet<>();
        fig1M2Q2.add(fig1M2.getState(0));
        fig1M2Q2.add(fig1M2.getState(1));
        fig1M1 = Convert.importFst("fig1M1");
        fig1M1Q1 = new HashSet<>();
        fig1M1Q1.add(fig1M1.getState(0));
        fig1M1Q1.add(fig1M1.getState(1));
        fig1M1Q1.add(fig1M1.getState(2));
        fig1M1Q2 = new HashSet<>();
        fig1M1Q1.add(fig1M1.getState(0));
        fig1M1Q1.add(fig1M1.getState(1));
        fig1M1Q1.add(fig1M1.getState(2));
        fig1M1NoSyms = Convert.importFst("fig1M1_no_syms");
        fig1M1Q1NoSyms = new HashSet<>();
        fig1M1Q1NoSyms.add(fig1M1NoSyms.getState(0));
        fig1M1Q1NoSyms.add(fig1M1NoSyms.getState(1));
        fig1M1Q1NoSyms.add(fig1M1NoSyms.getState(2));
        fig1M1Q2NoSyms = new HashSet<>();
        fig1M1Q2NoSyms.add(fig1M1NoSyms.getState(0));
        fig1M1Q2NoSyms.add(fig1M1NoSyms.getState(1));
        fig1M1Q2NoSyms.add(fig1M1NoSyms.getState(2));
        fig2M2_altered = Convert.importFst("fig2M2");
    }

    /**
     * Test that the pair graph has the correct number of states.
     */
    @Test
    public void testPairGraphStates()
    {
        // get the pair graph of the test dfa using the state subsets above
        Fst pairGraph = Utils.getPairGraph(fig3A, fig3AQ1, fig3AQ2);

        assertTrue( pairGraph.getStateCount() == 23 );
    }

    /**
     * Test that the delta_i transition function works.
     */
    @Test
    public void testDeltaI() {
        // test transition function delta_1
        assertTrue(Utils.deltaI(fig3A, fig3AQ1, "1", "a").equals("2"));
        assertTrue(Utils.deltaI(fig3A, fig3AQ1, "1", "b").equals(Utils.UNUSED_SYMBOL));
        assertTrue(Utils.deltaI(fig3A, fig3AQ1, "1", "c").equals(Utils.UNUSED_SYMBOL));

        // test transition function delta_2
        assertTrue(Utils.deltaI(fig3A, fig3AQ2, "3", "a").equals("4"));
        assertTrue(Utils.deltaI(fig3A, fig3AQ1, "3", "b").equals(Utils.UNUSED_SYMBOL));
        assertTrue(Utils.deltaI(fig3A, fig3AQ1, "3", "c").equals(Utils.UNUSED_SYMBOL));
    }

    /**
     * Test pair graph creation.
     */
    @Test
    public void testPairGraph() {
        // get the pair graph of the test dfa using the state subsets above
        Fst pairGraph = Utils.getPairGraph(fig3A, fig3AQ1, fig3AQ2);
        for (int i = 0; i < pairGraph.getStateCount(); i++) {
            System.out.println(pairGraph.getStateSymbols().invert().keyForId(i));
        }
        System.out.println(pairGraph);
    }

    /**
     * Test checking for cycles in graphs.
     */
    @Test
    public void testAcyclicChecking() {
        // this graph is cyclic, so isAcyclic should return false
        assertTrue(!Utils.isAcyclic(fig3A));

        // the pair graph is cyclic too
        Fst pairGraph = Utils.getPairGraph(fig3A, fig3AQ1, fig3AQ2);
        assertTrue(!Utils.isAcyclic(pairGraph));

        // this graph is acyclic, so isAcyclic should return true
        assertTrue(Utils.isAcyclic(acyclic1));
    }

    /**
     * Test checking if two components of a graph are pairwise s-local.
     */
    @Test
    public void testIsPairwiseSLocal() {
        // Fig. 3 in Kim, McNaughton, McCloskey 1991 (given components are not pairwise s-local)
        assertTrue(!Utils.isPairwiseSLocal(fig3A, fig3AQ1, fig3AQ2));

        // Fig. 1, M2 in Kim, McNaughton, McCloskey 1991 (given components are not pairwise s-local)
        assertTrue(!Utils.isPairwiseSLocal(fig1M2, fig1M2Q1, fig1M2Q2));

        // Fig. 1, M1 in Kim, McNaughton, McCloskey 1991 (give components are pairwise s-local)
        assertTrue(Utils.isPairwiseSLocal(fig1M1, fig1M1Q1, fig1M1Q2));
    }

    /**
     * Test hasDescendants().
     */
    @Test
    public void testHasDescendants() {
        ArrayList<State> scc = new ArrayList<>();
        scc.add(fig2M2_altered.getState(1));
        scc.add(fig2M2_altered.getState(2));
        assertTrue(!Utils.hasDescendants(scc, fig2M2_altered));

        scc.clear();
        scc.add(fig2M2_altered.getState(0));
        assertTrue(Utils.hasDescendants(scc, fig2M2_altered));
    }

    /**
     * Test componentIsReachable().
     */
    @Test
    public void testComponentIsReachable() {
        ArrayList<State> scc = new ArrayList<>();
        scc.add(fig3A.getState("1"));
        scc.add(fig3A.getState("2"));
        assertTrue(Utils.componentIsReachable(fig3A.getState("1"), scc, fig3A));
        assertTrue(Utils.componentIsReachable(fig3A.getState("2"), scc, fig3A));
        assertTrue(!Utils.componentIsReachable(fig3A.getState("3"), scc, fig3A));
        assertTrue(!Utils.componentIsReachable(fig3A.getState("4"), scc, fig3A));
        assertTrue(!Utils.componentIsReachable(fig3A.getState("5"), scc, fig3A));
    }

    /**
     * Test getM0().
     */
    @Test
    public void testGetM0() {
        ArrayList<State> scc = new ArrayList<>();
        scc.add(fig3A.getState("1"));
        scc.add(fig3A.getState("2"));
        assertTrue(Utils.getM0(scc, fig3A).equals(scc));

        scc.clear();
        scc.add(fig3A.getState("3"));
        scc.add(fig3A.getState("4"));
        scc.add(fig3A.getState("5"));
        ArrayList<State> m0 = new ArrayList<>();
        m0.add(fig3A.getState("1"));
        m0.add(fig3A.getState("2"));
        m0.add(fig3A.getState("3"));
        m0.add(fig3A.getState("4"));
        m0.add(fig3A.getState("5"));
        assertTrue(Utils.getM0(scc, fig3A).equals(m0));
    }

    /**
     * Test getAsteriskStates().
     */
    @Test
    public void testGetAsteriskStates() {
        Fst pairGraph = Utils.getPairGraph(fig3A, fig3AQ1, fig3AQ2);
        ArrayList<State> asteriskStates = Utils.getAsteriskStates(pairGraph);
        assertTrue(asteriskStates.contains(pairGraph.getState("1" + Utils.DELIMITER + Utils.UNUSED_SYMBOL)));
        assertTrue(asteriskStates.contains(pairGraph.getState("2" + Utils.DELIMITER + Utils.UNUSED_SYMBOL)));
        assertTrue(asteriskStates.contains(pairGraph.getState("3" + Utils.DELIMITER + Utils.UNUSED_SYMBOL)));
        assertTrue(asteriskStates.contains(pairGraph.getState("4" + Utils.DELIMITER + Utils.UNUSED_SYMBOL)));
        assertTrue(asteriskStates.contains(pairGraph.getState("5" + Utils.DELIMITER + Utils.UNUSED_SYMBOL)));
        assertTrue(asteriskStates.contains(pairGraph.getState(Utils.UNUSED_SYMBOL + Utils.DELIMITER + "3")));
        assertTrue(asteriskStates.contains(pairGraph.getState(Utils.UNUSED_SYMBOL + Utils.DELIMITER + "4")));
        assertTrue(asteriskStates.contains(pairGraph.getState(Utils.UNUSED_SYMBOL + Utils.DELIMITER + "5")));
    }

    @Test
    public void testTest() {
        Fst fig3A_copy = Convert.importFst("test_pairgraph_1");
        Set<State> fig3AQ1_copy = new HashSet<>();
        fig3AQ1_copy.add(fig3A_copy.getState("1"));
        fig3AQ1_copy.add(fig3A_copy.getState("2"));
        fig3AQ1_copy.add(fig3A_copy.getState("3"));
        fig3AQ1_copy.add(fig3A_copy.getState("4"));
        fig3AQ1_copy.add(fig3A_copy.getState("5"));
        Set<State> fig3AQ2_copy = new HashSet<>();
        fig3AQ2_copy.add(fig3A_copy.getState("3"));
        fig3AQ2_copy.add(fig3A_copy.getState("4"));
        fig3AQ2_copy.add(fig3A_copy.getState("5"));
        Fst pairGraph = Utils.getPairGraph(fig3A_copy, fig3AQ1_copy, fig3AQ2_copy);
        ((MutableFst) pairGraph).setStart(((MutableFst) fig3A_copy).getState(0));
        ArrayList<ArrayList<State>> SCCs = Utils.getSCCs(pairGraph);
        System.out.println(SCCs);
    }

    /**
     * Test isPath().
     */
    @Test
    public void testIsPath() {
        // test_isPath_0.fst.txt
        Fst test0 = Convert.importFst("test_isPath_0");
        State zero = test0.getState("0");
        State one = test0.getState("1");
        State two = test0.getState("2");
        assertTrue(!Utils.isPath(zero, two, test0));
        assertTrue(!Utils.isPath(two, zero, test0));
        assertTrue(!Utils.isPath(one, zero, test0));
        assertTrue(!Utils.isPath(one, two, test0));
        assertTrue(Utils.isPath(zero, one, test0));
        assertTrue(Utils.isPath(two, one, test0));
    }

    /**
     * Test isPathFromComponentToAsteriskState().
     */
    @Test
    public void testIsPathFromComponentToAsteriskState() {
        Fst pairGraph = Utils.getPairGraph(fig3A, fig3AQ1, fig3AQ2);
        ((MutableFst) pairGraph).setStart(((MutableFst) pairGraph).getState(0));
        ArrayList<State> comp = new ArrayList<>();
        comp.add(pairGraph.getState("1,3"));
        comp.add(pairGraph.getState("2,4"));
        assertTrue(!Utils.isPathFromComponentToAsteriskState(comp, pairGraph));

        comp.clear();
        comp.add(pairGraph.getState("5,3"));
        assertTrue(Utils.isPathFromComponentToAsteriskState(comp, pairGraph));

        comp.clear();
        comp.add(pairGraph.getState("4,4"));
        assertTrue(!Utils.isPathFromComponentToAsteriskState(comp, pairGraph));
    }

    /**
     * Test isLocallyTestable().
     */
    @Test
    public void testIsLocallyTestable() {
        // Fig. 1, M1 in Kim, McNaugton, McCloskey 1991 (is locally testable)
        assertTrue(Utils.isLocallyTestable(fig1M1));

        // Fig. 1, M2 in Kim, McNaughton, McCloskey 1991 (is NOT locally testable)
        assertTrue(!Utils.isLocallyTestable(fig1M2));

        // exactly one a
        Fst exactlyOneA = Convert.importFst("exactly_one_a");
        assertTrue(!Utils.isLocallyTestable(exactlyOneA));

        // sl0.fst.txt
        Fst sl0 = Convert.importFst("sl0");
        assertTrue(Utils.isLocallyTestable(sl0));

        // sl1.fst.txt
        Fst sl1 = Convert.importFst("sl1");
        assertTrue(Utils.isLocallyTestable(sl1));

        // sl2.fst.txt
        Fst sl2 = Convert.importFst("sl2");
        assertTrue(Utils.isLocallyTestable(sl2));

        // lt0.fst.txt
        Fst lt0 = Convert.importFst("lt0");
        assertTrue(Utils.isLocallyTestable(lt0));

        // lt1.fst.txt (failed)
        Fst lt1 = Convert.importFst("lt1");
        assertTrue(Utils.isLocallyTestable(lt1));

        // lt2.fst.txt
        Fst lt2 = Convert.importFst("lt2");
        assertTrue(Utils.isLocallyTestable(lt2));

        // lt3.fst.txt
        Fst lt3 = Convert.importFst("lt3");
        assertTrue(Utils.isLocallyTestable(lt3));

        // lt4.fst.txt
        Fst lt4 = Convert.importFst("lt4");
        assertTrue(Utils.isLocallyTestable(lt4));

        // sp0.fst.txt
        Fst sp0 = Convert.importFst("sp0");
        assertTrue(!Utils.isLocallyTestable(sp0));

        // sp1.fst.txt
        Fst sp1 = Convert.importFst("sp1");
        assertTrue(!Utils.isLocallyTestable(sp1));

        // sp2.fst.txt
        Fst sp2 = Convert.importFst("sp2");
        assertTrue(!Utils.isLocallyTestable(sp2));

        // pt0.fst.txt
        Fst pt0 = Convert.importFst("pt0");
        assertTrue(!Utils.isLocallyTestable(pt0));

        // pt1.fst.txt
        Fst pt1 = Convert.importFst("pt1");
        assertTrue(!Utils.isLocallyTestable(pt1));

        // pt2.fst.txt
        Fst pt2 = Convert.importFst("pt2");
        assertTrue(!Utils.isLocallyTestable(pt2));

        // pt3.fst.txt
        Fst pt3 = Convert.importFst("pt3");
        assertTrue(!Utils.isLocallyTestable(pt3));

        // pt4.fst.txt
        Fst pt4 = Convert.importFst("pt4");
        assertTrue(!Utils.isLocallyTestable(pt4));
    }

    /**
     * Test isPiecewiseTestable().
     */
    @Test
    public void testIsPiecewiseTestable() {
        // pt0.fst.txt
        Fst pt0 = Convert.importFst("pt0");
        assertTrue(Utils.isPiecewiseTestable(pt0));

        // pt1.fst.txt
        Fst pt1 = Convert.importFst("pt1");
        assertTrue(Utils.isPiecewiseTestable(pt1));

        // pt2.fst.txt
        Fst pt2 = Convert.importFst("pt2");
        assertTrue(Utils.isPiecewiseTestable(pt2));

        // sp0.fst.txt
        Fst sp0 = Convert.importFst("sp0");
        assertTrue(Utils.isPiecewiseTestable(sp0));

        // sp1.fst.txt
        Fst sp1 = Convert.importFst("sp1");
        assertTrue(Utils.isPiecewiseTestable(sp1));

        // sp2.fst.txt
        Fst sp2 = Convert.importFst("sp2");
        assertTrue(Utils.isPiecewiseTestable(sp2));

        // pt3.fst.txt
        Fst pt3 = Convert.importFst("pt3");
        assertTrue(Utils.isPiecewiseTestable(pt3));

        // pt4.fst.txt
        Fst pt4 = Convert.importFst("pt4");
        assertTrue(Utils.isPiecewiseTestable(pt4));

        // sl0.fst.txt
        Fst sl0 = Convert.importFst("sl0");
        assertTrue(!Utils.isPiecewiseTestable(sl0));

        // sl1.fst.txt
        Fst sl1 = Convert.importFst("sl1");
        assertTrue(!Utils.isPiecewiseTestable(sl1));

        // sl2.fst.txt
        Fst sl2 = Convert.importFst("sl2");
        assertTrue(!Utils.isPiecewiseTestable(sl2));

        // lt0.fst.txt
        Fst lt0 = Convert.importFst("lt0");
        assertTrue(!Utils.isPiecewiseTestable(lt0));

        // lt1.fst.txt
        Fst lt1 = Convert.importFst("lt1");
        assertTrue(!Utils.isPiecewiseTestable(lt1));

        // lt2.fst.txt
        Fst lt2 = Convert.importFst("lt2");
        assertTrue(!Utils.isPiecewiseTestable(lt2));

//        // lt3.fst.txt
//        Fst lt3 = Convert.importFst("lt3");
//        assertTrue(!Utils.isPiecewiseTestable(lt3));

        // lt4.fst.txt
        Fst lt4 = Convert.importFst("lt4");
        assertTrue(!Utils.isPiecewiseTestable(lt4));
    }

    /**
     * Test withoutSelfLoops().
     */
    @Test
    public void testWithoutSelfLoops() {
        // pt0.fst.txt
        Fst pt0 = Convert.importFst("pt0");
        Fst pt0WithoutSelfLoops = Utils.withoutSelfLoops(pt0);
        System.out.println(pt0.toString());
        System.out.println(pt0WithoutSelfLoops.toString());
        assertTrue(pt0WithoutSelfLoops.getStateCount() == 3);
        assertTrue(Utils.isAcyclic(pt0WithoutSelfLoops));
    }

    /**
     * Test getReachabilityMatrix().
     */
    @Test
    public void testGetReachabilityMatrix() {
        // lt0.fst.txt
        Fst lt0 = Convert.importFst("lt0");
        int n = lt0.getStateCount();
        boolean[][] reachabilityMatrix = Utils.getReachabilityMatrix(lt0);
        boolean[][] expectedReachabilityMatrix = new boolean[][]{
                { true,  true,  true },
                { true,  true,  true },
                { false, false, true }
        };
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertTrue(reachabilityMatrix[i][j] == expectedReachabilityMatrix[i][j]);
            }
        }

        // lt1.fst.txt
        Fst lt1 = Convert.importFst("lt1");
        n = lt1.getStateCount();
        reachabilityMatrix = Utils.getReachabilityMatrix(lt1);
        expectedReachabilityMatrix = new boolean[][]{
                { true,  true,  true,  true,  true },
                { true,  true,  true,  true,  true },
                { true,  true,  true,  true,  true },
                { true,  true,  true,  true,  true },
                { false, false, false, false, true }
        };
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertTrue(reachabilityMatrix[i][j] == expectedReachabilityMatrix[i][j]);
            }
        }
    }

    /**
     * Test directProduct().
     */
    @Test
    public void testDirectProduct() {
        // lt0.fst.txt
        Fst lt0 = Convert.importFst("lt0");
        int n = lt0.getStateCount();
        Fst lt0_2 = Utils.directProduct(lt0, 2);
        assertTrue(lt0_2.getStateCount() == n*n);
        Convert.export(lt0_2, "lt0_2");
        System.out.println(lt0_2);
    }

    /**
     * Test isLocallyThresholdTestable().
     */
    @Test
    public void testIsLocallyThresholdTestable() {
        // lt0.fst.txt
        Fst lt0 = Convert.importFst("lt0");
        assertTrue(Utils.isLocallyThresholdTestable(lt0));
//
//        // lt1.fst.txt
//        Fst lt1 = Convert.importFst("lt1");
//        assertTrue(Utils.isLocallyThresholdTestable(lt1));
//
//        // lt2.fst.txt
//        Fst lt2 = Convert.importFst("lt2");
//        assertTrue(Utils.isLocallyThresholdTestable(lt2));
//
//        // lt3.fst.txt
//        Fst lt3 = Convert.importFst("lt3");
//        assertTrue(Utils.isLocallyThresholdTestable(lt3));
//
//        // ltt0.fst.txt
//        Fst ltt0 = Convert.importFst("ltt0");
//        assertTrue(Utils.isLocallyThresholdTestable(ltt0));
//
//        // ltt1.fst.txt
//        Fst ltt1 = Convert.importFst("ltt1");
//        assertTrue(Utils.isLocallyThresholdTestable(ltt1));
//
//        // ltt2.fst.txt
//        Fst ltt2 = Convert.importFst("ltt2");
//        assertTrue(Utils.isLocallyThresholdTestable(ltt2));
//
        // pt0.fst.txt
        Fst pt0 = Convert.importFst("pt0");
        assertTrue(!Utils.isLocallyThresholdTestable(pt0));
    }

    /**
     * Run general functions.
     */
    @Test
    public void testGeneral() {
//        // lt0.fst.txt
//        Fst gamma = Convert.importFst("lt0");
//        Fst gamma2 = Utils.directProduct(gamma, 2);
//        Convert.export(gamma2, "lt0_2");

        // test_s_local.fst.txt
        Fst slocal = Convert.importFst("test_s_local");
        Set<State> q1 = new HashSet<>();
        q1.add(slocal.getState(0));
        q1.add(slocal.getState(1));
        Fst pairgraph = Utils.getPairGraph(slocal, q1, q1);
        ((MutableFst) pairgraph).setStart(((MutableFst) pairgraph).getState(0));
        System.out.println(pairgraph.getStateCount());
        Convert.export(pairgraph, "src/test/resources/test_s_local_pairgraph");
    }
}
