# SBFST - Classify the Language of Your DFA

The SBFST library lets you further classify your regular language as star free (SF) or locally testable (LT) (keep an eye out for more classes coming soon! :eyes:).

### Usage

This library is currently not very user friendly. One way to use it is to add your DFA (in the form of four files - test.fst.txt, test.states.syms, test.input.syms, and test.output.syms files — all in AT&T FSM format) to the src/test/resources directory; you can then create and run a test in the src/test/java/sbfst/UtilsTest.java file that reads in your DFA and calls one of the library methods on it to determine if it falls into a certain classification.

Alternatively, you can add your DFA to the src/main/resources directory and then use Maven to compile and package the library into a runnable JAR file:
```
$ mvn compile package
$ java -jar target/sbfst-1.0-SNAPSHOT-shaded.jar [file containing DFA in AT&T FSM format]
```
where the filename you specify should be the base name of the file without the .fst.txt extension (e.g. if you wanted
to test a file named test_fst.fst.txt then you should use test_fst as the filename)
