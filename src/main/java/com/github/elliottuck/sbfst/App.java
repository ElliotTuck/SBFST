package com.github.elliottuck.sbfst;

import com.github.steveash.jopenfst.*;
import com.github.steveash.jopenfst.io.*;
import java.io.File;
import java.util.*;


/**
 * Main class.
 */
public class App {
  /*
  *****************************
  * IMPORTANT!!!! will need to change
  * synMonoid state labels to comma separated
  * list to accomodate 2+ digit state labels
  * add determinization/minimization
  *********************************
  */
    public static void main(String[] args) {
      String fileName = args[0];
      File inputFile = new File(fileName);
      Convert.setRegexToSplitOn("\\s+");
      MutableFst originalFst = Convert.importFst(inputFile);

      Fst synMonoid = Utils.getSM(originalFst);

      System.out.println(synMonoid);
      Convert.setUseSymbolIdsInText(true);
      int index = fileName.indexOf(".fst");
      String exportFileName = fileName.substring(0, index);
      Convert.export(synMonoid, exportFileName + "SyntacticMonoid");

      int period = Utils.isAperiodic(synMonoid);
      if (period == -1) {
        System.out.println("The input language is aperiodic.");
      } else {
        System.out.println("The input language is periodic with period "
          + period + ".");
      }
    }
}
