package com.visualthreat.report.core.report.gradingFormula;

import com.visualthreat.report.core.report.ReportUtilsImpl;
import com.visualthreat.report.core.report.TestReportResult;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

public abstract class AbstractScoreFormula {

  protected abstract double calculateScore(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      List<TestReportResult> testReportResultList, Timestamp updateTime);

  protected String getVulnerabilityDetailInfo(TestReportResult testReportResult){
    StringBuilder result = new StringBuilder();
    for(String securityVuln : testReportResult.getSecurityVulnerabilitiesInfos()){
      result.append(securityVuln).append("\n");
    }
    for(String warning : testReportResult.getWarningsInfos()){
      result.append(warning).append("\n");
    }
    return result.toString().trim();
  }

  protected double getTestpointScore(int maxNum, int actualNum, int minNum, double defaultScore){
    double score;
    if(actualNum <= minNum){
      score = defaultScore;
    }else{
      double tempScore = defaultScore - defaultScore * (actualNum - minNum)/maxNum;
      score = tempScore < 0 ? 0 : tempScore;
    }
    BigDecimal b = new BigDecimal(score);
    score = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    return score;
  }

  protected double getTestpointPercentage(int first, int second, int third, int fourth, int five,
      int six, int[] percentageDefault, int actualNum){
    double percentage = 0;
    int firstPercentage = percentageDefault[0];
    int secondPercentage = percentageDefault[1];

    if(six > 0 && actualNum > six){
      return 0;
    }

    if(actualNum <= first){
      percentage = 100;
    }else if(actualNum <= second){
      percentage = firstPercentage;
    }else if(actualNum >= second && actualNum <= third){
      percentage = firstPercentage - ((actualNum - second) / fourth) * five;
      percentage = percentage < (firstPercentage - 10) ? (firstPercentage - 10) : percentage;
    }else if(actualNum >= third){
      percentage = secondPercentage - ((actualNum - third) / fourth) * five;
      percentage = percentage < (secondPercentage - 20) ? (secondPercentage - 20) : percentage;
    }
    percentage = percentage / 100;
    BigDecimal b = new BigDecimal(percentage);
    percentage = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    return percentage;
  }

  protected void insertIntoTestPointDetail(String carModel, UUID testTypeId, String pinPair, UUID formulaID,
      Timestamp updateTime, String testPointName, double score, double scoreDefault, String scoreDetailInfo) {
    ReportUtilsImpl reportUtils = new ReportUtilsImpl();
    UUID id = reportUtils.getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(testPointName, formulaID).join();
    reportUtils.insertAndUpdateTestPointScoreInTestPointDetailTable(carModel, testTypeId, pinPair, id, testPointName,
        formulaID, score, scoreDefault, scoreDetailInfo, updateTime).join();
  }
}
