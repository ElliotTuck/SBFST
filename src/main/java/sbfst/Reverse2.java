package sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.semiring.Semiring;

/**
 * Reverse2 operation.
 *
 * @author John Salatas jsalatas@users.sourceforge.net
 * @author Elliot Tuck
 */
public class Reverse2 {

    /**
     * Reverses an fst
     *
     * @param infst the fst to reverse
     * @return the reversed fst
     */
    public static MutableFst reverse(Fst infst) {
        infst.throwIfInvalid();

        MutableFst fst = ExtendFinal2.apply(infst);

        Semiring semiring = fst.getSemiring();

        MutableFst result = new MutableFst(fst.getStateCount(), semiring);
        result.setInputSymbolsAsCopy(fst.getInputSymbols());
        result.setOutputSymbolsAsCopy(fst.getOutputSymbols());
        MutableState[] stateMap = initStateMap(fst, semiring, result);

        for (int i = 0; i < fst.getStateCount(); i++) {
            State oldState = fst.getState(i);
            MutableState newState = stateMap[oldState.getId()];
            for (int j = 0; j < oldState.getArcCount(); j++) {
                Arc oldArc = oldState.getArc(j);
                MutableState newNextState = stateMap[oldArc.getNextState().getId()];
                double newWeight = semiring.reverse(oldArc.getWeight());
                result.addArc(newNextState, oldArc.getIlabel(), oldArc.getOlabel(), newState, newWeight);
            }
        }
        return result;
    }

    private static MutableState[] initStateMap(MutableFst fst, Semiring semiring, MutableFst result) {
        MutableState[] stateMap = new MutableState[fst.getStateCount()];
        for (int i = 0; i < fst.getStateCount(); i++) {
            State state = fst.getState(i);
            MutableState newState = result.newState();
            newState.setFinalWeight(semiring.zero());
            stateMap[state.getId()] = newState;
            if (semiring.isNotZero(state.getFinalWeight())) {
                result.setStart(newState);
            }
        }
        stateMap[fst.getStartState().getId()].setFinalWeight(semiring.one());
        return stateMap;
    }
}
