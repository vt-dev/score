package com.visualthreat.report.core.report;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface TestReportUtils {

  void writeGroupOfECUsToDatabase(Map<Integer, Set<Integer>> ecuSupportedUDS, int baud_rate);

  CompletableFuture<Integer> findCanBusNumByEcuID(int ecu_id);

  void writeVulnerabilityToDatabase(int ecu_id, int service_id,
      byte[] subfunction, String vulnerability_name);
}
