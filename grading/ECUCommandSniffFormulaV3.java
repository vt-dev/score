package com.visualthreat.report.core.report.gradingFormula.v3;

import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import com.visualthreat.report.core.report.gradingFormula.AbstractScoreFormula;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class ECUCommandSniffFormulaV3 extends AbstractScoreFormula{

  double ecuCommandSniffScore = 0;
  double ecuCommandSniffScoreDefault = 0;

  @Override
  public double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      List<TestReportResult> testReportResultList, Timestamp updateTime) {
    getDefaultScores(formulaID);
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    int commandSniffedNum = reportUtils.getAdditionalItemsCountForTestReport(carModel, testTypeId, "Signals Sniffed").join();
    if(commandSniffedNum == 0){
      ecuCommandSniffScore = 10;
    }else if(commandSniffedNum <=5){
      ecuCommandSniffScore = 6;
    }else if(commandSniffedNum <= 10){
      ecuCommandSniffScore = 10 * ((6 - (commandSniffedNum - 5)) * 0.1);
      ecuCommandSniffScore = ecuCommandSniffScore < 2 ? 2 : ecuCommandSniffScore;
    }else {
      ecuCommandSniffScore = 0;
    }
    insertIntoTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    return ecuCommandSniffScore;
  }

  private void insertIntoTestPointDetailTable(String carModel, UUID testTypeId, String pinPair, UUID formulaID, Timestamp updateTime){
    String testPointName = "ECU Command Sniff";
    String scoreDetailInfo = "";
    insertIntoTestPointDetail(carModel, testTypeId, pinPair, formulaID, updateTime,
        testPointName, ecuCommandSniffScore, ecuCommandSniffScoreDefault, scoreDetailInfo);
  }

  private void getDefaultScores(UUID gradingFormulaID){
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    ecuCommandSniffScoreDefault = reportUtils.getScoreFromTestPointScoreDescriptionTable("ECU Command Sniff",
        gradingFormulaID).join();
  }
}
