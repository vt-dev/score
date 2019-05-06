package com.visualthreat.report.core.report.gradingFormula;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class AdditionalScoreFormula {
  private final Collection<AdditionalItem> additionalItems;

  public DetailedScore calculateScore() {
    return additionalItems.stream()
        .filter(root -> !root.isExcluded())
        // just use score (only for root elements)
        .map(root -> {
          final PercentageScore score = calculateScore(root, PercentageScore.zero(), 1);
          return new DetailedScore(score.getScore(), new ArrayList<>(score.getDetails()));
        })
        .reduce((a, b) -> new DetailedScore(a.getScore() + b.getScore(), merge(a.getDetails(), b.getDetails())))
        .orElse(new DetailedScore(0.0, Collections.emptyList()));
  }

  private PercentageScore calculateScore(final AdditionalItem parent, final PercentageScore acc, final int level) {
    final PercentageScore childrenScore = parent.getItems().stream()
        .filter(root -> !root.isExcluded())
        .map(a -> calculateScore(a, PercentageScore.zero(), level + 1))
        .reduce(PercentageScore::add)
        .orElse(PercentageScore.one());
    final double score =
        parent.getScore() * childrenScore.getPercentage() +
            parent.getMultiplier() * childrenScore.getScore();
    final double multiplier = childrenScore.getPercentage() == 0 ?
        parent.getMultiplier() :
        parent.getMultiplier() * childrenScore.getPercentage();
    acc.add(multiplier, score);

    // details
    acc.setDetails(childrenScore.getDetails());

    // details
    final Details parentDetails = new Details(parent.getId(), parent.getName(), score, parent.getTotalScore(), parent.getDescription(), parent);

    // set scores
    if (parent.getScore() != 0) {
      Collection<Details> curParents = Collections.singletonList(parentDetails);
      while (!curParents.isEmpty()) {
        final Collection<Details> nextParents = new ArrayList<>();
        curParents.forEach(curParent -> {
          final double curScore = curParent.getItem().getScore() == 0 ?
              curParent.getScore() : curParent.getItem().getScore();
          final Collection<Details> children = acc.getDetails().stream()
              .filter(d -> d.getItem().getParent().equals(curParent.getItem()))
              .filter(d -> d.getScore() == 0 && d.getTotal() == 0)
              .collect(Collectors.toList());
          nextParents.addAll(children);

          final Map<Boolean, List<Details>> partitionedChildren = children.stream()
              .collect(Collectors.partitioningBy(i -> i.getItem().getMultiplier() == 0));
          Optional.ofNullable(partitionedChildren.get(false))
              .ifPresent(normChildren -> normChildren.forEach(d -> {
                final double childScore = curScore * d.getItem().getMultiplier();
                d.setScore(childScore);
                d.setTotal(childScore);
              }));

          Optional.ofNullable(partitionedChildren.get(true))
              .ifPresent(zeroChildren -> {
                final int zeroChildrenSize = zeroChildren.size();
                final double left = curScore * PercentageScore.zeroItemsPercentage(zeroChildrenSize);
                if (zeroChildrenSize > 0) {
                  final double zeroScore = left / zeroChildrenSize;
                  zeroChildren.forEach(d -> {
                    d.setScore(zeroScore);
                    d.setTotal(zeroScore);
                  });
                }
              });

          // when total child multiplier less than 1
          if (!children.isEmpty() && curParent.getItem().getScore() == 0) {
            final double childTotalScore = children.stream()
                .mapToDouble(Details::getScore).sum();
            final double childTotalTotal = children.stream()
                .mapToDouble(Details::getTotal).sum();
            if (curParent.getScore() > childTotalScore) {
              curParent.setScore(childTotalScore);
            }
            if (curParent.getTotal() > childTotalTotal) {
              curParent.setTotal(childTotalTotal);
            }
          }
        });

        curParents = nextParents;
      }
    }

    // multiply
    if (parent.getMultiplier() != 0) {
      final double mul = parent.getMultiplier();
      acc.getDetails()
          .forEach(d -> {
            d.setScore(mul * d.getScore());
            d.setTotal(mul * d.getTotal());
          });
    }

    // save details
    acc.getDetails().add(parentDetails);

    log.info("Level: {}, details: {}, childrenScores: {}， acc: {}", level, acc.getDetails(), childrenScore, acc);

    return acc;
  }

  public String toString() {
    return String.format("Additional Items Score: %.2f\n", calculateScore().getScore());
  }

  private static <T> Collection<T> merge(final Collection<T> a, final Collection<T> b) {
    final Collection<T> l = new ArrayList<>();
    l.addAll(a);
    l.addAll(b);
    return l;
  }
}
