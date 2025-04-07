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
package com.google.jenkins.flakyTestHandler.junit;

import hudson.tasks.test.TestResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test class copied from hudson.tasks.junit.ClassResultTest
 * <p>
 * https://github.com/jenkinsci/jenkins/blob/master/core/src/test/java/hudson/tasks/junit/
 * ClassResultTest.java
 */
class FlakyClassResultTest {

    @Test
    void testFindCorrespondingResult() {
        FlakyClassResult flakyClassResult = new FlakyClassResult(null, "com.example.ExampleTest");

        FlakyCaseResult flakyCaseResult = new FlakyCaseResult(null, "testCase", null);

        flakyClassResult.add(flakyCaseResult);

        TestResult result = flakyClassResult
                .findCorrespondingResult("extraprefix.com.example.ExampleTest.testCase");
        assertEquals(flakyCaseResult, result);
    }

    @Test
    void testFindCorrespondingResultWhereFlakyClassResultNameIsNotSubstring() {
        FlakyClassResult flakyClassResult = new FlakyClassResult(null, "aaaa");

        FlakyCaseResult flakyCaseResult = new FlakyCaseResult(null, "tc_bbbb", null);

        flakyClassResult.add(flakyCaseResult);

        TestResult result = flakyClassResult.findCorrespondingResult("tc_bbbb");
        assertEquals(flakyCaseResult, result);
    }

    @Test
    void testFindCorrespondingResultWhereFlakyClassResultNameIsLastInFlakyCaseResultName() {
        FlakyClassResult flakyClassResult = new FlakyClassResult(null, "aaaa");

        FlakyCaseResult flakyCaseResult = new FlakyCaseResult(null, "tc_aaaa", null);

        flakyClassResult.add(flakyCaseResult);

        TestResult result = flakyClassResult.findCorrespondingResult("tc_aaaa");
        assertEquals(flakyCaseResult, result);
    }

}
