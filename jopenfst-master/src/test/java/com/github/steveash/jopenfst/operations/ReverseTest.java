/*
 * Copyright 2014 Steve Ash
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.steveash.jopenfst.operations;

import com.github.steveash.jopenfst.Fst;
import com.github.steveash.jopenfst.MutableFst;
import com.github.steveash.jopenfst.io.Convert;
import com.github.steveash.jopenfst.semiring.TropicalSemiring;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author John Salatas jsalatas@users.sourceforge.net
 */
public class ReverseTest {

  @Test
  public void testReverse() {
    // Input label sort test

    MutableFst fst = Convert.importFst("data/tests/algorithms/reverse/A",
                                       TropicalSemiring.INSTANCE);
    MutableFst fstB = Convert.importFst("data/tests/algorithms/reverse/expected",
                                        TropicalSemiring.INSTANCE);

    Fst fstReversed = Reverse.reverse(fst);

    assertTrue(fstB.equals(fstReversed));
  }
}
