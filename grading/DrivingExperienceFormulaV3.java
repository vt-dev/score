package com.visualthreat.report.core.report.gradingFormula.v3;

import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class DrivingExperienceFormulaV3 extends AbstractScoreFormula {

  double drivingExperienceScore = 0;
  double drivingExperienceScoreDefault = 0;
  @Override
  public double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      List<TestReportResult> testReportResultList, Timestamp updateTime) {
    getDefaultScores(formulaID);
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    int drivingExperienceNum = reportUtils.getAdditionalItemsCountForTestReport(carModel, testTypeId,
        "Driving Experiences").join();
    if(drivingExperienceNum == 0){
      drivingExperienceScore = 10;
    }else if(drivingExperienceNum <=2){
      drivingExperienceScore = 6;
    }else {
      drivingExperienceScore = 0;
    }
    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return drivingExperienceScore;
  }

  private void insertIntoTestPointDetailTable(String carModel, UUID testTypeId, String pinPair, UUID formulaID, Timestamp updateTime){
    String testPointName = "Driving Experience";
    String scoreDetailInfo = "";
    insertIntoTestPointDetail(carModel, testTypeId, pinPair, formulaID, updateTime,
        testPointName, drivingExperienceScore, drivingExperienceScoreDefault, scoreDetailInfo);
  }

  private void getDefaultScores(UUID gradingFormulaID){
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    drivingExperienceScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable("Driving Experience",
        gradingFormulaID).join();
  }
}
