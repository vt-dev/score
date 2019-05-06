package com.visualthreat.report.core.report.gradingFormula.v3;


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
public class ReportGradingFormulaThree extends ReportGradingFormula{

  private String formulaName = "ReportGradingFormulaThree";
  @JsonIgnore
  private double totalScore = 0;
  @JsonIgnore
  private double ecuInfoLeakScore = 0;
  @JsonIgnore
  private double ecuAttackScore = 0;
  @JsonIgnore
  private double ecuDataSecurityScore = 0;
  @JsonIgnore
  private double ecuCommandSniffScore = 0;
  @JsonIgnore
  private double drivingExperienceScore = 0;
  @JsonIgnore
  ECUInfoLeakFormulaV3 ecuInfoLeakFormula;
  @JsonIgnore
  ECUDataSecurityFormulaV3 ecuDataSecurityFormula;
  @JsonIgnore
  ECUAttackFormulaV3 ecuAttackFormula;
  @JsonIgnore
  ECUCommandSniffFormulaV3 ecuCommandSniffFormula;
  @JsonIgnore
  DrivingExperienceFormulaV3 drivingExperienceFormula;

  @JsonIgnore
  private List<TestReportResult> ecuInfoLeakResult = new ArrayList<>();
  @JsonIgnore
  private List<TestReportResult> ecuDataSecurityResult = new ArrayList<>();
  @JsonIgnore
  private List<TestReportResult> ecuAttackResult = new ArrayList<>();


  @Override
  @JsonIgnore
  public String getName() {
    return formulaName;
  }

  @Override
  @JsonIgnore
  public double calculateSummaryTotalGrading(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
                                             Collection<TestReportResult> testReportResultList, Timestamp updateTime) {
    categorizeTestReportResult(testReportResultList);
    ecuInfoLeakFormula = new ECUInfoLeakFormulaV3();
    ecuInfoLeakScore = ecuInfoLeakFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        ecuInfoLeakResult, updateTime);
    ecuDataSecurityFormula = new ECUDataSecurityFormulaV3();
    ecuDataSecurityScore = ecuDataSecurityFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        ecuDataSecurityResult, updateTime);
    ecuAttackFormula = new ECUAttackFormulaV3();
    ecuAttackScore = ecuAttackFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        ecuAttackResult, updateTime);
    ecuCommandSniffFormula = new ECUCommandSniffFormulaV3();
    ecuCommandSniffScore = ecuCommandSniffFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        new ArrayList<>(), updateTime);
    drivingExperienceFormula = new DrivingExperienceFormulaV3();
    drivingExperienceScore = drivingExperienceFormula.calculateScore(carModel, testTypeId, pinPair, formulaID,
        new ArrayList<>(), updateTime);
    getTotalScore();
    return totalScore;
  }

  @Override
  @JsonIgnore
  public String getDetailInfoOfScore(){
    return null;
  }


  private void getTotalScore(){
    totalScore = ecuInfoLeakScore
        + ecuDataSecurityScore
        + ecuAttackScore
        + ecuCommandSniffScore
        + drivingExperienceScore
        + 15;
    BigDecimal b = new BigDecimal(totalScore);
    totalScore = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
  }

  private void categorizeTestReportResult(Collection<TestReportResult> testReportResultList){
    for(TestReportResult testReportResult : testReportResultList){
      String testPointName = testReportResult.getTestPointName();
      if(ECUInfoLeakFormulaV3.TestType.contains(testPointName)){
        ecuInfoLeakResult.add(testReportResult);
      }else if(ECUAttackFormulaV3.TestType.contains(testPointName)){
        ecuAttackResult.add(testReportResult);
      }else if(ECUDataSecurityFormulaV3.TestType.contains(testPointName)){
        ecuDataSecurityResult.add(testReportResult);
      }
    }
  }
}
