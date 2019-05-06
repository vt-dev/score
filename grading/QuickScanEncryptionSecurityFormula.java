package com.visualthreat.report.core.report.gradingFormula.v2;

import com.visualthreat.report.core.TestPoints;
import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class QuickScanEncryptionSecurityFormula extends AbstractScoreFormula {

  private final static int POINT_TWENTYFIVE = 25;
  private final static double DEFAULT_PERCENTAGE = 0.6;

  int totalScore = 0;
  double securityScore = 25 * DEFAULT_PERCENTAGE;
  double securityScoreDefault = 25;
  int numSecurityVulnerabilities = 0;
  String securityVulns = "";

  @Override
  public double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      List<TestReportResult> testReportResultList, Timestamp updateTime) {
    // First get the default scores from db
    getDefaultScores(formulaID);

    for(TestReportResult testReportResult : testReportResultList){
      String vulnsDetails = getVulnerabilityDetailInfo(testReportResult);

      switch (TestType.getType(testReportResult.getTestPointName())){
        case ECU_SECURITY_SCAN:
          numSecurityVulnerabilities = testReportResult.getNumSecurityVulnerabilities()
              + testReportResult.getNumWarnings();
          if(numSecurityVulnerabilities < 1){
            totalScore = totalScore + 25;
            securityScore = 25;
          }else if(numSecurityVulnerabilities >=1 && numSecurityVulnerabilities < 2){
            totalScore = totalScore + 19;
            securityScore = 19;
          }else if(numSecurityVulnerabilities >=2 && numSecurityVulnerabilities < 3){
            totalScore = totalScore +12;
            securityScore = 12;
          }else if(numSecurityVulnerabilities >= 3){
            totalScore = totalScore +8;
            securityScore = 8;
          }
          securityVulns = vulnsDetails;
          break;
        case UNKNOWN:
          break;
      }
    }

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
        case ECU_SECURITY_SCAN:
          testPointName = TestType.ECU_SECURITY_SCAN.value;
          score = securityScore;
          total = securityScoreDefault;
          scoreDetailInfo = securityVulns;
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
    securityScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECU_SECURITY_SCAN.value,
        gradingFormulaID).join();
  }

  public enum TestType {
    ECU_SECURITY_SCAN(TestPoints.SECURITY_ACCESS.getTestPoint().getName()),
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
    return TestType.ECU_SECURITY_SCAN.value + " score : " + securityScore
        + "/" + POINT_TWENTYFIVE + ", with number of security vulnerabilities: " + numSecurityVulnerabilities + "\n";
  }
}
