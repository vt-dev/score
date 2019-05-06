package com.visualthreat.report.core.report;

import lombok.Value;

import java.util.UUID;

@Value
public class CarModelTestType {
  private final String carModel;
  private final UUID testType;
}
