package no.uio.subjective_logic.opinion;

import java.beans.PropertyChangeListener;
import java.io.Serializable;

public interface Opinion extends Serializable, Comparable<Opinion>
{
  double getAtomicity();

  double getExpectation();

  SubjectiveOpinion toSubjectiveOpinion();

  PureBayesian toPureBayesian();

  int compareTo(Opinion paramOpinion);

  void addPropertyChangeListener(PropertyChangeListener paramPropertyChangeListener);

  void addPropertyChangeListener(String paramString, PropertyChangeListener paramPropertyChangeListener);

  PropertyChangeListener[] getPropertyChangeListeners();

  PropertyChangeListener[] getPropertyChangeListeners(String paramString);

  boolean hasListeners(String paramString);

  void removePropertyChangeListener(PropertyChangeListener paramPropertyChangeListener);

  void removePropertyChangeListener(String paramString, PropertyChangeListener paramPropertyChangeListener);
}
