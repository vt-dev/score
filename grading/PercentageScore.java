package com.visualthreat.report.core.report.gradingFormula;

import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;

@Data
@ToString(exclude = {"details"})
public class PercentageScore {
  public static final double EXTRA_2 = 0.2;
  private static final double EXTRA_3 = 0.4;
  private static final double EXTRA_4 = 0.7;

  private double percentage;
  private double score;
  private int zeroItems = 0;
  private Collection<Details> details = new ArrayList<>();

  public PercentageScore(final double percentage, final double score) {
    this.percentage = percentage;
    this.score = score;
  }

  public void add(final double percentage, final double score) {
    this.percentage += percentage;
    this.score += score;

    if (percentage == 0 && score == 0) {
      this.zeroItems += 1;
    }
  }

  public PercentageScore add(final PercentageScore percentageScore) {
    add(percentageScore.percentage, percentageScore.getScore());
    this.getDetails().addAll(percentageScore.getDetails());
    return this;
  }

  public double getPercentage() {

    final double result = percentage + zeroItemsPercentage(zeroItems);

    return Math.min(1, Math.max(-1, result));
  }

  public static double zeroItemsPercentage(final int zeroItems) {
    if (zeroItems == 0) {
      return 0.0;
    } else if (zeroItems <= 3) {
      return EXTRA_2;
    } else if (zeroItems <= 5) {
      return EXTRA_3;
    } else if (zeroItems <= 8) {
      return EXTRA_4;
    }
    return 1.0;
  }

  public static PercentageScore zero() {
    return new PercentageScore(0, 0);
  }

  public static PercentageScore one() {
    return new PercentageScore(1, 0);
  }
}
