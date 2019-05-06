package com.visualthreat.report.core.report.gradingFormula.v3;

import com.visualthreat.report.core.TestPoints;
import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class ECUInfoLeakFormulaV3 extends AbstractScoreFormula {

  private final static int POINT_FIVE = 5;
  private final static double DEFAULT_PERCENTAGE = 0.6;


  double totalScore = 0;
  double ecuProbingScore = 5 * DEFAULT_PERCENTAGE;
  double ecuScanScore = 3 * DEFAULT_PERCENTAGE;
  double ecuActivitiesScore = 5 * DEFAULT_PERCENTAGE;
  double uniqueECUIdScore = 2 * DEFAULT_PERCENTAGE;

  double ecuProbingScoreDefault = 5;
  double ecuScanScoreDefault = 3;
  double ecuActivitiesScoreDefault = 5;
  double uniqueECUIdScoreDefault = 2;

  int totalNumECU = 0;
  int totalNumECUService = 0;
  int totalNumECUActivities = 0;
  int totalNumUniqueECUId = 0;

  @Override
  public double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
                               List<TestReportResult> testReportResultList, Timestamp updateTime) {

    getDefaultScores(formulaID);
    getAvgScores();

    for (TestReportResult testReportResult : testReportResultList) {
      switch (TestType.getType(testReportResult.getTestPointName())) {
        case ECU_SYSTEM_PROBING:
          String[] strings = testReportResult.getExtraInformationForPass().get(0).split(" ");
          totalNumECU = Integer.parseInt(strings[1].trim());
          double percentage = getTestpointPercentage(10, 15, 25, 1, 2,
              -1, new int[]{60, 30}, totalNumECU);
          ecuProbingScore = ecuProbingScoreDefault * percentage;
          break;
        case ECU_SCAN:
          String[] ecuStr = testReportResult.getExtraInformationForPass().get(0).split(" ");
          totalNumECUService = Integer.parseInt(ecuStr[1].trim());
          percentage = getTestpointPercentage(100, 500, 1500, 50, 2,
              -1, new int[]{80, 60}, totalNumECUService);
          ecuScanScore = ecuScanScoreDefault * percentage;
          break;
        case CAN_BUS_ACTIVITIES:
          String[] str = testReportResult.getExtraInformationForPass().get(0).trim().split(",");
          totalNumECUActivities = Integer.parseInt(str[0].trim());
          percentage = getTestpointPercentage(500, 1000, 2000, 50, 2,
              -1, new int[]{80, 60}, totalNumECUActivities);
          ecuActivitiesScore = ecuActivitiesScoreDefault * percentage;
          break;
        case UNIQUE_ACTIVITIES_ECU_ID:
          totalNumUniqueECUId = Integer.parseInt(testReportResult.getExtraInformationForPass().get(0).trim());
          percentage = getTestpointPercentage(20, 50, 100, 1, 2,
              -1, new int[]{80, 60}, totalNumUniqueECUId);
          uniqueECUIdScore = uniqueECUIdScoreDefault * percentage;
          break;
        case UNKNOWN:
          break;
      }
    }
    totalScore = ecuProbingScore + ecuScanScore + ecuActivitiesScore + uniqueECUIdScore;
    BigDecimal b = new BigDecimal(totalScore);
    totalScore = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return totalScore;
  }

  private void insertIntoTestPointDetailTable(String carModel, UUID testTypeId, String pinPair, UUID formulaID, Timestamp updateTime) {
    String testPointName = "";
    double score = 0;
    double total = 0;
    String scoreDetailInfo = "";
    TestType[] testTypes = TestType.values();
    for (TestType testType : testTypes) {
      switch (testType) {
        case ECU_SYSTEM_PROBING:
          testPointName = TestType.ECU_SYSTEM_PROBING.value;
          score = ecuProbingScore;
          total = ecuProbingScoreDefault;
          scoreDetailInfo = "Found total " + totalNumECU + " ECUs";
          break;
        case ECU_SCAN:
          testPointName = TestType.ECU_SCAN.value;
          score = ecuScanScore;
          total = ecuScanScoreDefault;
          scoreDetailInfo = "Found total " + totalNumECUService + " ECU services";
          break;
        case CAN_BUS_ACTIVITIES:
          testPointName = TestType.CAN_BUS_ACTIVITIES.value;
          score = ecuActivitiesScore;
          total = ecuActivitiesScoreDefault;
          scoreDetailInfo = "Found total " + totalNumECUActivities + " ECU activities";
          break;
        case UNIQUE_ACTIVITIES_ECU_ID:
          testPointName = TestType.UNIQUE_ACTIVITIES_ECU_ID.value;
          score = uniqueECUIdScore;
          total = uniqueECUIdScoreDefault;
          scoreDetailInfo = "Found total " + totalNumUniqueECUId + " unique activities ecu ids";
          break;
        case UNKNOWN:
          break;

      }
      if (testPointName != null && !testPointName.equals("")) {
        insertIntoTestPointDetail(carModel, testTypeId, pinPair, formulaID, updateTime,
            testPointName, score, total, scoreDetailInfo);
        testPointName = "";
      }
    }
  }

  private void getDefaultScores(UUID gradingFormulaID) {
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    ecuProbingScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECU_SYSTEM_PROBING.value,
        gradingFormulaID).join();
    ecuScanScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECU_SCAN.value,
        gradingFormulaID).join();
    ecuActivitiesScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.CAN_BUS_ACTIVITIES.value,
        gradingFormulaID).join();
    uniqueECUIdScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.UNIQUE_ACTIVITIES_ECU_ID.value,
        gradingFormulaID).join();
  }

  private void getAvgScores() {
    ecuProbingScore = ecuProbingScoreDefault * DEFAULT_PERCENTAGE;
    ecuScanScore = ecuScanScoreDefault * DEFAULT_PERCENTAGE;
    ecuActivitiesScore = ecuActivitiesScoreDefault * DEFAULT_PERCENTAGE;
  }

  public enum TestType {
    ECU_SYSTEM_PROBING(TestPoints.UDS_DISCOVER_IDS.getTestPoint().getName()),
    ECU_SCAN(TestPoints.UDS_DISCOVER_SERVICES.getTestPoint().getName()),
    CAN_BUS_ACTIVITIES("CAN BUS Activities"),
    UNIQUE_ACTIVITIES_ECU_ID("Unique ECU ID"),
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

    public static TestType getType(String value) {
      for (TestType testType : TestType.values()) {
        if (testType.getCode().equals(value)) {
          return testType;
        }
      }
      return null;
    }

    public static int getTotalNum() {
      return TestType.values().length;
    }
  }

  public String toString() {
    return TestType.ECU_SYSTEM_PROBING.value + " score : " + ecuProbingScore
        + "/" + POINT_FIVE + ", with number of ECUs: " + totalNumECU + "\n"
        + TestType.ECU_SCAN.value + " score :" + ecuScanScore
        + "/" + POINT_FIVE + ", with number of ECU Services: " + totalNumECUService + "\n"
        + TestType.CAN_BUS_ACTIVITIES.value + " score : " + ecuActivitiesScore
        + "/" + POINT_FIVE + ", with number of can bus activities : " + totalNumECUActivities + "\n";
  }
}
