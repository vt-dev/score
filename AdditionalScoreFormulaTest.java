package com.visualthreat.report.core;

import com.visualthreat.report.core.report.gradingFormula.AdditionalItem;
import com.visualthreat.report.core.report.gradingFormula.AdditionalScoreFormula;
import com.visualthreat.report.core.report.gradingFormula.DetailedScore;
import com.visualthreat.report.core.report.gradingFormula.Details;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.visualthreat.report.core.report.gradingFormula.PercentageScore.EXTRA_2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
class AdditionalScoreFormulaTest {

  private static final double EPS = 0.0001;

  private static Collection<AdditionalItem> items;
  private static AdditionalItem b11;
  private static AdditionalItem b12;
  private static AdditionalItem b1;
  private static AdditionalItem b2;
  private static AdditionalItem bonus;
  private static AdditionalItem p311;
  private static AdditionalItem p312;
  private static AdditionalItem p221;
  private static AdditionalItem p222;
  private static AdditionalItem p31;
  private static AdditionalItem p21;
  private static AdditionalItem p22;
  private static AdditionalItem p1;
  private static AdditionalItem p2;
  private static AdditionalItem p3;
  private static AdditionalItem penalty;

  /*
   *     Additional Item structure
   *
   *          bonus (1x)          penalty (-1x)
   *         /     \             /    \         \
   *   (1x) b1     b2 (10) (15) p1    p2 (10)   p3 (10)
   *       /  \                      /  \          \
   * (3) b11  b12 (5)       (0.3x) p21  p22 (0.2x) p31 (1x)
   *                                   /  \            \     \
   *                          (0.5x) p221  p222 (0.5x)  p311 p312 (0 0x)
   */
  @SuppressWarnings("MagicNumber")
  @BeforeEach
  void beforeEach() {
    final UUID bonusId = UUID.randomUUID();
    final UUID penaltyId = UUID.randomUUID();
    final UUID b1Id = UUID.randomUUID();
    final UUID p2Id = UUID.randomUUID();
    final UUID p3Id = UUID.randomUUID();
    final UUID p22Id = UUID.randomUUID();
    final UUID p31Id = UUID.randomUUID();

    // 3
    b11 = new AdditionalItem(
        UUID.randomUUID(), b1Id, null,
        "Bonus -> 1 -> 1", "Bonus -> 1 -> 1 description",
        3, 0, false,
        Collections.emptyList());

    // 5
    b12 = new AdditionalItem(
        UUID.randomUUID(), b1Id, null,
        "Bonus -> 1 -> 2", "Bonus -> 1 -> 2 description",
        5, 0, false,
        Collections.emptyList());

    // 3 + 5 = 8
    b1 = new AdditionalItem(
        b1Id, bonusId, null,
        "Bonus -> 1", "Bonus -> 1 description",
        0, 1, false,
        Arrays.asList(b11, b12));
    b11.setParent(b1);
    b12.setParent(b1);

    // 10
    b2 = new AdditionalItem(
        UUID.randomUUID(), bonusId, null,
        "Bonus -> 2", "Bonus -> 2 description",
        10, 0, false,
        Collections.emptyList());

    // 8 + 10 = 18
    bonus = new AdditionalItem(
        bonusId, null, null,
        "Bonus", "",
        0, 1, false,
        Arrays.asList(b1, b2));
    b1.setParent(bonus);
    b2.setParent(bonus);

    // x0.3
    p21 = new AdditionalItem(
        UUID.randomUUID(), p2Id, null,
        "Penalty -> 2 -> 1", "Penalty -> 2 -> 1 description",
        0, 0.3, false,
        Collections.emptyList());

    // 0.5
    p221 = new AdditionalItem(
        UUID.randomUUID(), p22Id, null,
        "Penalty -> 2 -> 2 -> 1", "Penalty -> 2 -> 2 -> 1 description",
        0, 0.5, false,
        Collections.emptyList());

    // 0.5
    p222 = new AdditionalItem(
        UUID.randomUUID(), p22Id, null,
        "Penalty -> 2 -> 2 -> 2", "Penalty -> 2 -> 2 -> 2 description",
        0, 0.5, false,
        Collections.emptyList());

    // x0.2
    p22 = new AdditionalItem(
        p22Id, p2Id, null,
        "Penalty -> 2 -> 2", "Penalty -> 2 -> 2 description",
        0, 0.2, false,
        Arrays.asList(p221, p222));
    p221.setParent(p22);
    p222.setParent(p22);

    // 0
    p311 = new AdditionalItem(
        UUID.randomUUID(), p31Id, null,
        "Penalty -> 3 -> 1 -> 1", "Penalty -> 3 -> 1 -> 1 description",
        0, 0, false,
        Collections.emptyList());

    // 0
    p312 = new AdditionalItem(
        UUID.randomUUID(), p31Id, null,
        "Penalty -> 3 -> 1 -> 2", "Penalty -> 3 -> 1 -> 2 description",
        0, 0, false,
        Collections.emptyList());

    // x0.2 * x1 = x0.2
    p31 = new AdditionalItem(
        p31Id, p3Id, null,
        "Penalty -> 3 -> 1", "Penalty -> 3 -> 1 description",
        0, 1.0, false,
        Arrays.asList(p311, p312));
    p311.setParent(p31);
    p312.setParent(p31);

    // 15
    p1 = new AdditionalItem(
        UUID.randomUUID(), penaltyId, null,
        "Penalty -> 1", "Penalty -> 1 description",
        15, 0, false,
        Collections.emptyList());

    // (x0.3 + x0.2) x 10 = 5
    p2 = new AdditionalItem(
        p2Id, penaltyId, null,
        "Penalty -> 2", "Penalty -> 2 description",
        10, 0, false,
        Arrays.asList(p21, p22));
    p21.setParent(p2);
    p22.setParent(p2);

    // x0.2 * x1 * 10 = 2
    p3 = new AdditionalItem(p3Id, penaltyId, null,
        "Penalty -> 3", "Penalty -> 3 description",
        10, 0, false,
        Collections.singletonList(p31));
    p31.setParent(p3);

    // (15 + 5) x -1 = -20
    penalty = new AdditionalItem(
        penaltyId, null, null,
        "Penalty", "",
        0, -1, false,
        Arrays.asList(p1, p2, p3));
    p1.setParent(penalty);
    p2.setParent(penalty);
    p3.setParent(penalty);

    items = Arrays.asList(bonus, penalty);
  }

  @Test
  @DisplayName("One Bonus item")
  void testOneBonus() {
    b1.setExcluded(true);
    final AdditionalScoreFormula formula = new AdditionalScoreFormula(
        Collections.singletonList(bonus));
    final DetailedScore score = formula.calculateScore();
    assertEquals(b2.getScore(), score.getScore(), EPS);
    assertEquals(2, score.getDetails().size());

    forDetail(score.getDetails(), b2.getName(), d -> {
      assertEquals(b2.getScore(), d.getScore(), EPS);
      assertEquals(b2.getScore(), d.getTotal(), EPS);
      assertEquals(b2.getName(), d.getName());
      assertEquals(b2.getDescription(), d.getDetails());
    });

    forDetail(score.getDetails(), bonus.getName(), d -> {
      assertEquals(b2.getScore(), d.getScore(), EPS);
      assertEquals(b11.getScore() + b12.getScore() + b2.getScore(), d.getTotal(), EPS);
      assertEquals(bonus.getName(), d.getName());
      assertEquals("", d.getDetails());
    });
  }

  @Test
  @DisplayName("One Penalty Item")
  void testOnePenalty() {
    p1.setExcluded(true);
    p3.setExcluded(true);
    p21.setExcluded(true);
    p222.setExcluded(true);
    final AdditionalScoreFormula formula = new AdditionalScoreFormula(
        Collections.singletonList(penalty));
    final DetailedScore score = formula.calculateScore();

    final double expectedScore = p221.getMultiplier() * p22.getMultiplier() * p2.getScore() * penalty.getMultiplier();
    assertEquals(expectedScore, score.getScore(), EPS);

    final Collection<Details> details = score.getDetails();

    forDetail(details, p221.getName(), d -> {
      assertEquals(expectedScore, d.getScore(), EPS);
      assertEquals(expectedScore, d.getTotal(), EPS);
    });

    forDetail(details, p22.getName(), d -> {
      assertEquals(expectedScore, d.getScore(), EPS);
      assertEquals(expectedScore, d.getTotal(), EPS);
    });

    forDetail(details, p2.getName(), d -> {
      assertEquals(expectedScore, d.getScore(), EPS);
      assertEquals(p2.getScore() * penalty.getMultiplier(), d.getTotal(), EPS);
    });

    forDetail(details, penalty.getName(), d -> {
      assertEquals(expectedScore, d.getScore(), EPS);
      assertEquals((p1.getScore() + p2.getScore() + p3.getScore()) * penalty.getMultiplier(), d.getTotal(), EPS);
    });
  }

  @Test
  @DisplayName("Whole Bonus Tree")
  void testBonusTotalScore() {
    final AdditionalScoreFormula formula = new AdditionalScoreFormula(
        Collections.singletonList(bonus));
    final Collection<Details> details = formula.calculateScore().getDetails();
    forDetail(details, b11.getName(), d -> assertEquals(b11.getScore(), d.getTotal(), EPS));
    forDetail(details, b12.getName(), d -> assertEquals(b12.getScore(), d.getTotal(), EPS));
    forDetail(details, b1.getName(), d -> assertEquals(b11.getScore() + b12.getScore(), d.getTotal(), EPS));
    forDetail(details, b2.getName(), d -> assertEquals(b2.getScore(), d.getTotal(), EPS));
    forDetail(details, bonus.getName(), d -> assertEquals(b11.getScore() + b12.getScore() + b2.getScore(), d.getTotal(), EPS));
  }

  @Test
  @DisplayName("Whole Penalty Tree")
  void testPenaltyTotalScore() {
    final AdditionalScoreFormula formula = new AdditionalScoreFormula(
        Collections.singletonList(penalty));
    final Collection<Details> details = formula.calculateScore().getDetails();
    forDetail(details, p1.getName(), d -> assertEquals(-p1.getScore(), d.getTotal(), EPS));
    forDetail(details, p21.getName(), d -> assertEquals(-p21.getMultiplier() * p2.getScore(), d.getTotal(), EPS));
    forDetail(details, p22.getName(), d -> assertEquals(-p22.getMultiplier() * p2.getScore(), d.getTotal(), EPS));
    forDetail(details, p2.getName(), d -> assertEquals(-p2.getScore(), d.getTotal(), EPS));
    forDetail(details, penalty.getName(), d -> assertEquals(
        (p1.getScore() + p2.getScore() + p3.getScore()) * penalty.getMultiplier(),
        d.getTotal(),
        EPS));
  }

  @Test
  @DisplayName("All Trees (whole forest)")
  void testWholeTree() {
    final AdditionalScoreFormula formula = new AdditionalScoreFormula(items);
    final DetailedScore score = formula.calculateScore();

    final double expectedScore = b11.getScore() + b12.getScore() + b2.getScore() +
        penalty.getMultiplier() * (p1.getScore() +
            p2.getScore() * (p21.getMultiplier() + p22.getMultiplier()) +
            p3.getScore() * p31.getMultiplier() * EXTRA_2
        );
    assertEquals(expectedScore, score.getScore(), EPS);

    final long treeSize = items.stream().mapToLong(i -> i.flatChildren().size()).sum();
    assertEquals(treeSize, score.getDetails().size());

    final Collection<Details> details = new ArrayList<>(score.getDetails());

    forDetail(details, penalty.getName(), d ->
        assertEquals(penalty.getMultiplier() * (p1.getScore() + p2.getScore() + p3.getScore()), d.getTotal(), EPS));

    forDetail(details, bonus.getName(), d ->
        assertEquals(b11.getScore() + b12.getScore() + b2.getScore(), d.getTotal(), EPS));

    forDetail(details, p1.getName(), d -> {
      final double expected = p1.getScore() * penalty.getMultiplier();
      assertEquals(expected, d.getScore(), EPS);
      assertEquals(expected, d.getTotal(), EPS);
    });

    forDetail(details, p21.getName(), d -> {
      final double expected = p21.getMultiplier() * p2.getScore() * penalty.getMultiplier();
      assertEquals(expected, d.getScore(), EPS);
      assertEquals(expected, d.getTotal(), EPS);
    });
  }

  @Test
  @DisplayName("Zero items")
  void testZeroItems() {
    bonus.setExcluded(true);
    p1.setExcluded(true);
    p2.setExcluded(true);

    final AdditionalScoreFormula formula = new AdditionalScoreFormula(items);
    final DetailedScore score = formula.calculateScore();

    final double expectedTreeScore = penalty.getMultiplier() * p3.getScore() * p31.getMultiplier() * EXTRA_2;
    assertEquals(expectedTreeScore, score.getScore(), EPS);

    final long expectedTreeSize = items.stream().mapToLong(i -> i.flatChildren().size()).sum();
    final long treeSize = score.getDetails().stream()
        .filter(d -> d.getScore() != 0)
        .count();
    assertEquals(expectedTreeSize, treeSize);

    final Collection<Details> details = new ArrayList<>(score.getDetails());

    forDetail(details, p311.getName(), d -> {
      final double expected = p3.getScore() * penalty.getMultiplier() * EXTRA_2 / 2;
      assertEquals(expected, d.getScore(), EPS);
      assertEquals(expected, d.getTotal(), EPS);
    });

    forDetail(details, p312.getName(), d -> {
      final double expected = p3.getScore() * penalty.getMultiplier() * EXTRA_2 / 2;
      assertEquals(expected, d.getScore(), EPS);
      assertEquals(expected, d.getTotal(), EPS);
    });

    forDetail(details, p31.getName(), d -> {
      final double expected = p3.getScore() * penalty.getMultiplier() * EXTRA_2;
      assertEquals(expected, d.getScore(), EPS);
      assertEquals(expected, d.getTotal(), EPS);
    });

    forDetail(details, p3.getName(), d -> {
      final double expectedScore = p3.getScore() * penalty.getMultiplier() * EXTRA_2;
      final double expectedTotal = p3.getScore() * penalty.getMultiplier();
      assertEquals(expectedScore, d.getScore(), EPS);
      assertEquals(expectedTotal, d.getTotal(), EPS);
    });
  }

  private void forDetail(final Collection<Details> details, final String name,
                         final Consumer<Details> func) {
    final Optional<Details> detail = details.stream()
        .filter(d -> d.getName().equals(name))
        .findAny();
    assertTrue(detail.isPresent());
    detail.ifPresent(func);
  }
}
