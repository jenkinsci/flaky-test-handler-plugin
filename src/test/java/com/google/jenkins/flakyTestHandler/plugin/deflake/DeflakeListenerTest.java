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
import hudson.tasks.junit.CaseResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeflakeListenerTest {

    private static final String TEST_CLASS_ONE = "classOne";
    private static final String TEST_CLASS_TWO = "classTwo";

    private static final String TEST_METHOD_ONE = "methodOne";
    private static final String TEST_METHOD_TWO = "methodTwo";

    @Test
    void testGetFailingTestClassMethodMap() {
        List<CaseResult> caseResultList = setupCaseResultList();
        Map<String, Set<String>> classMethodMap = DeflakeListener.getFailingTestClassMethodMap(
                caseResultList);
        assertEquals(2, classMethodMap.size(), "Map size should be equal to number of classes");
        Set<String> expectedClassOneMethods = Sets.newHashSet(TEST_METHOD_ONE, TEST_METHOD_TWO);
        Set<String> expectedClassTwoMethods = Sets.newHashSet(TEST_METHOD_ONE);

        assertEquals(expectedClassOneMethods,
                classMethodMap.get(TEST_CLASS_ONE),
                "Incorrect test methods");
        assertEquals(expectedClassTwoMethods,
                classMethodMap.get(TEST_CLASS_TWO),
                "Incorrect test methods");
    }

    @Test
    void testGetFailingTestClassMethodMapWithNoTest() {
        assertTrue(DeflakeListener.getFailingTestClassMethodMap(new ArrayList<>()).isEmpty(),
                "Should return empty map for empty input list");
        assertTrue(DeflakeListener.getFailingTestClassMethodMap(null).isEmpty(),
                "Should return empty map for null input list");
    }

    static List<CaseResult> setupCaseResultList() {
        CaseResult caseOne = mock(CaseResult.class);
        CaseResult caseTwo = mock(CaseResult.class);
        CaseResult caseThree = mock(CaseResult.class);

        when(caseOne.getClassName()).thenReturn(TEST_CLASS_ONE);
        when(caseOne.getName()).thenReturn(TEST_METHOD_ONE);

        when(caseTwo.getClassName()).thenReturn(TEST_CLASS_ONE);
        when(caseTwo.getName()).thenReturn(TEST_METHOD_TWO);

        when(caseThree.getClassName()).thenReturn(TEST_CLASS_TWO);
        when(caseThree.getName()).thenReturn(TEST_METHOD_ONE);

        List<CaseResult> caseResultList = new ArrayList<>();
        caseResultList.add(caseOne);
        caseResultList.add(caseTwo);
        caseResultList.add(caseThree);
        return caseResultList;
    }
}