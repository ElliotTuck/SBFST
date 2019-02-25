# SBFST

A library to determine the subregular classification of a given language. It is recommended to use Maven to build this
code from source, as a pom.xml file specifying all of the dependencies is included.

This library is currently setup to determine whether a given FST is aperiodic (i.e. star-free). To test your FST, first
specify it in one or more files (depending on whether you use state symbols, etc.) written in the AT&T FSM format (see
example *.fst.txt files to see what this format looks like). The files you want to test should be placed within the
src/main/resources directory. Once you have done this, execute the following shell commands:
<ul>
   <li><code>$ mvn compile package</code></li>
   <li><code>$ java -jar target/sbfst-1.0-SNAPSHOT-shaded.jar [file containing AT&T text formatted fst]</code></li>
</ul>
where the filename you specify should be the base name of the file without the .fst.txt extension (e.g. if you wanted
to test a file named test_fst.fst.txt then you should use test_fst as the filename.
