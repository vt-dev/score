package com.visualthreat.report.core.report.gradingFormula;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Data
@AllArgsConstructor
@ToString(of = {"id", "name"})
@EqualsAndHashCode(of = {"id"})
public class AdditionalItem {
  private UUID id;
  private UUID parentId;
  private AdditionalItem parent;
  private String name;
  private String description;
  private int score;
  private double multiplier;
  private boolean excluded;
  private Collection<AdditionalItem> items;

  public Collection<AdditionalItem> flatChildren() {
    if (isExcluded()) {
      return Collections.emptyList();
    }

    final Collection<AdditionalItem> result = items.stream()
        .filter(i -> !i.isExcluded())
        .flatMap(i -> i.flatChildren().stream())
        .collect(Collectors.toList());
    result.add(this);

    return result;
  }

  public Collection<AdditionalItem> flatParents() {
    final Collection<AdditionalItem> result = new ArrayList<>();
    result.add(this);

    if (parent != null) {
      result.addAll(parent.flatParents(result));
    }

    return result;
  }

  private Collection<AdditionalItem> flatParents(final Collection<AdditionalItem> acc) {
    acc.add(this);

    if (parent != null) {
      acc.addAll(parent.flatParents(acc));
    }

    return acc;
  }

  public double getTotalScore() {
    final double mul = multiplier == 0 ? 1 : multiplier;
    return score + items.stream().mapToDouble(AdditionalItem::getTotalScore).sum() * mul;
  }
}
