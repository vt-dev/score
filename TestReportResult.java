package com.visualthreat.report.core.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.nashorn.internal.ir.annotations.Ignore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class TestReportResult {
  private TestResultStatus testResultStatus;
  private List<String> warningsInfos = new ArrayList<>();
  private List<String> securityVulnerabilitiesInfos = new ArrayList<>();
  private List<String> extraInformationForPass = new ArrayList<>();
  private String description = "";
  private String testPointName = "";

  public enum TestResultStatus {
    PASSED,
    NOT_PASSED,
    FAILED,
  }

  @JsonIgnore
  public int getNumWarnings() {
    return this.warningsInfos.size();
  }

  @JsonIgnore
  public int getNumSecurityVulnerabilities(){
    return this.securityVulnerabilitiesInfos.size();
  }

  @JsonIgnore
  public int getNumExtraInformation(){
    return this.extraInformationForPass.size();
  }

  public TestReportResult(){
    this.warningsInfos = new ArrayList<>();
    this.securityVulnerabilitiesInfos = new ArrayList<>();
    this.extraInformationForPass = new ArrayList<>();
    this.description = "";
  }

  public void addWarning(String warning){
    this.warningsInfos.add(warning);
  }

  public void addSecurityVulnerability(String securityVulnerability){
    this.securityVulnerabilitiesInfos.add(securityVulnerability);
  }

  public void addExtraInformationForPass(String extraInfo){
    this.extraInformationForPass.add(extraInfo);
  }

  public void addToFrontOfWarning(String warning){
    this.warningsInfos.add(0, warning);
  }

  public void addToFrontOfSecurityVulnerability(String securityVulnerability){
    this.securityVulnerabilitiesInfos.add(0, securityVulnerability);
  }

  public void addToFrontOfExtraInformationForPass(String extraInfo){
    this.extraInformationForPass.add(0, extraInfo);
  }
}
