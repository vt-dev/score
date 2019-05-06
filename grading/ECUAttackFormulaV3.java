package com.visualthreat.report.core.report.gradingFormula.v3;

import com.visualthreat.report.core.TestPoints;
import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class ECUAttackFormulaV3 extends AbstractScoreFormula{

  private final static double DEFAULT_PERCENTAGE = 0.6;


  double totalScore = 0;
  double ecuCanAttackScore = 5 * DEFAULT_PERCENTAGE;
  double ecuFloodAttackScore = 5 * DEFAULT_PERCENTAGE;
  double ecuCanAttackScoreDefault = 5;
  double ecuFloodAttackScoreDefault = 5;

  int ecuCanAttackNumVulns = -1;
  int ecuFloodAttackNumVulns = -1;

  StringBuilder ecuCanAttackVulns = new StringBuilder();
  StringBuilder ecuFloodAttackVulns = new StringBuilder();


  @Override
  public double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      List<TestReportResult> testReportResultList, Timestamp updateTime) {

    getDefaultScores(formulaID);
    getAvgScores();
    for(TestReportResult testReportResult : testReportResultList){
      int vulns = testReportResult.getNumSecurityVulnerabilities()
          + testReportResult.getNumWarnings();
      String vulnsDetails = getVulnerabilityDetailInfo(testReportResult);
      switch (TestType.getType(testReportResult.getTestPointName())){
        case CANFrameUnderflowOverflowAttack:
        case SimulateReprogrammingAttack:
        case TrafficHandling:
          ecuCanAttackNumVulns = ecuCanAttackNumVulns == -1 ? vulns : ecuCanAttackNumVulns + vulns;
          ecuCanAttackVulns.append(vulnsDetails);
          break;
        case DOSAttack:
        case ECUTrafficFuzzing:
          ecuFloodAttackNumVulns = ecuFloodAttackNumVulns == -1 ? vulns : ecuFloodAttackNumVulns + vulns;
          ecuFloodAttackVulns.append(vulnsDetails);
          break;
      }
    }

    TestType[] testTypes = TestType.values();
    for(TestType testType : testTypes){
      switch (testType) {
        case ECUCANAttack:
          if(ecuCanAttackNumVulns == -1){
            break;
          }
          double percentage = getECUAttackPercentage(ecuCanAttackNumVulns);
          ecuCanAttackScore = ecuCanAttackScoreDefault * percentage;
          break;
        case ECUFloodAttach:
          if(ecuFloodAttackNumVulns == -1){
            break;
          }
          percentage = getECUAttackPercentage(ecuFloodAttackNumVulns);
          ecuFloodAttackScore = ecuFloodAttackScoreDefault * percentage;
          break;
      }
    }
    totalScore = ecuCanAttackScore + ecuFloodAttackScore;
    BigDecimal b = new BigDecimal(totalScore);
    totalScore = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return totalScore;
  }

  private double getECUAttackPercentage(int numVulns) {
    double percentage = 0;
    if(numVulns < 1){
      percentage = 1;
    }else if(numVulns <=2){
      percentage = 0.6;
    }else if(numVulns >= 3){
      percentage = 0;
    }
    return percentage;
  }

  private void insertIntoTestPointDetailTable(String carModel, UUID testTypeId, String pinPair, UUID formulaID, Timestamp updateTime){
    String testPointName = "";
    double score = 0;
    double total = 0;
    String scoreDetailInfo = "";
    TestType[] testTypes = TestType.values();
    for(TestType testType : testTypes){
      switch (testType){
        case ECUCANAttack:
          testPointName = TestType.ECUCANAttack.value;
          score = ecuCanAttackScore;
          total = ecuCanAttackScoreDefault;
          scoreDetailInfo = ecuCanAttackVulns.toString();
          break;
        case ECUFloodAttach:
          testPointName = TestType.ECUFloodAttach.value;
          score = ecuFloodAttackScore;
          total = ecuFloodAttackScoreDefault;
          scoreDetailInfo = ecuFloodAttackVulns.toString();
          break;
      }
      if(testPointName != null && !testPointName.equals("")){
        insertIntoTestPointDetail(carModel, testTypeId, pinPair, formulaID, updateTime,
            testPointName, score, total, scoreDetailInfo);
        testPointName = "";
      }
    }
  }

  private void getDefaultScores(UUID gradingFormulaID){
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    ecuCanAttackScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECUCANAttack.value,
        gradingFormulaID).join();
    ecuFloodAttackScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECUFloodAttach.value,
        gradingFormulaID).join();
  }

  private void getAvgScores(){
    ecuCanAttackScore = ecuCanAttackScoreDefault * DEFAULT_PERCENTAGE;
    ecuFloodAttackScore = ecuFloodAttackScoreDefault * DEFAULT_PERCENTAGE;
  }

  public enum TestType {
    // ECU CAN Attack
    ECUCANAttack("ECU CAN Attack"),
    CANFrameUnderflowOverflowAttack(TestPoints.VARY_DLC.getTestPoint().getName()),
    SimulateReprogrammingAttack(TestPoints.SIMULATE_REPROGRAMMING.getTestPoint().getName()),
    TrafficHandling(TestPoints.CAN_TRAFFIC_RESPONSE_RATE.getTestPoint().getName()),
    ECUFloodAttach("ECU Flood Attack"),
    DOSAttack(TestPoints.DOS_FLOOD.getTestPoint().getName()),
    ECUTrafficFuzzing(TestPoints.CAN_FUZZ.getTestPoint().getName());

    private final String value;

    TestType(String inVal) {
      this.value = inVal;
    }

    public String getCode() {
      return this.value;
    }

    public static boolean contains(final String testType) {

      for (TestType value : TestType.values()) {
        if (value.getCode().equals(testType)) {
          return true;
        }
      }
      return false;
    }
    public static TestType getType(String value){
      for (TestType testType : TestType.values()) {
        if (testType.getCode().equals(value)) {
          return testType;
        }
      }
      return null;
    }
    public static int getTotalNum(){
      return TestType.values().length;
    }
  }
}
