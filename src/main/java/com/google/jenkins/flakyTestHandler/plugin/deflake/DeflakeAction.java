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

import hudson.model.Job;
import hudson.model.Run;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BooleanParameterValue;
import hudson.model.CauseAction;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.StringParameterValue;
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

  /**
   * The name of the build parameter on the deflaked build which can be changed via system property
   */
  private static final String BUILD_PARAM_TEST =
		  System.getProperty("jenkins.deflake.param.test", "test");

  /**
   * The name of the build parameter on the deflaked build which can be changed via system property.
   * This is useful when using failsafe plugin or for other scenarios.
   */
  private static final String BUILD_PARAM_RERUN_FAIL_TEST_COUNT =
		  System.getProperty("jenkins.deflake.param.rerun_fail_test_count", "surefire.rerunFailingTestsCount");

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
  private final Integer parentBuildNumber;

  public DeflakeAction(Map<String, Set<String>> failingClassMethodMap) {
    this(failingClassMethodMap, null);
  }

  public DeflakeAction(Map<String, Set<String>> failingClassMethodMap, Run<?, ?> parentBuild) {
    this.failingClassMethodMap = failingClassMethodMap;
    this.parentBuildNumber = (parentBuild != null) ? parentBuild.getNumber() : null;
  }

  @Override
  public String getIconFileName() {
    return "clock.gif";
  }

  @Override
  public String getDisplayName() {
    return "Deflake this build";
  }

  @Override
  public String getUrlName() {
    return "deflake";
  }

  /**
   * Handles the rebuild request and redirects to deflake config page
   *
   * @param request StaplerRequest the request.
   * @param response StaplerResponse the response handler.
   * @throws java.io.IOException in case of Stapler issues
   * @throws javax.servlet.ServletException if something unfortunate happens.
   * @throws InterruptedException if something unfortunate happens.
   */
  public void doIndex(StaplerRequest request, StaplerResponse response) throws
      IOException, ServletException, InterruptedException {
    Run<?, ?> currentBuild = request.findAncestorObject(Run.class);
    if (currentBuild != null) {

      Job<?, ?> job = currentBuild.getParent();
      job.checkPermission(AbstractProject.BUILD);
      response.sendRedirect(DEFLAKE_CONFIG_URL);
    }
  }

  /**
   * Get parameters from submitted form and submit deflake request
   * 
   * @param request request
   * @param response response
   * @throws ServletException when unable to parse input form
   * @throws IOException when unable to redirect
   */
  public void doSubmitDeflakeRequest(StaplerRequest request, StaplerResponse response) throws ServletException, IOException {

    Run<?,?> run = request.findAncestorObject(Run.class);
    if (run != null) {
      Job<?, ?> job = run.getParent();
      job.checkPermission(AbstractProject.BUILD);

      JSONObject formData = request.getSubmittedForm();
      List<ParameterValue> parameterValues = new ArrayList<ParameterValue>();
      String rerunFailTestCount = getStringParam(formData, RERUN_FAILING_TESTS_COUNT_PARAM, "0");
      parameterValues.add(new StringParameterValue(BUILD_PARAM_RERUN_FAIL_TEST_COUNT, rerunFailTestCount));

      boolean onlyRunFailingTests = getBooleanParam(formData, MAVEN_TEST_PARAM);
      if (onlyRunFailingTests) {
        String testParameter = generateMavenTestParams();
        if (testParameter != null) {
          parameterValues.add(new StringParameterValue(BUILD_PARAM_TEST, testParameter));
        }
      }

      ParametersAction originalParamAction = run.getAction(ParametersAction.class);
      if (originalParamAction == null) {
        originalParamAction = new ParametersAction();
      }

      List<Action> actions = constructDeflakeCause(run);
      actions.add(originalParamAction.createUpdated(parameterValues));

      Jenkins.get().getQueue().schedule2((Queue.Task) run.getParent(), 0, actions);
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
   * Construct a list of actions which contain deflake cause and the original failed build
   *
   * @param up upstream build
   * @return list with all original causes and a {@link hudson.model.Cause.UserIdCause} and a {@link
   * com.google.jenkins.flakyTestHandler.plugin.deflake.DeflakeCause}.
   */
  private static List<Action> constructDeflakeCause(Run up) {
    List<Action> actions = new ArrayList<Action>();
    actions.add(new CauseAction(new DeflakeCause(up)));
    return actions;
  }

  private static boolean getBooleanParam(JSONObject formData, String paramName) {
    JSONObject paramObj = JSONObject.fromObject(formData.get(paramName));
    return paramObj.getBoolean("value");
  }

  private static String getStringParam(JSONObject formData, String paramName, String defaultValue) {
    JSONObject paramObj = JSONObject.fromObject(formData.get(paramName));
    return StringUtils.defaultIfBlank(paramObj.getString("value"), defaultValue);
  }
  
  /**
   * The build number of the parent build that was deflaked.
   * 
   * @return build number of the parent build
   */
  @CheckForNull
  public Integer getParentBuildNumber() {
      return parentBuildNumber;
  }
}
