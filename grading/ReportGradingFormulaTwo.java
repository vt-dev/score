package com.visualthreat.report.core.report.gradingFormula.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.ReportGradingFormula;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
public class ReportGradingFormulaTwo extends ReportGradingFormula {
  private String formulaName = "ReportGradingFormulaTwo";
  @JsonIgnore
  double totalScore = 0;
  @JsonIgnore
  QuickScanECUInfoLeakFormula quickScanECUInfoLeakFormula;
  @JsonIgnore
  QuickScanDataSecurityFormula quickScanDataSecurityFormula;
  @JsonIgnore
  QuickScanEncryptionSecurityFormula quickScanEncryptionSecurityFormula;

  @Override
  @JsonIgnore
  public String getName() {
    return formulaName;
  }

  @JsonIgnore
  private List<TestReportResult> quickScanEcuInfoLeakResult = new ArrayList<>();
  @JsonIgnore
  private List<TestReportResult> quickScanDataSecurityResult = new ArrayList<>();
  @JsonIgnore
  private List<TestReportResult> quickScanEncryptionSecurityResult = new ArrayList<>();

  @Override
  @JsonIgnore
  public double calculateSummaryTotalGrading(final String carModel,
                                             final UUID testTypeId,
                                             final String pinPair,
                                             final UUID formulaID,
                                             final Collection<TestReportResult> testReportResultList,
                                             final Timestamp updateTime) {
    categorizeTestReportResult(testReportResultList);
    quickScanECUInfoLeakFormula = new QuickScanECUInfoLeakFormula();
    totalScore = totalScore + quickScanECUInfoLeakFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        quickScanEcuInfoLeakResult, updateTime);

    quickScanDataSecurityFormula = new QuickScanDataSecurityFormula();
    totalScore = totalScore + quickScanDataSecurityFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        quickScanDataSecurityResult, updateTime);

    quickScanEncryptionSecurityFormula = new QuickScanEncryptionSecurityFormula();
    totalScore = totalScore + quickScanEncryptionSecurityFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        quickScanEncryptionSecurityResult, updateTime);

    final BigDecimal b = new BigDecimal(totalScore);
    totalScore = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    return totalScore;
  }

  @Override
  @JsonIgnore
  public String getDetailInfoOfScore() {
    return quickScanECUInfoLeakFormula.toString()
        + quickScanDataSecurityFormula.toString()
        + quickScanEncryptionSecurityFormula.toString();
  }

  private void categorizeTestReportResult(final Collection<TestReportResult> testReportResultList) {
    for (final TestReportResult testReportResult : testReportResultList) {
      final String testPointName = testReportResult.getTestPointName();
      if (QuickScanECUInfoLeakFormula.TestType.contains(testPointName)) {
        quickScanEcuInfoLeakResult.add(testReportResult);
      } else if (QuickScanDataSecurityFormula.TestType.contains(testPointName)) {
        quickScanDataSecurityResult.add(testReportResult);
      } else if (QuickScanEncryptionSecurityFormula.TestType.contains(testPointName)) {
        quickScanEncryptionSecurityResult.add(testReportResult);
      }
    }
  }
}
