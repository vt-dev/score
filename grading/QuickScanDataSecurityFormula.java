package com.visualthreat.report.core.report.gradingFormula.v2;

import com.visualthreat.report.core.TestPoints;
import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class QuickScanDataSecurityFormula extends AbstractScoreFormula {

  private final static int POINT_TEN = 10;

  double totalScore = 0;
  double readMemorySecurityScore = 10;
  double manipulateCommunicationSecurityScore = 10;
  double readMemorySecurityScoreDefault = 10;
  double manipulateCommunicationSecurityScoreDefault = 10;

  String readMemorySecurityVulnerabilities = "";
  String manipulateVulns = "";

  @Override
  public double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      List<TestReportResult> testReportResultList, Timestamp updateTime) {

    getDefaultScores(formulaID);
    readMemorySecurityScore = readMemorySecurityScoreDefault;
    manipulateCommunicationSecurityScore = manipulateCommunicationSecurityScoreDefault;

    for(TestReportResult testReportResult : testReportResultList){
      int vulns = testReportResult.getNumSecurityVulnerabilities()
          + testReportResult.getNumWarnings();
      String vulnsDetails = getVulnerabilityDetailInfo(testReportResult);

      switch (TestType.getType(testReportResult.getTestPointName())){
        case DUMP_ECU_MEMORY:
          if(vulns < 1){
            readMemorySecurityScore = 10;
          }else if(vulns >=1 && vulns < 3){
            readMemorySecurityScore = 7;
          }else if(vulns >=3 && vulns < 7){
            readMemorySecurityScore = 5;
          }else if(vulns >= 7){
            readMemorySecurityScore = 3;
          }
          readMemorySecurityVulnerabilities = vulnsDetails;
          break;
        case MANIPULATE_ECU_COMMUNICATION:
          if(vulns < 1){
            manipulateCommunicationSecurityScore = 10;
          }else if(vulns < 5){
            manipulateCommunicationSecurityScore = 7;
          }else if(vulns < 10){
            manipulateCommunicationSecurityScore = 5;
          }else if(vulns >= 10){
            manipulateCommunicationSecurityScore = 3;
          }
          manipulateVulns = vulnsDetails;
          break;
        case UNKNOWN:
          break;
      }
    }
    totalScore = readMemorySecurityScore + manipulateCommunicationSecurityScore;
    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return totalScore;
  }

  private void insertIntoTestPointDetailTable(String carModel, UUID testTypeId, String pinPair, UUID formulaID, Timestamp updateTime){
    String testPointName = "";
    double score = 0;
    double total = 0;
    String scoreDetailInfo = "";
    for(TestType testType : TestType.values()){
      switch (testType){
        case DUMP_ECU_MEMORY:
          testPointName = TestType.DUMP_ECU_MEMORY.value;
          score = readMemorySecurityScore;
          total = readMemorySecurityScoreDefault;
          scoreDetailInfo = readMemorySecurityVulnerabilities;
          break;
        case MANIPULATE_ECU_COMMUNICATION:
          testPointName = TestType.MANIPULATE_ECU_COMMUNICATION.value;
          score = manipulateCommunicationSecurityScore;
          total = manipulateCommunicationSecurityScoreDefault;
          scoreDetailInfo = manipulateVulns;
          break;
      }
      ReportUtilsImpl reportUtils = new ReportUtilsImpl();
      UUID id = reportUtils.getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(testPointName, formulaID).join();
      reportUtils.insertAndUpdateTestPointScoreInTestPointDetailTable(carModel, testTypeId, pinPair, id, testPointName,
          formulaID, score, total, scoreDetailInfo, updateTime).join();
    }
  }

  private void getDefaultScores(UUID gradingFormulaID){
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    readMemorySecurityScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.DUMP_ECU_MEMORY.value,
        gradingFormulaID).join();
    manipulateCommunicationSecurityScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(
        TestType.MANIPULATE_ECU_COMMUNICATION.value, gradingFormulaID).join();
  }

  public enum TestType {
    DUMP_ECU_MEMORY(TestPoints.READ_MEMORY_ADDRESS.getTestPoint().getName()),
    MANIPULATE_ECU_COMMUNICATION(TestPoints.MANIPULATE_COMMUNICATION.getTestPoint().getName()),
    UNKNOWN("UNKNOWN");

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
      return TestType.UNKNOWN;
    }
  }

  public String toString(){
    return TestType.DUMP_ECU_MEMORY.value + " score : " + readMemorySecurityScore
        + "/" + POINT_TEN + ", with number of security vulnerabilities : " + readMemorySecurityVulnerabilities
        + "\n"
        + TestType.MANIPULATE_ECU_COMMUNICATION.value + " score : " + manipulateCommunicationSecurityScore
        + "/" + POINT_TEN + ", with number of security vulnerabilities: " + manipulateVulns + "\n";
  }
}
