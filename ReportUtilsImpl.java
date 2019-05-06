package com.visualthreat.report.core.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualthreat.analytics.can.LogEntryIterator;
import com.visualthreat.platform.common.can.CANLogEntry;
import com.visualthreat.report.core.report.TestReportResult.TestResultStatus;
import com.visualthreat.report.core.report.gradingFormula.*;
import com.visualthreat.report.core.report.gradingFormula.v3.ECUInfoLeakFormulaV3.TestType;
import com.visualthreat.report.core.report.gradingFormula.v3.ReportGradingFormulaThree;
import com.visualthreat.report.database.DatabaseInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jooq.Field;
import org.jooq.Record;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.visualthreat.report.database.schema.tables.RawImportData.RAW_IMPORT_DATA;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Slf4j
public class ReportUtilsImpl implements ReportUtils {
  private static final DatabaseInstance DB = DatabaseInstance.db();
  private static final String DB_POSTFIX = ".h2.db";
  private static final ObjectMapper json = new ObjectMapper();
  private static final Field<byte[]> TEST_TYPE_ID = field("TEST_TYPE_ID", byte[].class);

  @Override
  public Collection<AdditionalItem> getAdditionalItems(final String carModel, final UUID testTypeId) {

    final Map<UUID, String> usedItems = DB.runDbQuery(dsl ->
        dsl.select(
            field("ID", byte[].class),
            field("DESCRIPTION", String.class)
        )
            .from(table("ADDITIONAL_SCORES"))
            .where(field("CAR_MODEL").eq(carModel))
            .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
            .fetch().stream()
            .collect(Collectors.toMap(
                r -> asUuid(r.get(field("ID", byte[].class))),
                r -> r.get(field("DESCRIPTION", String.class))))
    ).join();

    // get whole table for additional items
    final Collection<Record> records = DB.runDbQuery(dsl ->
        dsl.selectFrom(table("ADDITIONAL_ITEM")).fetch()
    ).join();

    // build whole tree for additional items (recursive)
    final Collection<AdditionalItem> allItems = records.stream()
        .filter(r -> r.get(field("PARENT_ID")) == null) // start from roots
        .map(r -> buildAdditionalItem(r, records))
        .sorted(Comparator.comparing(AdditionalItem::getName))
        .collect(Collectors.toList());

    // flatten a tree
    final Collection<AdditionalItem> flatItems = allItems.stream()
        .flatMap(a -> a.flatChildren().stream())
        .collect(Collectors.toList());

    // add descriptions
    flatItems.forEach(i -> {
      final String desc = usedItems.get(i.getId());
      if (desc != null) {
        i.setDescription(desc);
      }
    });

    // filter flat tree by used ids, to get used ids
    final Collection<UUID> usedEndIDs = usedItems.keySet();
    final Collection<UUID> allUsedIDs = flatItems.stream()
        .filter(i -> usedEndIDs.contains(i.getId()))
        .flatMap(i -> i.flatParents().stream())
        .map(AdditionalItem::getId)
        .collect(Collectors.toSet());

    allItems.forEach(i -> removeAdditionalItemChildren(i, allUsedIDs));

    return allItems;
  }

  private void removeAdditionalItemChildren(final AdditionalItem item, final Collection<UUID> usedIDs) {
    for (final AdditionalItem child : item.getItems()) {
      if (!usedIDs.contains(child.getId())) {
        child.setExcluded(true);
      } else {
        removeAdditionalItemChildren(child, usedIDs);
      }
    }
  }

  private AdditionalItem buildAdditionalItem(final Record record,
                                             final Collection<Record> records) {
    final byte[] byteId = record.getValue(field("ID", byte[].class));
    final UUID id = asUuid(byteId);
    final UUID parentID = asUuid(record.getValue(field("PARENT_ID", byte[].class)));
    final String name = record.getValue(field("NAME", String.class));
    final Integer score = record.getValue(field("SCORE", Integer.class));
    final Double multiplier = record.getValue(field("MULTIPLIER", Double.class));

    final Collection<Record> children = records.stream()
        .filter(c -> Arrays.equals(c.getValue("PARENT_ID", byte[].class), byteId))
        .sorted(Comparator.comparing(c -> c.getValue(field("NAME", String.class))))
        .collect(Collectors.toList());

    final AdditionalItem item = new AdditionalItem(id, parentID, null,
        name, "", score, multiplier, false, Collections.emptyList());

    if (!children.isEmpty()) {
      final Collection<AdditionalItem> childrenItems = children.stream()
          .map(c -> buildAdditionalItem(c, records))
          .peek(c -> c.setParent(item))
          .collect(Collectors.toList());
      item.setItems(childrenItems);
    }

    return item;
  }

  // Operations for ADDITIONAL_SCORES and ADDITIONAL_ITEM Table
  @Override
  public CompletableFuture<Boolean> insertRecordIntoAdditionalScoreTable(final String carModel, final UUID testTypeId,
                                                                         final UUID itemID, final UUID parentID,
                                                                         final String description) {
    return DB.runDbQuery(dsl -> {
      try {
        return dsl.mergeInto(table("ADDITIONAL_SCORES"), field("CAR_MODEL"),
            TEST_TYPE_ID, field("ID"), field("PARENT_ID"), field("DESCRIPTION"))
            .values(carModel, asBytes(testTypeId), asBytes(itemID), asBytes(parentID), description)
            .execute() > 0;
      } catch (final Exception e) {
        log.error("Can't insert into additional_scores table", e);
        return false;
      }
    });
  }

  @Override
  public CompletableFuture<Integer> getScoreFromItemDescriptionTable(final UUID itemId) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("SCORE"))
          .from(table("ADDITIONAL_ITEM"))
          .where(field("ID").eq(asBytes(itemId)))
          .fetchOne();
      return Integer.valueOf(record.getValue(field("SCORE")).toString());
    });
  }

  @Override
  public CompletableFuture<List<UUID>> findItemIDFromAdditionalItemTable(final List<String> nameHierarchy) {
    return DB.runDbQuery(dsl -> {
      UUID parentID = null;
      UUID id = null;
      while (!nameHierarchy.isEmpty()) {
        parentID = id;
        id = findItemIDByNameAndParentIdFromAdditionalItemTable(nameHierarchy.remove(0), parentID).join();
      }
      final List<UUID> ids = new LinkedList<>();
      ids.add(id);
      ids.add(parentID);
      return ids;
    });
  }

  @Override
  public CompletableFuture<Integer> getAdditionalItemsCountForTestReport(final String carModel, final UUID testTypeId, final String parentName) {
    final UUID parentID = getParentIDByNameFromAdditionalItemTable(parentName).join();
    return DB.runDbQuery(dsl -> {
      final Collection<Record> recordList = new ArrayList<>(dsl.select(field("ID"))
          .from(table("ADDITIONAL_SCORES"))
          .where(field("PARENT_ID").eq(asBytes(parentID)))
          .and(field("CAR_MODEL").eq(carModel))
          .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
          .fetch());
      return recordList.size();
    });
  }

  private CompletableFuture<UUID> getParentIDByNameFromAdditionalItemTable(final String name) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("ID"))
          .from(table("ADDITIONAL_ITEM"))
          .where(field("NAME").eq(name))
          .fetchOne();
      return asUuid(record.getValue(field("ID", byte[].class)));
    });
  }

  private CompletableFuture<UUID> findItemIDByNameAndParentIdFromAdditionalItemTable(final String name, final UUID parentID) {
    return DB.runDbQuery(dsl -> {
      Record record = null;
      if (parentID == null) {
        record = dsl.select(field("ID", byte[].class))
            .from(table("ADDITIONAL_ITEM"))
            .where(field("NAME").eq(name))
            .fetchOne();
      } else {
        record = dsl.select(field("ID", byte[].class))
            .from(table("ADDITIONAL_ITEM"))
            .where(field("NAME").eq(name), field("PARENT_ID").eq(asBytes(parentID)))
            .fetchOne();
      }
      return asUuid(record.getValue(field("ID", byte[].class)));
    });
  }

  private static UUID createItemIDList(final Record record) {
    return record.get(field("ID")) == null ?
        UUID.randomUUID() :
        asUuid(record.get(field("ID", byte[].class)));
  }

  // Operations for TEST_REPORT Table

  @Override
  public CompletableFuture<Boolean> insertAndUpdateScoreInTestReportTable(final String carModel, final UUID gradingFormulaID,
                                                                          final double score, final Timestamp updateTime,
                                                                          final UUID testTypeId) {
    return DB.runDbQuery(dsl -> {
      try {
        return dsl.mergeInto(table("TEST_REPORT"), field("CAR_MODEL"), field("GRADING_FORMULA_ID"),
            field("SCORE"), field("UPDATE_TIME"), TEST_TYPE_ID)
            .values(carModel, gradingFormulaID.toString(), score, updateTime, asBytes(testTypeId))
            .execute() > 0;
      } catch (final Exception e) {
        log.error("Can't insert into test_report table", e);
        return false;
      }
    });
  }


  // Operations for RAW_IMPORT_DATA table

  @Override
  public Collection<TestReportResult> getTestReportResults(final String dbPath, final String sniffFilePath) {
    final List<TestReportResult> testReportResultsList;
    try (final DatabaseInstance fileDB = DatabaseInstance.fromFile(dbPath)) {
      testReportResultsList = getTestReportResultList(fileDB);
    }
    // Get CAN Bus Activities info from Sniff file and add into TestReportResultList
    if (!testpointExists(testReportResultsList, TestType.CAN_BUS_ACTIVITIES.getCode())) {
      testReportResultsList.addAll(addCANBusActivitiesIntoTestReportResult(sniffFilePath));
    }
    return testReportResultsList;
  }

  @Override
  public void loadResultDatabaseIntoReportDB(final String dbPath, final String sniffFilePath, final String carModel,
                                             final String pinPair, final ReportGradingFormula reportGradingFormula,
                                             final String testType) {
    // Use test db
    final Collection<TestReportResult> testReportResultsList = getTestReportResults(dbPath, sniffFilePath);
    final UUID gradingFormulaID = findFormulaIDByFormula(reportGradingFormula).join();
    insertAndUpdateRawImportDataTable(generateFile(dbPath + DB_POSTFIX), generateFile(sniffFilePath),
        carModel, pinPair, gradingFormulaID, testReportResultsList, testType).join();
  }

  public void loadRecordIntoReportDB(final String carModel, final String pinPair,
                                     final ReportGradingFormula reportGradingFormula,
                                     final String testReportString, final String testType) {
    final UUID gradingFormulaID = findFormulaIDByFormula(reportGradingFormula).join();
    insertRecordIntoTestReportTable(carModel, pinPair, gradingFormulaID, testReportString, testType).join();
  }

  @Override
  public CompletableFuture<Boolean> insertAndUpdateRawImportDataTable(final File dbFile, final File sniffFile,
                                                                      final String carModel, final String pinPair,
                                                                      final UUID gradingFormulaID,
                                                                      final Collection<TestReportResult> testReportResultList,
                                                                      final String testType) {
    return DB.runDbQuery(dsl -> {
      try {
        final byte[] dbFileContent;
        final byte[] sniffFileContent;
        try {
          dbFileContent = FileUtils.readFileToByteArray(dbFile);
          sniffFileContent = FileUtils.readFileToByteArray(sniffFile);
        } catch (final IOException e) {
          throw new IOException("Unable to convert file to byte array. " + e.getMessage());
        }

        // Handle testReportResult list
        String testReportResultsList = "";
        try {
          testReportResultsList = json.writeValueAsString(testReportResultList);
        } catch (final JsonProcessingException e) {
          log.error("Can't encode {} to JSON", testReportResultList);
        }

        // Handle upload time
        final Date date = new java.util.Date();
        final Timestamp uploadTime = new Timestamp(date.getTime());

        // Handle TEST_TYPE
        final byte[] testTypeID = getTestTypeID(testType).join();

        return dsl.mergeInto(table("RAW_IMPORT_DATA"),
            field("CAR_MODEL"), TEST_TYPE_ID, field("PIN"), field("RAW_DB"),
            field("SNIFF_FILE"), field("TEST_RESULT"), field("GRADING_FORMULA_ID"),
            field("UPLOAD_TIME"))
            .values(carModel, testTypeID, pinPair, dbFileContent, sniffFileContent,
                testReportResultsList, gradingFormulaID, uploadTime)
            .execute() > 0;
      } catch (final Exception e) {
        log.error("Can't insert into raw_import_data table", e);
        return false;
      }
    });
  }

  @Override
  public CompletableFuture<Boolean> insertRecordIntoTestReportTable(final String carModel, final String pinPair,
                                                                    final UUID gradingFormulaID, final String testReportResultsList,
                                                                    final String testType) {
    return DB.runDbQuery(dsl -> {
      try {
        // Handle upload time
        final Date date = new java.util.Date();
        final Timestamp uploadTime = new Timestamp(date.getTime());

        // Handle TEST_TYPE
        final byte[] testTypeID = getTestTypeID(testType).join();

        return dsl.mergeInto(table("RAW_IMPORT_DATA"),
            field("CAR_MODEL"), TEST_TYPE_ID, field("PIN"), field("RAW_DB"),
            field("SNIFF_FILE"), field("TEST_RESULT"), field("GRADING_FORMULA_ID"),
            field("UPLOAD_TIME"))
            .values(carModel, testTypeID, pinPair, null, null,
                testReportResultsList, gradingFormulaID, uploadTime)
            .execute() > 0;
      } catch (final Exception e) {
        log.error("Can't insert into RAW_IMPORT_DATA table", e);
        return false;
      }
    });
  }

  @Override
  public void updateGradeFormulaForCarModel(final String carModel, final UUID testTypeId,
                                            final ReportGradingFormula reportGradingFormula) {
    final UUID gradingFormulaID = findFormulaIDByFormula(reportGradingFormula).join();
    updateGradeFormulaForRecordInRawImportDataTable(carModel, testTypeId, gradingFormulaID).join();
  }

  private CompletableFuture<Boolean> updateGradeFormulaForRecordInRawImportDataTable(final String carModel,
                                                                                     final UUID testTypeId,
                                                                                     final UUID gradingFormulaID) {
    return DB.runDbQuery(dsl -> {
      try {
        final Collection<Record> recordList = new ArrayList<>(dsl.select(field("PIN"))
            .from(table("RAW_IMPORT_DATA"))
            .where(field("CAR_MODEL").eq(carModel))
            .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
            .fetch());
        boolean insertStatus = true;
        if (!recordList.isEmpty()) {
          for (final Record record : recordList) {
            final String pinPair = record.getValue(field("PIN")).toString();
            insertStatus = insertStatus && dsl.mergeInto(table("RAW_IMPORT_DATA"),
                field("CAR_MODEL"), TEST_TYPE_ID, field("PIN"), field("GRADING_FORMULA_ID"))
                .values(carModel, asBytes(testTypeId), pinPair, gradingFormulaID)
                .execute() > 0;
          }
        }
        return insertStatus;
      } catch (final Exception e) {
        log.error("Can't insert into RAW_IMPORT_DATA table", e);
        return false;
      }
    });
  }

  private CompletableFuture<UUID> findFormulaIDByCarModelInRawImportDataTable(final String carModel,
                                                                              final UUID testTypeId) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("GRADING_FORMULA_ID"))
          .from(table("RAW_IMPORT_DATA"))
          .where(field("CAR_MODEL").eq(carModel))
          .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
          .limit(1)
          .fetchOne();
      return record == null ? null : UUID.fromString(record.getValue(field("GRADING_FORMULA_ID")).toString());
    });
  }

  private CompletableFuture<Collection<String>> findPinPairsByCarModelInRawImportDataTable(final String carModel,
                                                                                           final UUID testTypeId) {
    return DB.runDbQuery(dsl -> {
      try {
        final Collection<String> pinPairs = new ArrayList<>();
        final Collection<Record> recordList = new ArrayList<>(dsl.select(field("PIN"))
            .from(table("RAW_IMPORT_DATA"))
            .where(field("CAR_MODEL").eq(carModel))
            .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
            .fetch());
        if (!recordList.isEmpty()) {
          for (final Record record : recordList) {
            pinPairs.add(record.getValue(field("PIN")).toString());
          }
        }
        return pinPairs;
      } catch (final Exception e) {
        log.error("Can't get pinPairs from RAW_IMPORT_DATA table", e);
        return null;
      }
    });
  }

  private CompletableFuture<Collection<TestReportResult>> findTestReportResultsListByCarModelAndPin(final String carModel,
                                                                                                    final UUID testTypeId,
                                                                                                    final String pinPair) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(RAW_IMPORT_DATA.TEST_RESULT)
          .from(RAW_IMPORT_DATA)
          .where(RAW_IMPORT_DATA.CAR_MODEL.eq(carModel))
          .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
          .and(RAW_IMPORT_DATA.PIN.eq(pinPair))
          .fetchOne();
      // Convert JSON to TestReportResult Object.
      final ObjectMapper json = new ObjectMapper();
      List<TestReportResult> testReportResultList = new ArrayList<>();
      try {
        if (record.get(field("TEST_RESULT")) != null) {
          final TypeReference<List<TestReportResult>> mapType = new TypeReference<List<TestReportResult>>() {
          };
          testReportResultList = json.readValue(record.get(field("TEST_RESULT")).toString(), mapType);
        }
      } catch (final IOException e) {
        log.error("Can't decode {} to TestReportResult class", record.get(field("TEST_RESULT")).toString());
      }
      return testReportResultList;
    });
  }

  private List<TestReportResult> getTestReportResultList(final DatabaseInstance fileDB) {
    return fileDB.runDbQuery(dsl ->
        dsl.select(field("TEST_REPORT_RESULT"))
            .from(table("TEST_RUN"))
            .fetch().stream()
            .map(ReportUtilsImpl::createTestReportResult)
            .filter(Objects::nonNull)
            .collect(Collectors.toList())).join();
  }

  private CompletableFuture<String> getCanBusActivitiesInfoFromSniffFile(final String carModel,
                                                                         final UUID testTypeId,
                                                                         final String pinPair) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("SNIFF_FILE"))
          .from(table("RAW_IMPORT_DATA"))
          .where(field("CAR_MODEL").eq(carModel))
          .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
          .and(field("PIN").eq(pinPair))
          .fetchOne();
      if (record != null) {
        final byte[] sniffFileContent = (byte[]) record.getValue(field("SNIFF_FILE"));
        final File sniffFile = new File("fakePath");
        try {
          FileUtils.writeByteArrayToFile(sniffFile, sniffFileContent);
        } catch (final IOException e) {
          log.error("Can't save Sniff to file", e);
        }
      }
      return "600,0";
    });
  }

  private static TestReportResult createTestReportResult(final Record record) {
    // Convert JSON to TestReportResult Object.
    final ObjectMapper json = new ObjectMapper();
    TestReportResult testReportResult = null;
    try {
      if (record.get(field("TEST_REPORT_RESULT")) != null) {
        testReportResult = json.readValue(record.get(field("TEST_REPORT_RESULT")).toString(), TestReportResult.class);
      }
    } catch (final IOException e) {
      log.error("Can't decode {} to TestReportResult class", record.get(field("TEST_REPORT_RESULT")).toString());
    }
    return testReportResult;
  }

  private boolean testpointExists(final Iterable<TestReportResult> testReportResultList, final String testPointName) {
    for (final TestReportResult testReportResult : testReportResultList) {
      if (testReportResult.getTestPointName().equals(testPointName)) {
        return true;
      }
    }
    return false;
  }

  private Collection<TestReportResult> addCANBusActivitiesIntoTestReportResult(final String sniffFilePath) {
    final Collection<TestReportResult> testReportResults = new ArrayList<>();
    final TestReportResult testReportResult = new TestReportResult();
    testReportResult.setTestPointName(TestType.CAN_BUS_ACTIVITIES.getCode());
    testReportResult.setTestResultStatus(TestResultStatus.PASSED);


    final int[] canBusActivitiesInfo = getCANBusActivitiesInfoFromSniffFile(sniffFilePath);
    final String canBusActivities = Integer.toString(canBusActivitiesInfo[0]);
    testReportResult.addExtraInformationForPass(canBusActivities);
    testReportResults.add(testReportResult);


    final TestReportResult testReportResultUniqueID = new TestReportResult();
    testReportResultUniqueID.setTestPointName(TestType.UNIQUE_ACTIVITIES_ECU_ID.getCode());
    testReportResultUniqueID.setTestResultStatus(TestResultStatus.PASSED);

    final String uniqueActivitiesIDs = Integer.toString(canBusActivitiesInfo[1]);
    testReportResultUniqueID.addExtraInformationForPass(uniqueActivitiesIDs);
    testReportResults.add(testReportResultUniqueID);

    return testReportResults;
  }

  private int[] getCANBusActivitiesInfoFromSniffFile(final String sniffFilePath) {
    int totalNumECUIDs = 0;
    final Iterator<CANLogEntry> canLogEntryIterator = readInTrafficFile(generateFile(sniffFilePath));
    final Collection<Integer> uniqueIDs = new HashSet<>();
    while (canLogEntryIterator.hasNext()) {
      final CANLogEntry canLogEntry = canLogEntryIterator.next();
      uniqueIDs.add(canLogEntry.getId());
      totalNumECUIDs++;
    }
    totalNumECUIDs = totalNumECUIDs / 120;
    return new int[]{totalNumECUIDs, uniqueIDs.size()};
  }

  private static Iterator<CANLogEntry> readInTrafficFile(final File file) {
    Iterator<CANLogEntry> canLogEntryIterator = null;
    try {
      canLogEntryIterator = new LogEntryIterator(file);
    } catch (final IOException e) {
      log.error("Can't read traffic file", e);
    }
    return canLogEntryIterator;
  }

  // Calculate score api
  @Override
  public void calculateAndUpdateGradeForCarModel(final String carModel, final UUID testTypeId) {
    // Create updateTime timestamp
    final Date date = new java.util.Date();
    final Timestamp updateTime = new Timestamp(date.getTime());
    // Find latest formulaID and PIN pairs from RAW_IMPORT_DATA Table
    final UUID formulaID = findFormulaIDByCarModelInRawImportDataTable(carModel, testTypeId).join();
    final Collection<String> pinPairs = findPinPairsByCarModelInRawImportDataTable(carModel, testTypeId).join();
    // calculate the scores for given PIN pairs and formulaID
    for (final String pinPair : pinPairs) {
      calculateAndUpdateScoreForTestPointDetailTable(carModel, testTypeId, pinPair, formulaID, updateTime);
    }
    // Group the TESTPOINT_DETAIL scores and add the additional scores to generate the final score for carModel
    final Collection<Double> scoreList = getScoreForCarModelFromTestPointDetailTable(carModel, testTypeId, pinPairs, formulaID).join();
    double score = adjustScore(scoreList);

    // TODO: it's a temp solution, for grading formula three, add penalty score first, then
    // minus penalty scores from additional_score
    if (formulaID != null && formulaID.equals(findFormulaIDByFormula(new ReportGradingFormulaThree()).join())) {
      //noinspection MagicNumber
      score = score + 15;
    }

    // get additional items for car model
    final Collection<AdditionalItem> additionalItems = getAdditionalItems(carModel, testTypeId);
    final AdditionalScoreFormula additionalScoreFormula = new AdditionalScoreFormula(additionalItems);

    // Add the additional scores
    final DetailedScore detailedScore = additionalScoreFormula.calculateScore();
    score += detailedScore.getScore();

    // Insert Details
    final Collection<Details> validDetails = detailedScore.getDetails()
        .stream()
        .filter(d -> d.getScore() != 0 || d.getTotal() != 0)
        .collect(Collectors.toList());
    insertAdditionalItemsDetails(carModel, testTypeId, validDetails);


    score = Math.round(score * 100.0) / 100.0;

    // Insert into TEST_REPORT Table
    insertAndUpdateScoreInTestReportTable(carModel, formulaID, score, updateTime, testTypeId).join();
  }

  private void insertAdditionalItemsDetails(final String carModel, final UUID testTypeId, final Collection<Details> details) {
    final Timestamp now = new Timestamp(System.currentTimeMillis());
    DB.runDbQuery(dsl ->
        dsl.batch(details.stream()
            .map(d -> dsl
                .insertInto(table("TESTPOINT_DETAIL"),
                    field("CAR_MODEL"), TEST_TYPE_ID, field("PIN"), field("ID"), field("TESTPOINT_NAME"), field("GRADING_FORMULA_ID"),
                    field("SCORE"), field("TOTAL"), field("DETAIL_SCORE_INFO"), field("UPDATE_TIME"))
                .values(carModel, asBytes(testTypeId), "", asBytes(d.getId()), d.getName(), "", d.getScore(), d.getTotal(), d.getDetails(), now))
            .collect(Collectors.toList())
        ).execute().length > 0).join();
  }

  @Override
  public void calculateAndUpdateGradesForAllRecords() {
    final Collection<CarModelTestType> allCarModelNames = findCarModels().join();
    for (final CarModelTestType carModel : allCarModelNames) {
      calculateAndUpdateGradeForCarModel(carModel.getCarModel(), carModel.getTestType());
    }
  }

  @Override
  public void calculateAndUpdateGradesForSelectedRecords(final Collection<CarModelTestType> carModelList) {
    for (final CarModelTestType carModel : carModelList) {
      calculateAndUpdateGradeForCarModel(carModel.getCarModel(), carModel.getTestType());
    }
  }

  private void calculateAndUpdateScoreForTestPointDetailTable(final String carModel, final UUID testTypeId, final String pinPair,
                                                              final UUID formulaID, final Timestamp updateTime) {
    final ReportGradingFormula reportGradingFormula = findFormulaByFormulaID(formulaID).join();
    reportGradingFormula.calculateSummaryTotalGrading(carModel, testTypeId, pinPair, formulaID,
        findTestReportResultsListByCarModelAndPin(carModel, testTypeId, pinPair).join(), updateTime);
  }

  private double adjustScore(final Collection<Double> scoreList) {
    double score = 0;
    for (final double sc : scoreList) {
      score = score + sc;
    }
    return score / scoreList.size();
  }

  private CompletableFuture<Collection<Double>> getScoreForCarModelFromTestPointDetailTable(final String carModel,
                                                                                            final UUID testTypeId,
                                                                                            final Iterable<String> pinPairs,
                                                                                            final UUID gradingFromulaID) {
    return DB.runDbQuery(dsl -> {
      try {
        final Collection<Double> scoreList = new LinkedList<>();
        for (final String pinPair : pinPairs) {
          double score = 0;
          final Collection<Record> recordList = new ArrayList<>(dsl.select(field("SCORE"))
              .from(table("TESTPOINT_DETAIL"))
              .where(field("CAR_MODEL").eq(carModel))
              .and(TEST_TYPE_ID.eq(asBytes(testTypeId)))
              .and(field("PIN").eq(pinPair))
              .and(field("GRADING_FORMULA_ID").eq(gradingFromulaID))
              .fetch());
          if (!recordList.isEmpty()) {
            for (final Record record : recordList) {
              score = score + Double.valueOf(record.getValue(field("SCORE")).toString());
            }
          }
          scoreList.add(score);
        }
        return scoreList;
      } catch (final Exception e) {
        log.error("Can't get score", e);
        return null;
      }
    });
  }

  private CompletableFuture<Collection<CarModelTestType>> findCarModels() {
    return DB.runDbQuery(dsl ->
        dsl.select(field("CAR_MODEL"), TEST_TYPE_ID)
            .from(table("TEST_REPORT"))
            .fetch().stream()
            .map(r -> new CarModelTestType(
                r.get(field("CAR_MODEL", String.class)),
                asUuid(r.get("TEST_TYPE_ID", byte[].class)))
            )
            .collect(Collectors.toList()));
  }

  // Operations for TESTPOINT_DETAIL and TESTPOINT_SCORE_DESCRIPTION tables
  @Override
  public CompletableFuture<Boolean> insertAndUpdateTestPointScoreInTestPointDetailTable(
      final String carModel, final UUID testTypeId, final String pinPair, final UUID id, final String testPointName,
      final UUID gradingFormulaID, final double score,
      final double total, final String scoreDetailInfo, final Timestamp updateTime) {
    return DB.runDbQuery(dsl -> {
      try {
        return dsl.mergeInto(table("TESTPOINT_DETAIL"),
            field("CAR_MODEL"), TEST_TYPE_ID,
            field("PIN"), field("ID"), field("TESTPOINT_NAME"),
            field("GRADING_FORMULA_ID"), field("SCORE"), field("TOTAL"),
            field("DETAIL_SCORE_INFO"), field("UPDATE_TIME")
        )
            .values(carModel, asBytes(testTypeId), pinPair, asBytes(id),
                testPointName, gradingFormulaID, score, total, scoreDetailInfo, updateTime)
            .execute() > 0;
      } catch (final Exception e) {
        log.error("Can't insert into TESTPOINT_DETAIL table", e);
        return false;
      }
    });
  }

  @Override
  public CompletableFuture<Double> getScoreFromTestPointScoreDescriptionTable(final String testPointName,
                                                                              final UUID gradingFormulaID) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("SCORE"))
          .from(table("TESTPOINT_SCORE_DESCRIPTION"))
          .where(field("TESTPOINT_NAME").eq(testPointName),
              field("GRADING_FORMULA_ID").eq(gradingFormulaID.toString()))
          .limit(1)
          .fetchOne();
      if (record == null) {
        log.error("Can't find test point default score for: {}, formulaId: {}", testPointName, gradingFormulaID);
      }
      return record == null ? 0.0 : Double.valueOf(record.getValue(field("SCORE")).toString());
    });
  }

  // Name Hierarchy list order is from root to leaf
  @Override
  public CompletableFuture<List<String>> getNameHierarchyByIDFromTestPointScoreDescTable(final UUID id) {
    return DB.runDbQuery(dsl -> {
      final List<String> nameHierarchy = new LinkedList<>();
      UUID currentID = id;
      while (currentID != null) {
        final Record record = dsl.select(field("TESTPOINT_NAME"), field("PARENT_ID"))
            .from(table("TESTPOINT_SCORE_DESCRIPTION"))
            .where(field("ID").eq(asBytes(id)))
            .limit(1)
            .fetchOne();
        if (record != null) {
          nameHierarchy.add(0, record.getValue("TESTPOINT_NAME").toString());
          currentID = asUuid(record.getValue(field("PARENT_ID", byte[].class)));
        }
      }
      return nameHierarchy;
    });
  }

  @Override
  public CompletableFuture<UUID> getIDByNameAndFormulaIDFromTestPointScoreDescriptionTable(final String testPointName,
                                                                                           final UUID gradingFormulaID) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("ID"))
          .from(table("TESTPOINT_SCORE_DESCRIPTION"))
          .where(field("TESTPOINT_NAME").eq(testPointName),
              field("GRADING_FORMULA_ID").eq(gradingFormulaID.toString()))
          .limit(1)
          .fetchOne();
      return record == null ? null : asUuid(record.getValue(field("ID", byte[].class)));
    });
  }


  // Operations for GRADING_FORMULA Table
  @Override
  public CompletableFuture<Boolean> insertAndUpdateGradeFormulaIntoGradingFormulasTable(
      final ReportGradingFormula reportGradingFormula) {
    return DB.runDbQuery(dsl -> {
      try {
        // Handle testReportResult list
        String reportGradingFormulaStr = "";
        try {
          reportGradingFormulaStr = json.writeValueAsString(reportGradingFormula);
        } catch (final JsonProcessingException e) {
          log.error("Can't encode {} to JSON", reportGradingFormula);
        }

        // Find formula exist or not
        UUID formulaID = findFormulaIDByFormula(reportGradingFormula).join();
        formulaID = formulaID == null ? UUID.randomUUID() : formulaID;
        return dsl.mergeInto(table("GRADING_FORMULAS"), field("GRADING_FORMULA_ID"),
            field("GRADING_FORMULA"))
            .values(formulaID, reportGradingFormulaStr)
            .execute() > 0;
      } catch (final Exception e) {
        log.error("Can't insert into grading_formulas table", e);
        return false;
      }
    });
  }

  private CompletableFuture<ReportGradingFormula> findFormulaByFormulaID(final UUID formulaID) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("GRADING_FORMULA"))
          .from(table("GRADING_FORMULAS"))
          .where(field("GRADING_FORMULA_ID").eq(formulaID.toString()))
          .fetchOne();
      // Convert JSON to TestReportResult Object.
      final ObjectMapper json = new ObjectMapper();
      ReportGradingFormula reportGradingFormula = null;
      try {
        if (record.get(field("GRADING_FORMULA")) != null) {
          reportGradingFormula = json.readValue(record.get(field("GRADING_FORMULA")).toString(),
              ReportGradingFormula.class);
        }
      } catch (final IOException e) {
        log.error("Can't decode {} to ReportGradingFormula class", record.get(field("GRADING_FORMULA")).toString());
      }
      return reportGradingFormula;
    });
  }

  @Override
  public CompletableFuture<UUID> findFormulaIDByFormula(final ReportGradingFormula reportGradingFormula) {
    return DB.runDbQuery(dsl -> {
      // Handle testReportResult list
      String reportGradingFormulaStr = "";
      try {
        reportGradingFormulaStr = json.writeValueAsString(reportGradingFormula);
      } catch (final JsonProcessingException e) {
        log.error("Can't encode {} to JSON", reportGradingFormulaStr);
      }
      final Record record = dsl.select(field("GRADING_FORMULA_ID"))
          .from(table("GRADING_FORMULAS"))
          .where(field("GRADING_FORMULA").eq(reportGradingFormulaStr))
          .limit(1)
          .fetchOne();
      return record == null ? null : UUID.fromString(record.getValue(field("GRADING_FORMULA_ID")).toString());
    });
  }

  // Operations for TEST_TYPE table

  @Override
  public CompletableFuture<byte[]> getTestTypeID(final String testType) {
    return DB.runDbQuery(dsl -> {
      final Record record = dsl.select(field("ID", byte[].class))
          .from(table("TEST_TYPE"))
          .where(field("NAME").eq(testType))
          .limit(1)
          .fetchOne();
      return record == null ? null : record.getValue(field("ID", byte[].class));
    });
  }

  private File generateFile(final String dbPath) {
    if (dbPath == null) {
      return null;
    }
    return new File(dbPath);
  }

  public static byte[] asBytes(final UUID uuid) {
    if (uuid == null) {
      return null;
    }

    final ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }

  public static UUID asUuid(final byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    final ByteBuffer bb = ByteBuffer.wrap(bytes);
    final long firstLong = bb.getLong();
    final long secondLong = bb.getLong();
    return new UUID(firstLong, secondLong);
  }
}
