package dk.alexandra.fresco.ml.libext;

import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.Computation;
import dk.alexandra.fresco.framework.builder.numeric.Comparison;
import dk.alexandra.fresco.framework.builder.numeric.Numeric;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.util.Pair;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.lib.conditional.ConditionalSelect;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Computes the index of the minimum element in a list and the element itself. <p>The index is
 * expressed as a list of bits where all bits are 0 except for the bit at the index of the minimum
 * element, which is set to 1.</p>
 */
public class ArgMin implements
    Computation<Pair<List<DRes<SInt>>, SInt>, ProtocolBuilderNumeric> {

  private final List<DRes<SInt>> xs;
  private final int size;

  public ArgMin(List<DRes<SInt>> xs) {
    this.size = xs.size();
    if (this.size < 2) {
      throw new IllegalArgumentException("Minimum protocol. Size should never be less than 2.");
    }
    this.xs = xs;
  }


  @Override
  public DRes<Pair<List<DRes<SInt>>, SInt>> buildComputation(
      ProtocolBuilderNumeric builder) {
    if (this.size == 2) {
      Comparison comparison = builder.comparison();
      Numeric numeric = builder.numeric();
      DRes<SInt> firstValue = this.xs.get(0);
      DRes<SInt> secondValue = this.xs.get(1);
      DRes<SInt> firstCompare = new LessThan(firstValue, secondValue).buildComputation(builder);
      DRes<SInt> minimum = builder
          .seq(new ConditionalSelect(firstCompare, firstValue, secondValue));
      DRes<SInt> secondCompare = numeric
          .sub(BigInteger.ONE, firstCompare);
      return () -> new Pair<>(
          Arrays.asList(firstCompare, secondCompare),
          minimum.out());
    } else if (this.size == 3) {
      Numeric numeric = builder.numeric();
      DRes<SInt> firstValue = this.xs.get(0);
      DRes<SInt> secondValue = this.xs.get(1);
      DRes<SInt> thirdValue = this.xs.get(2);
      DRes<SInt> c1Prime = new LessThan(firstValue, secondValue).buildComputation(builder);

      DRes<SInt> m1 = builder.seq(new ConditionalSelect(c1Prime, firstValue, secondValue));

      DRes<SInt> c2Prime = new LessThan(m1, thirdValue).buildComputation(builder);

      DRes<SInt> m2 = builder.seq(new ConditionalSelect(c2Prime, m1, thirdValue));

      DRes<SInt> firstComparison = numeric.mult(c1Prime, c2Prime);
      DRes<SInt> secondComparison = numeric.sub(c2Prime, firstComparison);
      DRes<SInt> tmp = numeric.sub(BigInteger.ONE, firstComparison);
      DRes<SInt> thirdComparison = numeric.sub(tmp, secondComparison);
      return () -> new Pair<>(
          Arrays.asList(firstComparison, secondComparison, thirdComparison),
          m2.out());
    } else {
      return builder.seq((seq) -> {
        int k1 = size / 2;
        List<DRes<SInt>> x1 = xs.subList(0, k1);
        List<DRes<SInt>> x2 = xs.subList(k1, size);
        return Pair.lazy(x1, x2);
      }).pairInPar(
          (seq, pair) -> seq.seq(new ArgMin(pair.getFirst())),
          (seq, pair) -> seq.seq(new ArgMin(pair.getSecond()))
      ).seq((seq, pair) -> {
        Numeric numeric = seq.numeric();
        Pair<List<DRes<SInt>>, SInt> minimum1 = pair.getFirst();
        Pair<List<DRes<SInt>>, SInt> minimum2 = pair.getSecond();
        SInt m1 = minimum1.getSecond();
        SInt m2 = minimum2.getSecond();

        DRes<SInt> compare = new LessThan(() -> m1, () -> m2).buildComputation(seq);
        DRes<SInt> oneMinusCompare = numeric.sub(BigInteger.ONE, compare);
        DRes<SInt> m = seq.seq(new ConditionalSelect(compare, () -> m1, () -> m2));
        DRes<List<DRes<SInt>>> enteringIndexes = seq.par((par) -> {
          Numeric parNumeric = par.numeric();
          List<DRes<SInt>> cs = new ArrayList<>(size);
          for (DRes<SInt> c : minimum1.getFirst()) {
            cs.add(parNumeric.mult(c, compare));
          }
          for (DRes<SInt> c : minimum2.getFirst()) {
            cs.add(parNumeric.mult(c, oneMinusCompare));
          }
          return () -> cs;
        });
        return () -> {
          List<DRes<SInt>> out = enteringIndexes.out();
          SInt out1 = m.out();
          return new Pair<>(out, out1);
        };
      });
    }
  }
}