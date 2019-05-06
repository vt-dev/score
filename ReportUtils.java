package com.visualthreat.report.core.report;

import com.visualthreat.report.core.report.gradingFormula.AdditionalItem;
import com.visualthreat.report.core.report.gradingFormula.ReportGradingFormula;

import java.io.File;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ReportUtils {

  void calculateAndUpdateGradesForAllRecords();
  void calculateAndUpdateGradesForSelectedRecords(Collection<CarModelTestType> testReportNameList);

  // Operations for TEST_REPORT Table
  CompletableFuture<Boolean> insertAndUpdateScoreInTestReportTable(String carModel, UUID gradingFormulaID,
      double score, Timestamp updateTime, UUID testTypeId);

  // Operations for RAW_IMPORT_DATA table
  Collection<TestReportResult> getTestReportResults(final String dbPath, final String sniffFilePath);
  void loadResultDatabaseIntoReportDB(String dbPath, String sniffFilePath, String carModel,
      String pinPair, ReportGradingFormula reportGradingFormula, String testType);
  CompletableFuture<Boolean> insertAndUpdateRawImportDataTable(File dbFile, File sniffFile,
      String carModel, String pinPair, UUID gradingFormulaID, Collection<TestReportResult> testReportResultList, String testType);
  CompletableFuture<Boolean> insertRecordIntoTestReportTable(String carModel,
      String pinPair, UUID gradingFormulaID, String testReportResultsList, String testType);
  void updateGradeFormulaForCarModel(String carModel, UUID testTypeId, ReportGradingFormula reportGradingFormula);

  // Calculate score api
  void calculateAndUpdateGradeForCarModel(String carModel, UUID testTypeId);
  CompletableFuture<UUID> findFormulaIDByFormula(final ReportGradingFormula reportGradingFormula);

    // Operations for TESTPOINT_DETAIL and TESTPOINT_SCORE_DESCRIPTION tables
  CompletableFuture<Boolean> insertAndUpdateTestPointScoreInTestPointDetailTable(String carModel, UUID testTypeId,
      String pinPair, UUID id, String testPointName, UUID gradingFormulaID, double score, double total,
      String scoreDetailInfo, Timestamp updateTime);
  CompletableFuture<Double> getScoreFromTestPointScoreDescriptionTable(String testPointName, UUID gradingFormulaID);
  CompletableFuture<UUID> getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(String testPointName, UUID gradingFormulaID);
  // Name Hierarchy list order is from root to leaf
  CompletableFuture<List<String>> getNameHierarchyByIDFromTestPointScoreDescTable(UUID id);


  // Operations for ADDITIONAL_SCORES and ADDITIONAL_ITEM_DESCRIPTION tables
  CompletableFuture<Boolean> insertRecordIntoAdditionalScoreTable(String carModel, UUID testTypeId, UUID itemID, UUID parentID, String description);
  CompletableFuture<Integer> getScoreFromItemDescriptionTable(UUID itemId);
  CompletableFuture<List<UUID>> findItemIDFromAdditionalItemTable(List<String> nameHierarchy);
  Collection<AdditionalItem> getAdditionalItems(String carModel, UUID testTypeId);

  //test
  CompletableFuture<Integer> getAdditionalItemsCountForTestReport(String carModel, UUID testTypeId, String parentName);

  //Operations for GRADING_FORMULAS table
  CompletableFuture<Boolean> insertAndUpdateGradeFormulaIntoGradingFormulasTable(ReportGradingFormula reportGradingFormula);

  //Operations for TEST_TYPE table
  CompletableFuture<byte[]> getTestTypeID(String testType);
}
