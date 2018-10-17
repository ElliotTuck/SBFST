package test;

import com.github.steveash.jopenfst.*;

public class Test {
    public static void main(String[] args) {
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
    }
}