package com.visualthreat.report.core.report.gradingFormula.v3;

import com.visualthreat.report.core.TestPoints;
import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;
import com.visualthreat.report.core.report.gradingFormula.v1.ECUInfoLeakFormula;
import com.visualthreat.report.core.report.gradingFormula.v1.ECUInfoLeakFormula.TestType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class ECUDataSecurityFormulaV3 extends AbstractScoreFormula{

  private final static double DEFAULT_PERCENTAGE = 0.6;

  double totalScore = 0;

  // ECU READ
  double ecuReadScore = 5 * DEFAULT_PERCENTAGE;

  // ECU WRITE
  double ecuWriteScore = 5 * DEFAULT_PERCENTAGE;

  // ECU Manipulation
  double ecuManipulationScore = 5 * DEFAULT_PERCENTAGE;
  double privilegeScore = 8 * DEFAULT_PERCENTAGE;
  double parameterScore = 2 * DEFAULT_PERCENTAGE;

  // ECU Security
  double seedLengthScore = 5 * DEFAULT_PERCENTAGE;
  double seedRandomnessScore = 5 * DEFAULT_PERCENTAGE;
  double seedTimeoutScore = 5 * DEFAULT_PERCENTAGE;

  // ECU READ
  int ecuReadNumVulns = -1;

  // ECU WRITE
  int ecuWriteNumVulns = -1;

  // ECU Manipulation
  int ecuManipulationNumVulns = -1;
  int privilegeNumVulns = -1;
  int parameterNumVulns = -1;

  // ECU Security
  int seedLengthNumVulns = -1;
  int seedRandomnessNumVulns = -1;
  int seedTimeoutNumVulns = -1;

  // ECU READ
  double ecuReadScoreDefault = 5;

  // ECU WRITE
  double ecuWriteScoreDefault = 5;

  // ECU Manipulation
  double ecuManipulationScoreDefault = 5;
  double privilegeScoreDefault = 8;
  double parameterScoreDefault = 2;

  // ECU Security
  double seedLengthScoreDefault = 5;
  double seedRandomnessScoreDefault = 5;
  double seedTimeoutScoreDefault = 5;

  // ECU READ
  StringBuilder ecuReadVulns = new StringBuilder();

  // ECU WRITE
  StringBuilder ecuWriteVulns = new StringBuilder();

  // ECU Manipulation
  StringBuilder ecuManipulationVulns = new StringBuilder();
  StringBuilder privilegeVulns = new StringBuilder();
  StringBuilder parameterVulns = new StringBuilder();

  // ECU Security
  StringBuilder seedLengthVulns = new StringBuilder();
  StringBuilder seedRandomnessVulns = new StringBuilder();
  StringBuilder seedTimeoutVulns = new StringBuilder();
  boolean seedLengthSmallThanTwoExist = false;
  boolean seedRandomnessSmallerThanOneExist = false;

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
        case DumpECUMemory:
        case ECUDumpMemoryTwo:
        case ProtocolOneRetrieveECUSettings:
        case ProtocolOneRetrieveECURecord:
          ecuReadNumVulns = ecuReadNumVulns == -1 ? vulns : ecuReadNumVulns + vulns;
          ecuReadVulns.append(vulnsDetails);
          break;
        case ProtocolOneModifyECUSettings:
        case ModifyECUDataAttempt:
        case ModifyECUMemoryAttempt:
        case ProtocolTwoModifyMemory:
          ecuWriteNumVulns = ecuWriteNumVulns == -1 ? vulns : ecuWriteNumVulns + vulns;
          ecuWriteVulns.append(vulnsDetails);
          break;
        case ProtocolOneManipulationAttempt:
        case ScanRemoteInvocationVulnerability:
        case ManipulateECUFunctionAttempt:
        case ManipulateECUCommunication:
        case ManipulateECUCommunicationRate:
          ecuManipulationNumVulns = ecuManipulationNumVulns == -1 ? vulns : ecuManipulationNumVulns + vulns;
          ecuManipulationVulns.append(vulnsDetails);
          break;
        case ScanParameterTampering:
          parameterNumVulns = parameterNumVulns == -1 ? vulns : parameterNumVulns + vulns;
          parameterVulns.append(vulnsDetails);
          break;
        case ScanPrivilegeElevation:
          privilegeNumVulns = privilegeNumVulns == -1 ? vulns : privilegeNumVulns + vulns;
          privilegeVulns.append(vulnsDetails);
          break;
        case ProtocolTwoSecurityAccess:
        case ECUSecurityScan:
          seedLengthNumVulns = seedLengthNumVulns == -1 ? 0 : seedLengthNumVulns;
          seedRandomnessNumVulns = seedRandomnessNumVulns == -1 ? 0 : seedRandomnessNumVulns;
          seedTimeoutNumVulns = seedTimeoutNumVulns == -1 ? 0 : seedTimeoutNumVulns;
          categorizeSecurityInfo(testReportResult);
          break;
      }
    }

    TestType[] testTypes = TestType.values();
    for(TestType testType : testTypes){
      switch (testType) {
        case ECURead:
          if(ecuReadNumVulns == -1){
            break;
          }
          double percentage = getTestpointPercentage(0, 5, 20, 1, 5,
              50, new int[]{80, 60}, ecuReadNumVulns);
          ecuReadScore = ecuReadScoreDefault * percentage;
          break;
        case ECUWrite:
          if(ecuWriteNumVulns == -1){
            break;
          }
          percentage = getTestpointPercentage(0, 5, 20, 1, 5,
              50, new int[]{80, 60}, ecuWriteNumVulns);
          ecuWriteScore = ecuWriteScoreDefault * percentage;
          break;
        case ECUManipulation:
          if(ecuManipulationNumVulns == -1){
            break;
          }
          percentage = getTestpointPercentage(0, 10, 25, 1, 2,
              50, new int[]{60, 30}, ecuManipulationNumVulns);
          ecuManipulationScore = ecuManipulationScoreDefault * percentage;
          break;
        case ScanParameterTampering:
          if(parameterNumVulns == -1){
            break;
          }
          percentage = getTestpointPercentage(0, 10, 20, 1, 2,
              50, new int[]{60, 30}, parameterNumVulns);
          parameterScore = parameterScoreDefault * percentage;
          break;
        case ScanPrivilegeElevation:
          if(privilegeNumVulns == -1){
            break;
          }
          percentage = getTestpointPercentage(0, 10, 25, 1, 2,
              50, new int[]{60, 30}, privilegeNumVulns);
          privilegeScore = privilegeScoreDefault * percentage;
          break;
        case SecuritySeedLength:
          if(seedLengthNumVulns == -1){
            break;
          }
          percentage = getPercentage(TestType.SecuritySeedLength);
          seedLengthScore = seedLengthScoreDefault * percentage;
          break;
        case SecuritySeedRandomness:
          if(seedRandomnessNumVulns == -1){
            break;
          }
          percentage = getPercentage(TestType.SecuritySeedRandomness);
          seedRandomnessScore = seedRandomnessScoreDefault * percentage;
          break;
        case SecuritySeedTimeout:
          if(seedTimeoutNumVulns == -1){
            break;
          }
          percentage = seedTimeoutNumVulns > 0 ? 0 : 1;
          seedTimeoutScore = seedTimeoutScoreDefault * percentage;
          break;
      }
    }


    totalScore  = ecuReadScore + ecuWriteScore + ecuManipulationScore + privilegeScore
        + parameterScore + seedLengthScore + seedRandomnessScore + seedTimeoutScore;
    BigDecimal b = new BigDecimal(totalScore);
    totalScore = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return totalScore;
  }

  private double getPercentage(TestType testType) {
    int numVulns = 0;
    boolean existFlag = false;
    if(testType.value.equals(TestType.SecuritySeedLength.value)){
      numVulns = seedLengthNumVulns;
      existFlag = seedLengthSmallThanTwoExist;
    }else if(testType.value.equals(TestType.SecuritySeedRandomness.value)){
      numVulns = seedRandomnessNumVulns;
      existFlag = seedRandomnessSmallerThanOneExist;
    }
    double percentage;
    if(numVulns == 0){
      percentage = 1;
    }else if(numVulns > 0 && existFlag){
      percentage = 0;
    } else {
      percentage = 60 - numVulns * 5;
      percentage = percentage >= 40 ? percentage : 40;
      percentage = percentage / 100;
    }
    return percentage;
  }

  private void categorizeSecurityInfo(TestReportResult testReportResult){
    List<String> vulns = testReportResult.getSecurityVulnerabilitiesInfos();
    vulns.addAll(testReportResult.getWarningsInfos());
    for(String securityVuln : vulns){
      if(securityVuln.contains("Seed length for ECU") || securityVuln.contains("No seeds needed")){
        seedLengthVulns.append(securityVuln).append("\n");
        seedLengthNumVulns = seedLengthNumVulns == -1 ? 1 : seedLengthNumVulns + 1;
        if(securityVuln.contains("2")
            || securityVuln.contains("1")
            || securityVuln.contains("0")){
          seedLengthSmallThanTwoExist = true;
        }
      }else if(securityVuln.contains("Seed Unpredictability test failed")){
        seedRandomnessVulns.append(securityVuln).append("\n");
        seedRandomnessNumVulns = seedRandomnessNumVulns == -1 ? 1 : seedRandomnessNumVulns + 1;
        if(securityVuln.contains("1/")){
          seedRandomnessSmallerThanOneExist = true;
        }
      }else if(securityVuln.contains("Unlocking ECU with random security seed test failed")
          || securityVuln.contains("Time Delay test failed")
          || securityVuln.contains("Length of time delay test failed")
          || securityVuln.contains("Number of attempts are allowed before time delay")){
        seedTimeoutVulns.append(securityVuln).append("\n");
        seedTimeoutNumVulns = seedTimeoutNumVulns == -1 ? 1 : seedTimeoutNumVulns + 1;
      }
    }
  }

  private void insertIntoTestPointDetailTable(String carModel, UUID testTypeId, String pinPair, UUID formulaID, Timestamp updateTime){
    String testPointName = "";
    double score = 0;
    double total = 0;
    String scoreDetailInfo = "";
    TestType[] testTypes = TestType.values();
    for(TestType testType : testTypes){
      switch (testType){
        case ECURead:
          testPointName = TestType.ECURead.value;
          score = ecuReadScore;
          total = ecuReadScoreDefault;
          scoreDetailInfo = ecuReadVulns.toString();
          break;
        case ECUWrite:
          testPointName = TestType.ECUWrite.value;
          score = ecuWriteScore;
          total = ecuWriteScoreDefault;
          scoreDetailInfo = ecuWriteVulns.toString();
          break;
        case ECUManipulation:
          testPointName = TestType.ECUManipulation.value;
          score = ecuManipulationScore;
          total = ecuManipulationScoreDefault;
          scoreDetailInfo = ecuManipulationVulns.toString();
          break;
        case SecuritySeedLength:
          testPointName = TestType.SecuritySeedLength.value;
          score = seedLengthScore;
          total = seedLengthScoreDefault;
          scoreDetailInfo = seedLengthVulns.toString();
          break;
        case SecuritySeedRandomness:
          testPointName = TestType.SecuritySeedRandomness.value;
          score = seedRandomnessScore;
          total = seedRandomnessScoreDefault;
          scoreDetailInfo = seedRandomnessVulns.toString();
          break;
        case SecuritySeedTimeout:
          testPointName = TestType.SecuritySeedTimeout.value;
          score = seedTimeoutScore;
          total = seedTimeoutScoreDefault;
          scoreDetailInfo = seedTimeoutVulns.toString();
          break;
        case ScanParameterTampering:
          testPointName = TestType.ScanParameterTampering.value;
          score = parameterScore;
          total = parameterScoreDefault;
          scoreDetailInfo = parameterVulns.toString();
          break;
        case ScanPrivilegeElevation:
          testPointName = TestType.ScanPrivilegeElevation.value;
          score = privilegeScore;
          total = privilegeScoreDefault;
          scoreDetailInfo = privilegeVulns.toString();
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
    ecuReadScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECURead.value,
        gradingFormulaID).join();
    ecuWriteScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECUWrite.value,
        gradingFormulaID).join();

    ecuManipulationScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ECUManipulation.value,
        gradingFormulaID).join();
    privilegeScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ScanPrivilegeElevation.value,
        gradingFormulaID).join();
    parameterScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.ScanParameterTampering.value,
        gradingFormulaID).join();
    // ECU Security
    seedLengthScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.SecuritySeedLength.value,
        gradingFormulaID).join();
    seedTimeoutScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.SecuritySeedTimeout.value,
        gradingFormulaID).join();
    seedRandomnessScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable(TestType.SecuritySeedRandomness.value,
        gradingFormulaID).join();
  }

  private void getAvgScores(){
    // ECU READ
    ecuReadScore = ecuReadScoreDefault * DEFAULT_PERCENTAGE;
    // ECU WRITE
    ecuWriteScore = ecuWriteScoreDefault * DEFAULT_PERCENTAGE;
    // ECU Manipulation
    ecuManipulationScore = ecuManipulationScoreDefault * DEFAULT_PERCENTAGE;
    privilegeScore = privilegeScoreDefault * DEFAULT_PERCENTAGE;
    parameterScore = parameterScoreDefault * DEFAULT_PERCENTAGE;
    // ECU Security
    seedLengthScore = seedLengthScoreDefault * DEFAULT_PERCENTAGE;
    seedRandomnessScore = seedRandomnessScoreDefault * DEFAULT_PERCENTAGE;
    seedTimeoutScore = seedTimeoutScoreDefault * DEFAULT_PERCENTAGE;
  }

  public enum TestType {
    // ECU READ
    DumpECUMemory(TestPoints.READ_MEMORY_ADDRESS.getTestPoint().getName()),
    ECUDumpMemoryTwo(TestPoints.REQUEST_UPLOAD.getTestPoint().getName()),
    ProtocolOneRetrieveECUSettings(TestPoints.GMLAN_READ_DATA_BY_IDENTIFIER.getTestPoint().getName()),
    ProtocolOneRetrieveECURecord(TestPoints.GMLAN_READ_FAILURE_RECORD.getTestPoint().getName()),
    ECURead("ECU READ"),
    // ECU WRITE
    ProtocolOneModifyECUSettings(TestPoints.GMLAN_WRITE_IDENTIFIER.getTestPoint().getName()),
    ModifyECUDataAttempt(TestPoints.WRITE_DATA_BY_IDENTIFIER.getTestPoint().getName()),
    ModifyECUMemoryAttempt(TestPoints.WRITE_MEMORY_ADDRESS.getTestPoint().getName()),
    ProtocolTwoModifyMemory(TestPoints.XCP_MODIFY_MEMORY.getTestPoint().getName()),
    ECUWrite("ECU WRITE"),
    // ECU Manipulation
    ProtocolOneManipulationAttempt(TestPoints.GMLAN_DEVICE_CONTROL.getTestPoint().getName()),
    ManipulateECUFunctionAttempt(TestPoints.IO_CONTROL_BY_IDENTIFIER.getTestPoint().getName()),
    ScanPrivilegeElevation(TestPoints.SCAN_UDS_SERVICE_VULNERABILITIES.getTestPoint().getName()),
    ScanRemoteInvocationVulnerability(TestPoints.SCAN_ROUTINE_CONTROL_VULNERABILITIES.getTestPoint().getName()),
    ScanParameterTampering(TestPoints.SCAN_UDS_SUB_FUNCTION_VULNERABILITIES.getTestPoint().getName()),
    ManipulateECUCommunication(TestPoints.MANIPULATE_COMMUNICATION.getTestPoint().getName()),
    ManipulateECUCommunicationRate(TestPoints.LINK_CONTROL.getTestPoint().getName()),
    ECUManipulation("ECU MANIPULATION"),

    // ECU Security
    ProtocolTwoSecurityAccess(TestPoints.XCP_SECURITY_ACCESS.getTestPoint().getName()),
    ECUSecurityScan(TestPoints.SECURITY_ACCESS.getTestPoint().getName()),
    SecuritySeedLength("Security Seed Length"),
    SecuritySeedRandomness("Security Seed Randomness"),
    SecuritySeedTimeout("Security Seed Timeout");

    private String value;

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
