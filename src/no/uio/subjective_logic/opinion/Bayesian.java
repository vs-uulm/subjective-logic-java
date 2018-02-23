package no.uio.subjective_logic.opinion;

public interface Bayesian<T>
{
  double getNegative();

  double getPositive();

  double max();

  double min();

  int size();

  double[] values();
}