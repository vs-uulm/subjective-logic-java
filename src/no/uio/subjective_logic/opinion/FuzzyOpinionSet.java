package no.uio.subjective_logic.opinion;

import java.util.Iterator;
import java.util.SortedSet;

public interface FuzzyOpinionSet extends Iterable<FuzzyOpinion>
{
  void setAtomicity(double paramDouble);

  double getAtomicity();

  FuzzyOpinion get(Opinion paramOpinion);

  String getText(Opinion paramOpinion);

  double getPolarization();

  void setPolarization(double paramDouble);

  String toString(FuzzyOpinion paramFuzzyOpinion);

  String toString();

  SortedSet<FuzzyOpinion> values();

  Iterator<FuzzyOpinion> iterator();

  FuzzySet getExpectationSet();

  FuzzySet getUncertaintySet();
}