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
package com.google.jenkins.flakyTestHandler.plugin;

import com.google.jenkins.flakyTestHandler.plugin.FlakyTestResultAction.FlakyRunStats;
import com.google.jenkins.flakyTestHandler.plugin.HistoryAggregatedFlakyTestResultAction.SingleTestFlakyStats;
import com.google.jenkins.flakyTestHandler.plugin.HistoryAggregatedFlakyTestResultAction.SingleTestFlakyStatsWithRevision;
import com.google.jenkins.flakyTestHandler.plugin.deflake.DeflakeCause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class HistoryAggregatedFlakyTestResultActionTest {

    private static final String TEST_ONE = "testOne";

    private static final String TEST_TWO = "testTwo";

    private static final String TEST_THREE = "testThree";

    private static final String TEST_FOUR = "testFour";

    private static final String REVISION_ONE = "revision_one";

    private static final String REVISION_TWO = "revision_two";

    private static final int TOTAL_RUNS = 2;

    // Use fields instead of local variables, so that WeakReference to those in FlakyTestResultAction
    // cannot be garbage collected during the test. Especially important when running test on Windows,
    // as test execution is much slower there.
    private FlakyRunStats actionOneResult;
    private FlakyRunStats actionTwoResult;
    private FlakyRunStats actionThreeResult;

    @Test
    void testAggregateFlakyRunsWithRevisions(JenkinsRule jenkins) throws Exception {

        FreeStyleProject project = jenkins.createFreeStyleProject("project");

        List<Run> runList = new ArrayList<>();

        for (FlakyTestResultAction action : setUpFlakyTestResultAction()) {
            FreeStyleBuild build = new FreeStyleBuild(project);
            build.addAction(action);
            runList.add(build);
        }

        HistoryAggregatedFlakyTestResultAction action = new HistoryAggregatedFlakyTestResultAction(
                null);

        for (Run run : runList) {
            action.aggregateOneBuild(run);
        }

        Map<String, Map<String, SingleTestFlakyStats>> statsMapOverRevision =
                action.getAggregatedTestFlakyStatsWithRevision();

        // TEST_ONE
        SingleTestFlakyStats testOneRevisionOneStats = statsMapOverRevision.get(TEST_ONE)
                .get(REVISION_ONE);
        assertEquals(2, testOneRevisionOneStats.getPass(), "wrong number passes");
        assertEquals(0, testOneRevisionOneStats.getFail(), "wrong number fails");
        assertEquals(0, testOneRevisionOneStats.getFlake(), "wrong number flakes");

        SingleTestFlakyStats testOneRevisionTwoStats = statsMapOverRevision.get(TEST_ONE)
                .get(REVISION_TWO);
        assertEquals(1, testOneRevisionTwoStats.getPass(), "wrong number passes");
        assertEquals(1, testOneRevisionTwoStats.getFail(), "wrong number fails");
        assertEquals(0, testOneRevisionTwoStats.getFlake(), "wrong number flakes");

        // TEST_TWO
        SingleTestFlakyStats testTwoRevisionOneStats = statsMapOverRevision.get(TEST_TWO)
                .get(REVISION_ONE);
        assertEquals(1, testTwoRevisionOneStats.getPass(), "wrong number passes");
        assertEquals(3, testTwoRevisionOneStats.getFail(), "wrong number fails");
        assertEquals(0, testTwoRevisionOneStats.getFlake(), "wrong number flakes");

        SingleTestFlakyStats testTwoRevisionTwoStats = statsMapOverRevision.get(TEST_TWO)
                .get(REVISION_TWO);
        assertEquals(1, testTwoRevisionTwoStats.getPass(), "wrong number passes");
        assertEquals(0, testTwoRevisionTwoStats.getFail(), "wrong number fails");
        assertEquals(0, testTwoRevisionTwoStats.getFlake(), "wrong number flakes");

        // TEST_THREE
        SingleTestFlakyStats testThreeRevisionOneStats = statsMapOverRevision.get(TEST_THREE)
                .get(REVISION_ONE);
        assertEquals(1, testThreeRevisionOneStats.getPass(), "wrong number passes");
        assertEquals(2, testThreeRevisionOneStats.getFail(), "wrong number fails");
        assertEquals(0, testThreeRevisionOneStats.getFlake(), "wrong number flakes");

        SingleTestFlakyStats testThreeRevisionTwoStats = statsMapOverRevision.get(TEST_THREE)
                .get(REVISION_TWO);
        assertEquals(0, testThreeRevisionTwoStats.getPass(), "wrong number passes");
        assertEquals(2, testThreeRevisionTwoStats.getFail(), "wrong number fails");
        assertEquals(0, testThreeRevisionTwoStats.getFlake(), "wrong number flakes");

        // TEST_FOUR
        SingleTestFlakyStats testFourRevisionOneStats = statsMapOverRevision.get(TEST_FOUR)
                .get(REVISION_ONE);
        assertEquals(1, testFourRevisionOneStats.getPass(), "wrong number passes");
        assertEquals(3, testFourRevisionOneStats.getFail(), "wrong number fails");
        assertEquals(0, testFourRevisionOneStats.getFlake(), "wrong number flakes");

        SingleTestFlakyStats testFourRevisionTwoStats = statsMapOverRevision.get(TEST_FOUR)
                .get(REVISION_TWO);
        assertEquals(1, testFourRevisionTwoStats.getPass(), "wrong number passes");
        assertEquals(0, testFourRevisionTwoStats.getFail(), "wrong number fails");
        assertEquals(0, testFourRevisionTwoStats.getFlake(), "wrong number flakes");
    }

    @Test
    void testAggregate(JenkinsRule jenkins) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject("project");
        List<FlakyTestResultAction> flakyTestResultActions = setUpFlakyTestResultAction();

        List<FlakyTestResultAction> flakyTestResultActionList = new ArrayList<>(
                flakyTestResultActions);

        // First non-deflake build
        Run firstBuild = project
                .scheduleBuild2(0, flakyTestResultActionList.get(0)).get();
        jenkins.waitForCompletion(firstBuild);

        // Second deflake build
        Run secondBuild = project
                .scheduleBuild2(0, flakyTestResultActionList.get(1),
                        new CauseAction(new DeflakeCause(firstBuild))).get();
        jenkins.waitForCompletion(secondBuild);

        // Third deflake build with HistoryAggregatedFlakyTestResultAction
        Run thirdBuild = project
                .scheduleBuild2(0, flakyTestResultActionList.get(2),
                        new HistoryAggregatedFlakyTestResultAction(project)).get();
        jenkins.waitForCompletion(thirdBuild);

        HistoryAggregatedFlakyTestResultAction action = thirdBuild
                .getAction(HistoryAggregatedFlakyTestResultAction.class);
        action.aggregate();

        Map<String, SingleTestFlakyStats> aggregatedFlakyStatsMap = action.getAggregatedFlakyStats();

        // Make sure revisions are inserted in the order of their appearance
        Map<String, SingleTestFlakyStats> revisionMap = action.getAggregatedTestFlakyStatsWithRevision()
                .get(TEST_ONE);
        assertArrayEquals(new String[]{REVISION_ONE, REVISION_TWO},
                revisionMap.keySet().toArray(new String[0]),
                "Incorrect revision history");

        assertEquals(4, aggregatedFlakyStatsMap.size(), "wrong number of entries for flaky stats");

        SingleTestFlakyStats testOneStats = aggregatedFlakyStatsMap.get(TEST_ONE);
        SingleTestFlakyStats testTwoStats = aggregatedFlakyStatsMap.get(TEST_TWO);
        SingleTestFlakyStats testThreeStats = aggregatedFlakyStatsMap.get(TEST_THREE);
        SingleTestFlakyStats testFourStats = aggregatedFlakyStatsMap.get(TEST_FOUR);

        assertEquals(1, testOneStats.getPass(), "wrong number passes");
        assertEquals(0, testOneStats.getFail(), "wrong number fails");
        assertEquals(1, testOneStats.getFlake(), "wrong number flakes");

        assertEquals(1, testTwoStats.getPass(), "wrong number passes");
        assertEquals(0, testTwoStats.getFail(), "wrong number fails");
        assertEquals(1, testTwoStats.getFlake(), "wrong number flakes");

        assertEquals(0, testThreeStats.getPass(), "wrong number passes");
        assertEquals(1, testThreeStats.getFail(), "wrong number fails");
        assertEquals(1, testThreeStats.getFlake(), "wrong number flakes");

        assertEquals(1, testFourStats.getPass(), "wrong number passes");
        assertEquals(0, testFourStats.getFail(), "wrong number fails");
        assertEquals(1, testFourStats.getFlake(), "wrong number flakes");
    }


    private List<FlakyTestResultAction> setUpFlakyTestResultAction() {
        FlakyTestResultAction actionOne = new FlakyTestResultAction();
        FlakyTestResultAction actionTwo = new FlakyTestResultAction();
        FlakyTestResultAction actionThree = new FlakyTestResultAction();

        Map<String, SingleTestFlakyStatsWithRevision> testFlakyStatsWithRevisionMap =
                new HashMap<>();
        testFlakyStatsWithRevisionMap.put(TEST_ONE,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.PASSED));
        testFlakyStatsWithRevisionMap.put(TEST_TWO,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.FAILED));
        testFlakyStatsWithRevisionMap.put(TEST_THREE,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.PASSED));
        testFlakyStatsWithRevisionMap.put(TEST_FOUR,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.FLAKED));

        actionOneResult = new FlakyRunStats(testFlakyStatsWithRevisionMap);

        testFlakyStatsWithRevisionMap = new HashMap<>();
        testFlakyStatsWithRevisionMap.put(TEST_ONE,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.PASSED));
        testFlakyStatsWithRevisionMap.put(TEST_TWO,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.FLAKED));
        testFlakyStatsWithRevisionMap.put(TEST_THREE,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.FAILED));
        testFlakyStatsWithRevisionMap.put(TEST_FOUR,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_ONE, TestState.FAILED));

        actionTwoResult = new FlakyRunStats(testFlakyStatsWithRevisionMap);

        testFlakyStatsWithRevisionMap = new HashMap<>();
        testFlakyStatsWithRevisionMap.put(TEST_ONE,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_TWO, TestState.FLAKED));
        testFlakyStatsWithRevisionMap.put(TEST_TWO,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_TWO, TestState.PASSED));
        testFlakyStatsWithRevisionMap.put(TEST_THREE,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_TWO, TestState.FAILED));
        testFlakyStatsWithRevisionMap.put(TEST_FOUR,
                createSingleTestFlakyStatsWithRevision(TOTAL_RUNS, REVISION_TWO, TestState.PASSED));

        actionThreeResult = new FlakyRunStats(testFlakyStatsWithRevisionMap);

        actionOne.setFlakyRunStats(actionOneResult);
        actionTwo.setFlakyRunStats(actionTwoResult);
        actionThree.setFlakyRunStats(actionThreeResult);

        List<FlakyTestResultAction> actionList = new ArrayList<>();

        actionList.add(actionOne);
        actionList.add(actionTwo);
        actionList.add(actionThree);
        return actionList;
    }

    private enum TestState {
        PASSED, FAILED, FLAKED
    }

    /**
     * Create a {@link SingleTestFlakyStatsWithRevision} object for testing
     *
     * @param totalRuns number of maximal number of potential runs in this bulid. Assume if a test is
     *                  flaky, it will fail in all previous totalRuns-1 retries but pass in the last time.
     * @param revision  the revision this build was run
     * @param result    result of a single test
     * @return a {@link SingleTestFlakyStatsWithRevision} object which contains the revision
     * information and the number of passes/fails for the test
     */
    private static SingleTestFlakyStatsWithRevision createSingleTestFlakyStatsWithRevision(
            int totalRuns,
            String revision, TestState result) {

        SingleTestFlakyStats stats;
        if (result == TestState.PASSED) {
            stats = new SingleTestFlakyStats(1, 0, 0);
        } else if (result == TestState.FAILED) {
            stats = new SingleTestFlakyStats(0, totalRuns, 0);
        } else {
            stats = new SingleTestFlakyStats(1, totalRuns - 1, 0);
        }

        return new SingleTestFlakyStatsWithRevision(stats, revision);
    }

}
