package sbfst;

import com.github.steveash.jopenfst.Fst;
import com.github.steveash.jopenfst.MutableFst;
import com.github.steveash.jopenfst.MutableState;
import com.github.steveash.jopenfst.semiring.Semiring;

import java.util.ArrayList;

/**
 * Extend an Fst to a single final state and undo operations.
 *
 * @author John Salatas jsalatas@users.sourceforge.net
 * @author Elliot Tuck
 */
public class ExtendFinal2 {

    /**
     * Creates a new FST that is a copy of the existing with a new signle final state
     *
     * It adds a new final state with a 0.0 (Semiring's 1) final wight and connects the current final states to it using
     * epsilon transitions with weight equal to the original final state's weight.
     *
     * @param fst the input fst -- it will not be modified
     * @return a mutable fst that is a copy of the input + extended with a single final
     */
    public static MutableFst apply(Fst fst) {
        fst.throwIfInvalid();
        MutableFst copy = MutableFst.copyFrom(fst);
        Semiring semiring = copy.getSemiring();
        ArrayList<MutableState> resultStates = initResultStates(copy, semiring);

        // Add a new single final
        MutableState newFinal = new MutableState(semiring.one());
        copy.addState(newFinal, Utils.FINAL_SYMBOL);
        int epsILabel = copy.getInputSymbols().getOrAdd(Fst.EPS);
        int epsOLabel = copy.getOutputSymbols().getOrAdd(Fst.EPS);
        for (MutableState s : resultStates) {
            // add epsilon transition from the old final to the new one
            copy.addArc(s, epsILabel, epsOLabel, newFinal, s.getFinalWeight());
            // set old state's weight to zero
            s.setFinalWeight(semiring.zero());
        }
        return copy;
    }

    private static ArrayList<MutableState> initResultStates(MutableFst copy, Semiring semiring) {
        ArrayList<MutableState> resultStates = new ArrayList<>();
        for (int i = 0; i < copy.getStateCount(); i++) {
            MutableState state = copy.getState(i);
            if (semiring.isNotZero(state.getFinalWeight())) {
                resultStates.add(state);
            }
        }
        return resultStates;
    }

}
