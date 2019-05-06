package com.visualthreat.report.core.report.gradingFormula.v2;


import com.visualthreat.report.core.TestPoints;
import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class QuickScanECUInfoLeakFormula extends AbstractScoreFormula {

  private final static int POINT_TEN = 10;
  private final static int POINT_FIVE = 5;
  private final static double DEFAULT_PERCENTAGE = 0.6;

  double totalScore = 0;
  double ecuSystemProbingScore = 10 * DEFAULT_PERCENTAGE;
  double ecuScanScore = 5 * DEFAULT_PERCENTAGE;
  double ecuActivitiesScore = 5 * DEFAULT_PERCENTAGE;
  double ecuIDPerSecondsScore = 5 * DEFAULT_PERCENTAGE;
  double topThreeECUIDServicePercentageScore = 5 * DEFAULT_PERCENTAGE;
  double percentForServicesOfTopThreeECUIDs = 0;

  double ecuSystemProbingScoreDefault = 10;
  double ecuScanScoreDefault = 5;
  double ecuActivitiesScoreDefault = 5;
  double ecuIDPerSecondsScoreDefault = 5;
  double topThreeECUIDServicePercentageScoreDefault = 5;

  int totalNumECU = 0;
  int totalNumECUService = 0;
  int totalNumForTopThree = 0;
  int totalNumECUActivities = 0;
  int totalNumECUID = 0;

  @Override
  public double calculateScore(final String carModel,
                               final UUID testTypeId,
                               final String pinPair,
                               final UUID formulaID,
                               final List<TestReportResult> testReportResultList,
                               final Timestamp updateTime) {

    getDefaultScores(formulaID);

    for (final TestReportResult testReportResult : testReportResultList) {
      switch (TestType.getType(testReportResult.getTestPointName())) {
        case ECU_SYSTEM_PROBING:
          final String[] strings = testReportResult.getExtraInformationForPass().get(0).split(" ");
          totalNumECU = Integer.parseInt(strings[1].trim());
          if (totalNumECU < 10) {
            totalScore = totalScore + 10;
            ecuSystemProbingScore = 10;
          } else if (totalNumECU >= 10 && totalNumECU < 20) {
            totalScore = totalScore + 6;
            ecuSystemProbingScore = 6;
          } else if (totalNumECU >= 20) {
            totalScore = totalScore + 3;
            ecuSystemProbingScore = 3;
          }
          break;
        case ECU_SCAN:
          final String[] ecuStr = testReportResult.getExtraInformationForPass().get(0).split(" ");
          totalNumECUService = Integer.parseInt(ecuStr[1].trim());
          percentForServicesOfTopThreeECUIDs = getPercentOfServicesForTopThreeECUIDs(totalNumECUService,
              testReportResult.getExtraInformationForPass());
          if (totalNumECUService < 50) {
            totalScore = totalScore + 5;
            ecuScanScore = 5;
          } else if (totalNumECUService >= 50 && totalNumECUService < 100) {
            totalScore = totalScore + 4;
            ecuScanScore = 4;
          } else if (totalNumECUService >= 100) {
            totalScore = totalScore + 3;
            ecuScanScore = 3;
          }

          if (percentForServicesOfTopThreeECUIDs < 0.1) {
            totalScore = totalScore + 5;
            topThreeECUIDServicePercentageScore = 5;
          } else if (percentForServicesOfTopThreeECUIDs >= 0.1) {
            totalScore = totalScore + 4;
            topThreeECUIDServicePercentageScore = 4;
          } else if (percentForServicesOfTopThreeECUIDs >= 0.7) {
            totalScore = totalScore + 3;
            topThreeECUIDServicePercentageScore = 3;
          }
          break;
        case CAN_BUS_ACTIVITIES:
          final String[] ecuActivitiesStr = testReportResult.getExtraInformationForPass().get(0).trim().split(",");
          totalNumECUActivities = Integer.parseInt(ecuActivitiesStr[0].trim());
          if (totalNumECUActivities < 500) {
            totalScore = totalScore + 5;
            ecuActivitiesScore = 5;
          } else if (totalNumECUActivities >= 500 && totalNumECUActivities < 1000) {
            totalScore = totalScore + 4;
            ecuActivitiesScore = 4;
          } else if (totalNumECUActivities >= 1000) {
            totalScore = totalScore + 3;
            ecuActivitiesScore = 3;
          }
          break;
        case UNIQUE_ACTIVITIES_ECU_ID:
          final String uniqueIDString = testReportResult.getExtraInformationForPass().get(0).trim();
          totalNumECUID = Integer.parseInt(uniqueIDString.trim());
          if (totalNumECUID < 10) {
            totalScore = totalScore + 5;
            ecuIDPerSecondsScore = 5;
          } else if (totalNumECUID >= 10 && totalNumECUID < 50) {
            totalScore = totalScore + 4;
            ecuIDPerSecondsScore = 4;
          } else if (totalNumECUID >= 50) {
            totalScore = totalScore + 3;
            ecuIDPerSecondsScore = 3;
          }
          break;
        case UNKNOWN:
          break;
      }
    }
    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return totalScore;
  }

  private void insertIntoTestPointDetailTable(final String carModel, final UUID testTypeId, final String pinPair, final UUID formulaID, final Timestamp updateTime) {
    String testPointName = "";
    double score = 0;
    double total = 0;
    String scoreDetailInfo = "";
    for (final TestType testType : TestType.values()) {
      switch (testType) {
        case ECU_SYSTEM_PROBING:
          testPointName = TestType.ECU_SYSTEM_PROBING.value;
          score = ecuSystemProbingScore;
          total = ecuSystemProbingScoreDefault;
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
          scoreDetailInfo = "Found total " + totalNumECUActivities + " ECU Activities";
          break;
        case UNKNOWN:
          break;

      }
      final ReportUtilsImpl reportUtils = new ReportUtilsImpl();
      UUID id = reportUtils.getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(testPointName, formulaID).join();
      reportUtils.insertAndUpdateTestPointScoreInTestPointDetailTable(carModel, testTypeId, pinPair, id, testPointName,
          formulaID, score, total, scoreDetailInfo, updateTime);

      // Handle the other two cases
      testPointName = "Percentage for Number of Services of Top three ECU IDs";
      score = topThreeECUIDServicePercentageScore;
      total = topThreeECUIDServicePercentageScoreDefault;
      scoreDetailInfo = "Percentage for Number of Services of Top three ECU IDs is "
          + percentForServicesOfTopThreeECUIDs;
      id = reportUtils.getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(testPointName, formulaID).join();
      reportUtils.insertAndUpdateTestPointScoreInTestPointDetailTable(carModel, testTypeId, pinPair, id, testPointName,
          formulaID, score, total, scoreDetailInfo, updateTime);


      testPointName = "Unique ECU IDs per seconds";
      score = ecuIDPerSecondsScore;
      total = ecuIDPerSecondsScoreDefault;
      scoreDetailInfo = "total number of unique ECU ID per seconds is " + totalNumECUID;
      id = reportUtils.getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(testPointName, formulaID).join();
      reportUtils.insertAndUpdateTestPointScoreInTestPointDetailTable(carModel, testTypeId, pinPair, id, testPointName,
          formulaID, score, total, scoreDetailInfo, updateTime).join();
    }
  }

  private void getDefaultScores(final UUID gradingFormulaID) {
    final ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    ecuSystemProbingScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECU_SYSTEM_PROBING.value,
        gradingFormulaID).join();
    ecuScanScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECU_SCAN.value,
        gradingFormulaID).join();
    ecuActivitiesScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.CAN_BUS_ACTIVITIES.value,
        gradingFormulaID).join();
    ecuIDPerSecondsScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(
        "Unique ECU IDs per seconds", gradingFormulaID).join();
    topThreeECUIDServicePercentageScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(
        "Percentage for Number of Services of Top three ECU IDs", gradingFormulaID).join();
  }

  public enum TestType {
    ECU_SYSTEM_PROBING(TestPoints.UDS_DISCOVER_IDS.getTestPoint().getName()),
    ECU_SCAN(TestPoints.UDS_DISCOVER_SERVICES.getTestPoint().getName()),
    CAN_BUS_ACTIVITIES("CAN BUS Activities"),
    UNIQUE_ACTIVITIES_ECU_ID("Unique ECU ID"),
    UNKNOWN("UNKNOWN");

    private final String value;

    TestType(final String inVal) {
      this.value = inVal;
    }

    public String getCode() {
      return this.value;
    }

    public static boolean contains(final String testType) {

      for (final TestType value : TestType.values()) {
        if (value.getCode().equals(testType)) {
          return true;
        }
      }
      return false;
    }

    public static TestType getType(final String value) {
      for (final TestType testType : TestType.values()) {
        if (testType.getCode().equals(value)) {
          return testType;
        }
      }
      return TestType.UNKNOWN;
    }
  }

  public String toString() {
    return TestType.ECU_SYSTEM_PROBING.value + " score : " + ecuSystemProbingScore
        + "/" + POINT_TEN + ", with number of ECUs: " + totalNumECU + "\n"
        + TestType.ECU_SCAN.value + " score :" + ecuScanScore
        + "/" + POINT_FIVE + ", with number of ECU Services: " + totalNumECUService + "\n"
        + TestType.CAN_BUS_ACTIVITIES.value + " score : " + ecuActivitiesScore
        + "/" + POINT_FIVE + ", with number of can bus activities : " + totalNumECUActivities + "\n"
        + "Percentage for Number of Services of Top three ECU IDs score :" + topThreeECUIDServicePercentageScore
        + "/" + POINT_FIVE + ", with numServiceForTopThreeECU/totalNumServices : " + totalNumForTopThree + "/" + totalNumECUService + "\n"
        + "unique ECU IDs per seconds score :" + ecuIDPerSecondsScore
        + "/" + POINT_FIVE + ", with number of unique id per seconds : " + totalNumECUID + "\n";
  }

  private double getPercentOfServicesForTopThreeECUIDs(final int totalNumService, final List<String> ecuServicesList) {
    double percent = 0;
    final List<Integer> numOfServices = new LinkedList<>();
    ecuServicesList.remove(0);
    for (final String service : ecuServicesList) {
      final String[] serviceStr = service.split(" ");
      numOfServices.add(Integer.parseInt(serviceStr[1].trim()));
    }
    Collections.sort(numOfServices);
    Collections.reverse(numOfServices);
    for (int i = 0; i < 3; i++) {
      if (numOfServices.size() <= 0) {
        break;
      }
      totalNumForTopThree = totalNumForTopThree + numOfServices.remove(0);
    }
    percent = totalNumForTopThree / ((double) totalNumService);
    return percent;
  }
}
