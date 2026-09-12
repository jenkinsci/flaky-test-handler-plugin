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

import com.thoughtworks.xstream.XStream;
import hudson.XmlFile;
import hudson.util.HeapSpaceStringConverter;
import hudson.util.XStream2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the JUnit result XML file parsing in {@link hudson.tasks.junit.TestResult}.
 * <p>
 * https://github.com/jenkinsci/jenkins/blob/master/core/src/test/java/hudson/tasks/junit/
 * TestResultTest.java
 */
class FlakyTestResultTest {

    private File getDataFile(String name) throws URISyntaxException {
        return new File(FlakyTestResultTest.class.getResource(name).toURI());
    }

    /**
     * Verifies that all suites of an Eclipse Plug-in Test Suite are collected. These suites don't
     * differ by name (and timestamp), the y differ by 'id'.
     */
    @Test
    void testIpsTests() throws Exception {
        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(getDataFile("eclipse-plugin-test-report.xml"));

        Collection<FlakySuiteResult> suites = testResult.getSuites();
        assertEquals(16, suites.size(), "Wrong number of test suites");
        int testCaseCount = 0;
        for (FlakySuiteResult suite : suites) {
            testCaseCount += suite.getCases().size();
        }
        assertEquals(3366, testCaseCount, "Wrong number of test cases");
    }

    /**
     * This test verifies compatibility of JUnit test results persisted to XML prior to the test code
     * refactoring.
     */
    @Test
    void testXmlCompatibility() throws Exception {
        XmlFile xmlFile = new XmlFile(XSTREAM, getDataFile("junitResult.xml"));
        FlakyTestResult result = (FlakyTestResult) xmlFile.read();

        // Regenerate the transient data
        result.tally();
        assertEquals(9, result.getTotalCount());
        assertEquals(1, result.getSkipCount());
        assertEquals(1, result.getFailCount());

        // XStream seems to produce some weird rounding errors...
        assertEquals(0.576, result.getDuration(), 0.0001);

        Collection<FlakySuiteResult> suites = result.getSuites();
        assertEquals(6, suites.size());

        List<FlakyCaseResult> failedTests = result.getFailedTests();
        assertEquals(1, failedTests.size());

        FlakySuiteResult failedSuite = result.getSuite("broken");
        assertNotNull(failedSuite);
        FlakyCaseResult failedCase = failedSuite.getCase("becomeUglier");
        assertNotNull(failedCase);
        assertFalse(failedCase.isSkipped());
        assertFalse(failedCase.isPassed());
    }

    /**
     * When test methods are parametrized, they can occur multiple times in the testresults XMLs. Test
     * that these are counted correctly.
     */
    @Issue("JENKINS-13214")
    @Test
    void testDuplicateTestMethods() throws IOException, URISyntaxException {
        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(getDataFile("JENKINS-13214/27449.xml"));
        testResult.parse(getDataFile("JENKINS-13214/27540.xml"));
        testResult.parse(getDataFile("JENKINS-13214/29734.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of test suites");
        assertEquals(3, testResult.getTotalCount(), "Wrong number of test cases");
    }

    @Issue("JENKINS-12457")
    @Test
    void testTestSuiteDistributedOverMultipleFilesIsCountedAsOne()
            throws IOException, URISyntaxException {
        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a1.xml"));
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_a2.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(2, testResult.getTotalCount(), "Wrong number of test cases");

        // check duration: 157.980 (TestSuite_a1.xml) and 15.000 (TestSuite_a2.xml) = 172.98
        assertEquals(172.98, testResult.getDuration(), 0.1, "Wrong duration for test result");
    }

    /**
     * A common problem is that people parse TEST-*.xml as well as TESTS-TestSuite.xml. See
     * http://jenkins.361315.n4.nabble.com/Problem-with-duplicate-build-execution-td371616.html for
     * discussion.
     */
    @Test
    void testDuplicatedTestSuiteIsNotCounted() throws IOException, URISyntaxException {
        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b.xml"));
        testResult.parse(getDataFile("JENKINS-12457/TestSuite_b_duplicate.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(1, testResult.getTotalCount(), "Wrong number of test cases");
        assertEquals(1.0, testResult.getDuration(), 0.01, "Wrong duration for test result");
    }

    /**
     * Test parsing of test reports with flaky tests information. More testing of contents of flaky
     * tests is in FlakySuiteResultTest
     */
    @Test
    void testFlakyTestReport() throws IOException, URISyntaxException {
        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(getDataFile("flaky-reports/flaky-report-1.xml"));
        testResult.tally();

        assertEquals(1, testResult.getSuites().size(), "Wrong number of testsuites");
        assertEquals(5, testResult.getTotalCount(), "Wrong number of test cases");
        assertEquals(3, testResult.getPassCount(), "Wrong number of passing test cases");
        assertEquals(1, testResult.getFailCount(), "Wrong number of failing test cases");
        assertEquals(1, testResult.getFlakyTests().size(), "Wrong number of flaky test cases");
    }

    /**
     * Maven Surefire with {@code maven.test.redirectTestOutputToFile=true} writes stdout to a
     * sibling {@code *-output.txt} rather than {@code <system-out>} in the XML. A NUL byte in that
     * sidecar is legal in a text file, but XStream refuses it when persisting {@code FlakySuiteResult#stdout}
     * (the pipeline {@code junitPublisher} save path).
     *
     * <p>This only shows up on multi-module builds because each module has its own
     * {@code target/surefire-reports} directory; a single NUL in one module is enough to fail
     * archiving the aggregated result.
     */
    @Issue("JENKINS-75519")
    @Test
    void testNulInSurefireOutputFileOfMultiModuleProjectFailsXmlSerialization(@TempDir Path workspace)
            throws Exception {
        Path moduleAReports = workspace.resolve("module-a/target/surefire-reports");
        Path moduleBReports = workspace.resolve("module-b/target/surefire-reports");
        writeSurefireReport(moduleAReports, "com.example.ModuleATest", "module A stdout\n", true);
        writeSurefireReport(moduleBReports, "com.example.ModuleBTest",
                "module B stdout with NUL: \0 here\n", true);

        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(moduleAReports.resolve("TEST-com.example.ModuleATest.xml").toFile());
        testResult.parse(moduleBReports.resolve("TEST-com.example.ModuleBTest.xml").toFile());
        testResult.tally();

        assertEquals(2, testResult.getSuites().size(), "Wrong number of testsuites");
        FlakySuiteResult moduleB = testResult.getSuite("com.example.ModuleBTest");
        assertNotNull(moduleB);
        assertTrue(moduleB.getStdout().contains("^@"),
                "Surefire sidecar stdout should reencode the NUL character");

        File dest = workspace.resolve("flaky-result.xml").toFile();
        XmlFile xmlFile = new XmlFile(XSTREAM, dest);
        assertDoesNotThrow(() -> xmlFile.write(testResult));
    }

    /**
     * Same setup as {@link #testNulInSurefireOutputFileOfMultiModuleProjectFailsXmlSerialization}
     * except {@code maven.test.redirectTestOutputToFile=false}: Surefire writes console output into
     * {@code <system-out>} in the XML report instead of a sibling {@code *-output.txt}.
     */
    @Issue("JENKINS-75519")
    @Test
    void testNulInSurefireSystemOutOfMultiModuleProjectFailsXmlSerialization(@TempDir Path workspace)
            throws Exception {
        Path moduleAReports = workspace.resolve("module-a/target/surefire-reports");
        Path moduleBReports = workspace.resolve("module-b/target/surefire-reports");
        writeSurefireReport(moduleAReports, "com.example.ModuleATest", "module A stdout\n", false);
        // any NUL in stdout is encoded as &x0; by PrettyPrintWriter
        writeSurefireReport(moduleBReports, "com.example.ModuleBTest",
                "module B stdout with NUL: &x0; here\n", false);

        FlakyTestResult testResult = new FlakyTestResult();
        testResult.parse(moduleAReports.resolve("TEST-com.example.ModuleATest.xml").toFile());
        testResult.parse(moduleBReports.resolve("TEST-com.example.ModuleBTest.xml").toFile());
        testResult.tally();

        assertEquals(2, testResult.getSuites().size(), "Wrong number of testsuites");
        FlakySuiteResult moduleB = testResult.getSuite("com.example.ModuleBTest");
        assertNotNull(moduleB);
        assertTrue(moduleB.getStdout().contains("&x0;"),
                "Embedded <system-out> stdout should retain the xml encoded NUL character");

        File dest = workspace.resolve("flaky-result.xml").toFile();
        XmlFile xmlFile = new XmlFile(XSTREAM, dest);
        assertDoesNotThrow(() -> xmlFile.write(testResult));
    }

    /**
     * @param redirectTestOutputToFile {@code true} mimics Surefire
     * {@code maven.test.redirectTestOutputToFile=true} (stdout in {@code *-output.txt});
     * {@code false} puts stdout in {@code <system-out>} in the XML.
     */
    private static void writeSurefireReport(Path reportsDir, String className, String stdout,
            boolean redirectTestOutputToFile) throws IOException {
        Files.createDirectories(reportsDir);
        StringBuilder report = new StringBuilder();
        report.append("<testsuite failures='0' errors='0' tests='1' name='").append(className).append("'>\n");
        report.append("<testcase name='testSomething' classname='").append(className).append("'/>\n");
        if (!redirectTestOutputToFile) {
            report.append("<system-out><![CDATA[").append(stdout).append("]]></system-out>\n");
        }
        report.append("</testsuite>\n");
        Files.writeString(reportsDir.resolve("TEST-" + className + ".xml"), report.toString(),
                StandardCharsets.UTF_8);
        if (redirectTestOutputToFile) {
            Files.writeString(reportsDir.resolve(className + "-output.txt"), stdout, StandardCharsets.UTF_8);
        }
    }

    private static final XStream XSTREAM = new XStream2();

    static {
        XSTREAM.alias("result", FlakyTestResult.class);
        XSTREAM.alias("suite", FlakySuiteResult.class);
        XSTREAM.alias("case", FlakyCaseResult.class);
        XSTREAM.registerConverter(new HeapSpaceStringConverter(), 100);
    }
}
