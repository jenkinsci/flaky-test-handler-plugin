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

import com.google.common.collect.Sets;
import hudson.model.BuildListener;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class DeflakeActionIntegrationTest {

    @Test
    void testDeflakeAction(JenkinsRule jenkins) throws Exception {
        FreeStyleProject fooProj = jenkins.createFreeStyleProject("foo");
        FreeStyleBuild build = fooProj.scheduleBuild2(0, new FailingTestResultAction())
                .get();

        // Assert deflake action is not null when there are failing tests
        DeflakeAction action = build.getAction(DeflakeAction.class);
        assertNotNull(action, "Deflake action is not available when there are failing tests");

        Set<String> possibleResults = Sets.newHashSet("classOne#methodOne+methodTwo,classTwo#methodOne",
                "classOne#methodTwo+methodOne,classTwo#methodOne",
                "classTwo#methodOne,classOne#methodTwo+methodOne",
                "classTwo#methodOne,classOne#methodOne+methodTwo");
        assertTrue(possibleResults.contains(action.generateMavenTestParams()),
                "Incorrect test parameter for maven");
        assertDisplayName(build, "#1");

        // Verify display name
        build = fooProj.scheduleBuild2(0, new DeflakeCause(build)).get();
        assertDisplayName(build, "#2: Deflake Build #1");

        // deflake action is null when there is no failing test or not test result action
        build = fooProj.scheduleBuild2(0).get();
        assertNull(build.getAction(DeflakeAction.class));

        build = fooProj.scheduleBuild2(0, new NoFailingTestResultAction()).get();
        assertNull(build.getAction(DeflakeAction.class));
    }

    @Test
    void testDeflakeActionForPipeline(JenkinsRule jenkins) throws Exception {
        WorkflowJob fooProj = jenkins.createProject(WorkflowJob.class, "foo");
        WorkflowRun run = fooProj.scheduleBuild2(0, new FailingTestResultAction()).get();

        // Assert deflake action is not null when there are failing tests
        DeflakeAction action = run.getAction(DeflakeAction.class);
        assertNotNull(action, "Deflake action is not available when there are failing tests");

        Set<String> possibleResults = Sets.newHashSet("classOne#methodOne+methodTwo,classTwo#methodOne",
                "classOne#methodTwo+methodOne,classTwo#methodOne",
                "classTwo#methodOne,classOne#methodTwo+methodOne",
                "classTwo#methodOne,classOne#methodOne+methodTwo");
        assertTrue(possibleResults.contains(action.generateMavenTestParams()),
                "Incorrect test parameter for maven");
        assertDisplayName(run, "#1");

        // Verify display name
        run = fooProj.scheduleBuild2(0, new CauseAction(new DeflakeCause(run))).get();
        assertDisplayName(run, "#2: Deflake Build #1");

        // deflake action is null when there is no failing test or not test result action
        run = fooProj.scheduleBuild2(0).get();
        assertNull(run.getAction(DeflakeAction.class));

        run = fooProj.scheduleBuild2(0, new NoFailingTestResultAction()).get();
        assertNull(run.getAction(DeflakeAction.class));
    }

    private void assertDisplayName(FreeStyleBuild build, String expectedName) {
        assertEquals(Result.SUCCESS, build.getResult());
        assertEquals(expectedName, build.getDisplayName());
    }

    private void assertDisplayName(WorkflowRun run, String expectedName) {
        assertEquals(Result.FAILURE, run.getResult());
        assertEquals(expectedName, run.getDisplayName());
    }

    public static class FailingTestResultAction extends TestResultAction {

        public FailingTestResultAction() {
            super(new TestResult(), new StreamBuildListener(System.out, StandardCharsets.UTF_8));
        }

        @Override
        public int getFailCount() {
            return 1;
        }

        @Override
        public int getTotalCount() {
            return 0;
        }

        @Override
        public List<CaseResult> getFailedTests() {
            return DeflakeListenerTest.setupCaseResultList();
        }

        @Override
        public synchronized void setResult(TestResult result, BuildListener listener) {
        }
    }

    private static class NoFailingTestResultAction extends
            AbstractTestResultAction<FailingTestResultAction> {

        @Override
        public int getFailCount() {
            return 0;
        }

        @Override
        public int getTotalCount() {
            return 1;
        }

        @Override
        public Object getResult() {
            return null;
        }
    }
}