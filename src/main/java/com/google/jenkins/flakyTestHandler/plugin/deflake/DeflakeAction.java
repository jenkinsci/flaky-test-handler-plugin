/* Copyright 2014 Google Inc. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.jenkins.flakyTestHandler.plugin.deflake;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.jenkins.flakyTestHandler.plugin.FlakyTestResultAction;

import hudson.model.*;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import jakarta.servlet.ServletException;

import jenkins.model.Jenkins;

/**
 * Deflake action used to configure and trigger deflake build
 *
 * @author Qingzhou Luo
 */
public class DeflakeAction implements Action {

  private static final String DEFLAKE_CONFIG_URL = "deflakeConfig";

  private static final String RERUN_FAILING_TESTS_COUNT_PARAM = "rerunFailingTestsCountParam";

  private static final String MAVEN_TEST_PARAM = "testParam";

  private static final String MAVEN_TEST = "test";

  private static final String COMMA = ",";

  private static final String SHARP = "#";

  private static final String PLUS = "+";

  private static final Function<Map.Entry<String, Set<String>>, String>
      CLASS_METHOD_MAP_TO_MAVEN_TESTS_LIST = new Function<Entry<String, Set<String>>, String>() {

    @Override
    @Nonnull
    public String apply(@Nonnull Entry<String, Set<String>> entry) {
      return entry.getKey() + SHARP + Joiner.on(PLUS).join(entry.getValue());
    }
  };

  private final Map<String, Set<String>> failingClassMethodMap;

  public DeflakeAction(Map<String, Set<String>> failingClassMethodMap) {
    this.failingClassMethodMap = failingClassMethodMap;
  }

  @Override
  public String getIconFileName() {
    if (Jenkins.get().hasPermission(Item.BUILD)) {
      return "clock.png";
    }
    return null;
  }

  @Override
  public String getDisplayName() {
    if (Jenkins.get().hasPermission(Item.BUILD)) {
      return "Deflake this build";
    }
    return null;
  }

  @Override
  public String getUrlName() {
    if (Jenkins.get().hasPermission(Item.BUILD)) {
      return "deflake";
    }
    return null;
  }

  /**
   * Handles the rebuild request and redirects to deflake config page
   *
   * @param request StaplerRequest2 the request.
   * @param response StaplerResponse2 the response handler.
   * @throws java.io.IOException in case of Stapler issues
   * @throws jakarta.servlet.ServletException if something unfortunate happens.
   * @throws InterruptedException if something unfortunate happens.
   */
  public void doIndex(StaplerRequest2 request, StaplerResponse2 response) throws
      IOException, ServletException, InterruptedException {
    Run currentBuild = request.findAncestorObject(Run.class);
    if (currentBuild != null) {

      Job job = currentBuild.getParent();
      job.checkPermission(AbstractProject.BUILD);
      response.sendRedirect(DEFLAKE_CONFIG_URL);
    }
  }

  /**
   * Get parameters from submitted form and submit deflake request
   */
  @RequirePOST
  public void doSubmitDeflakeRequest(StaplerRequest2 request, StaplerResponse2 response) throws
      IOException, ServletException, InterruptedException {

    Run run = request.findAncestorObject(Run.class);
    if (run != null) {
      Job job = run.getParent();
      job.checkPermission(AbstractProject.BUILD);
      List<Action> actions = constructDeflakeCause(run);

      JSONObject formData = request.getSubmittedForm();
      List<ParameterValue> parameterValues = new ArrayList<ParameterValue>();
      parameterValues.add(getStringParam(formData, RERUN_FAILING_TESTS_COUNT_PARAM));

      JSONObject paramObj = JSONObject.fromObject(formData.get(MAVEN_TEST_PARAM));
      boolean onlyRunFailingTests = paramObj.getBoolean("value");
      if (onlyRunFailingTests) {
        String testParameter = generateMavenTestParams();
        if (testParameter != null) {
          parameterValues.add(new StringParameterValue(MAVEN_TEST, testParameter));
        }
      }

      ParametersAction originalParamAction = run.getAction(ParametersAction.class);
      if (originalParamAction == null) {
        originalParamAction = new ParametersAction();
      }

      actions.add(originalParamAction.createUpdated(parameterValues));

      Jenkins.getInstance().getQueue().schedule((Queue.Task) run.getParent(), 0, actions);
    }

    response.sendRedirect("../../");
  }

  /**
   * Generate maven test parameters to run all the failed tests
   *
   * @return  a string in the format of testClass1#testMethod1+testMethod2,testClass2#testMethod3,
   * ...
   */
  String generateMavenTestParams() {
    return Joiner.on(COMMA).join(Iterables.transform(failingClassMethodMap.entrySet(),
        CLASS_METHOD_MAP_TO_MAVEN_TESTS_LIST));
  }

  /**
   * Construct a list of actions which contain the user and deflake cause and the original failed build
   *
   * @param up upstream build
   * @return list with all original causes and a {@link hudson.model.Cause.UserIdCause} and a {@link
   * com.google.jenkins.flakyTestHandler.plugin.deflake.DeflakeCause}.
   */
  private static List<Action> constructDeflakeCause(Run up) {
    List<Action> actions = new ArrayList<>();
    actions.add(new CauseAction(new Cause.UserIdCause(), new DeflakeCause(up)));
    return actions;
  }

  private static ParameterValue getBooleanParam(JSONObject formData, String paramName) {
    JSONObject paramObj = JSONObject.fromObject(formData.get(paramName));
    String name = paramObj.getString("name");
    FlakyTestResultAction.logger.log(Level.FINE,
        "Param: " + name + " with value: " + paramObj.getBoolean("value"));
    return new BooleanParameterValue(name, paramObj.getBoolean("value"));
  }

  private static ParameterValue getStringParam(JSONObject formData, String paramName) {
    JSONObject paramObj = JSONObject.fromObject(formData.get(paramName));
    String name = paramObj.getString("name");
    FlakyTestResultAction.logger.log(Level.FINE,
        "Param: " + name + " with value: " + paramObj.getString("value"));
    return new StringParameterValue(name, paramObj.getString("value"));
  }
}
