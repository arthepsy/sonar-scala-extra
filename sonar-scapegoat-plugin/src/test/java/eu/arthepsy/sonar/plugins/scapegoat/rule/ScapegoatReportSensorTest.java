/*
 * Scapegoat
 * Copyright (C) 2015 Andris Raugulis
 * moo@arthepsy.eu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package eu.arthepsy.sonar.plugins.scapegoat.rule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import eu.arthepsy.sonar.plugins.scapegoat.ScapegoatConfiguration;
import eu.arthepsy.sonar.plugins.scapegoat.language.Scala;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.internal.DefaultIssue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.server.rule.RulesDefinition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ScapegoatReportSensorTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final String SOURCES_PROPERTY_KEY = "sonar.sources";

    private ScapegoatReportSensor sensor;
    private Settings settings;
    private RulesProfile profile;
    private DefaultFileSystem fileSystem;
    private ResourcePerspectives perspectives;
    private Project project;
    private SensorContext context;
    private Issuable issuable;
    private Issuable.IssueBuilder issueBuilder;
    private Issue issue;
    private File baseDir;
    private File report;
    private Appender<ILoggingEvent> mockAppender;

    private enum Warning {
        UnusedMethodParameter("<warning line=\"%d\" text=\"Unused method parameter\" snippet=\"Unused method parameter\" level=\"Warning\" file=\"%s\" inspection=\"com.sksamuel.scapegoat.inspections.unneccesary.UnusedMethodParameter\"/>"),
        OptionGet("<warning line=\"%d\" text=\"Use of Option.get\" level=\"Error\" file=\"%s\" inspection=\"com.sksamuel.scapegoat.inspections.option.OptionGet\"/>"),
        EmptyInterpolatedString("<warning line=\"%d\" text=\"Looks for interpolated strings that have no arguments\" level=\"Error\" file=\"%s\" inspection=\"com.sksamuel.scapegoat.inspections.string.EmptyInterpolatedString\"/>"),
        ListSize("<warning line=\"%d\" text=\"List.size is O(n)\" snippet=\"List.size is O(n). Consider using a different data type with O(1) size lookup such as Vector or Array.\" level=\"Info\" file=\"%s\" inspection=\"com.sksamuel.scapegoat.inspections.collections.ListSize\"/>"),
        ListAppend("<warning line=\"%d\" text=\"List append is slow\" snippet=\"List append is O(n). For large lists, consider using cons (::) or another data structure such as ListBuffer or Vector and converting to a List once built.\" level=\"Info\" file=\"%s\" inspection=\"com.sksamuel.scapegoat.inspections.collections.ListAppend\"/>");

        private final String stringValue;
        private Warning(final String s) { stringValue = s; }
        public String toString() { return stringValue; }
        public static String getWarning(Warning warning, Integer line, String filePath)
        {
            return String.format(warning.toString(), line, filePath);
        }
    }

    @Before
    public void prepare() throws IOException {
        baseDir = temp.newFolder();
        report = new File(baseDir, "scapegoat-report.xml");

        settings = new Settings();
        settings.setProperty(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY, report.getAbsolutePath());
        profile = RulesProfile.create("test-profile", Scala.KEY);
        fileSystem = new DefaultFileSystem(baseDir);
        perspectives = mock(ResourcePerspectives.class);

        sensor = new ScapegoatReportSensor(settings, profile, fileSystem, perspectives);

        project = mock(Project.class);
        context = mock(SensorContext.class);

        issuable = mock(Issuable.class);
        issueBuilder = mock(Issuable.IssueBuilder.class);
        issue = mock(Issue.class);

        when(project.key()).thenReturn("test-project");

        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        mockAppender = mock(Appender.class);
        when(mockAppender.getName()).thenReturn("MOCK");
        rootLogger.addAppender(mockAppender);
    }

    private void runSensor() {
        sensor.analyse(project, context);
    }

    @Test
    public void testAbsoluteFilePath() throws IOException {
        String dirPrefix = baseDir.getPath().replace('/', '.');
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.UnusedMethodParameter, 64, dirPrefix + ".src.app.foo.scala") +
                "</scapegoat>");
        this.addFile("src/app/foo.scala");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        this.verifyIssue(this.verifyIssues(1).get(0), Severity.MINOR, 64);
        this.verifyLogEvents(1);
    }

    @Test
    public void testSingleRelativeSourceDirectory() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.OptionGet, 128, "foo.scala") +
                "</scapegoat>");
        this.addFile("src/app/foo.scala");
        settings.setProperty(SOURCES_PROPERTY_KEY, "src/app");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        this.verifyIssue(this.verifyIssues(1).get(0), Severity.MAJOR, 128);
        this.verifyLogEvents(1);
    }

    @Test
    public void testSingleAbsoluteSourceDirectory() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.OptionGet, 128, "foo.scala") +
                "</scapegoat>");
        this.addFile("src/app/foo.scala");
        settings.setProperty(SOURCES_PROPERTY_KEY, new File(baseDir, "src/app").getAbsolutePath());
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        this.verifyIssue(this.verifyIssues(1).get(0), Severity.MAJOR, 128);
        this.verifyLogEvents(1);
    }

    @Test
    public void testMultipleSourceDirectoryFilePath() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.EmptyInterpolatedString, 256, "foo.scala") +
                Warning.getWarning(Warning.ListAppend, 512, "bar.scala") +
                Warning.getWarning(Warning.ListSize, 1024, "foobar.scala") +
                "</scapegoat>");
        this.addFile("src/a/foo.scala");
        this.addFile("src/b/bar.scala");
        this.addFile("src/a/foobar.scala");
        this.addFile("src/b/foobar.scala");
        settings.setProperty(SOURCES_PROPERTY_KEY, "src/a, src/b");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        List<Issue> issues = this.verifyIssues(2);
        this.verifyIssue(issues.get(0), Severity.MAJOR, 256);
        this.verifyIssue(issues.get(1), Severity.INFO, 512);
        List<LoggingEvent> logEvents = this.verifyLogEvents(3);
        this.verifyLogContains(logEvents.get(1), Level.WARN, "multiple source directories");
    }

    @Test
    public void testRelativeFilePath() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.UnusedMethodParameter, 2048, "foo.scala") +
                "</scapegoat>");
        this.addFile("foo.scala");
        settings.setProperty(SOURCES_PROPERTY_KEY, "src/main/scala");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        this.verifyIssue(this.verifyIssues(1).get(0), Severity.MINOR, 2048);
        this.verifyLogEvents(1);
    }

    @Test
    public void testInvalidReportPath() throws IOException {
        settings.setProperty(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY, "invalid-report.xml");
        this.runSensor();
        settings.setProperty(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY, "");
        this.runSensor();
        settings.setProperty(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY, "/");
        this.runSensor();
        settings.setProperty(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY, "./");
        this.runSensor();
        List<LoggingEvent> logEvents = this.verifyLogEvents(4);
        this.verifyLogContains(logEvents.get(0), Level.WARN, "report not found");
        this.verifyLogContains(logEvents.get(1), Level.WARN, "report not found");
        this.verifyLogContains(logEvents.get(2), Level.WARN, "report not found");
        this.verifyLogContains(logEvents.get(3), Level.WARN, "report not found");
    }

    @Test
    public void testInvalidReportXml() throws IOException {
        FileUtils.write(report, "<xml");
        this.runSensor();
        List<LoggingEvent> logEvents = this.verifyLogEvents(2);
        this.verifyLogContains(logEvents.get(1), Level.ERROR, "error parsing");
    }

    @Test
    public void testInvalidFilePath() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.UnusedMethodParameter, 4096, "invalidFilePath") +
                "</scapegoat>");
        settings.setProperty(SOURCES_PROPERTY_KEY, "src/main/scala");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        this.verifyIssues(0);
        List<LoggingEvent> logEvents = this.verifyLogEvents(2);
        this.verifyLogContains(logEvents.get(1), Level.WARN, "report source file not found");
    }

    @Test
    public void testNonIssuableRule() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.UnusedMethodParameter, 64, "foo.scala") +
                "</scapegoat>");
        this.addFile("foo.scala");
        settings.setProperty(SOURCES_PROPERTY_KEY, "src/main/scala");
        this.loadRules();
        this.runSensor();
        this.verifyIssues(0);
        List<LoggingEvent> logEvents = this.verifyLogEvents(2);
        this.verifyLogContains(logEvents.get(1), Level.WARN, "could not create issue");
    }

    @Test
    public void testMissingRule() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.UnusedMethodParameter, 64, "foo.scala").replace(".UnusedMethodParameter", ".MagicRule") +
                "</scapegoat>");
        this.addFile("foo.scala");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        this.verifyIssues(0);
        List<LoggingEvent> logEvents = this.verifyLogEvents(2);
        this.verifyLogContains(logEvents.get(1), Level.WARN, "rule not active");
    }

    @Test
    public void testMultipleSourceDirectoryParsing() throws IOException {
        FileUtils.write(report, "<scapegoat>" +
                Warning.getWarning(Warning.UnusedMethodParameter, 64, "foo.scala") +
                "</scapegoat>");
        this.addFile("src/main/scala/foo.scala");
        settings.setProperty(SOURCES_PROPERTY_KEY, "src/main/scala, ., src/main/scala-extra, src/main/scala");
        this.loadRules();
        this.makeIssuable();
        this.runSensor();
        List<Issue> issues = this.verifyIssues(1);
        this.verifyIssue(issues.get(0), Severity.MINOR, 64);
        this.verifyLogEvents(1);
    }


    private void makeIssuable()
    {
        when(perspectives.as(eq(Issuable.class), any(InputFile.class))).thenReturn(issuable);

        when(issuable.newIssueBuilder()).thenReturn(issueBuilder);

        when(issueBuilder.ruleKey(any(RuleKey.class))).thenReturn(issueBuilder);
        when(issueBuilder.line(anyInt())).thenReturn(issueBuilder);
        when(issueBuilder.severity(anyString())).thenReturn(issueBuilder);
        when(issueBuilder.message(anyString())).thenReturn(issueBuilder);
        when(issueBuilder.effortToFix(anyDouble())).thenReturn(issueBuilder);
        when(issueBuilder.build()).thenReturn(issue);

        when(issuable.addIssue(any(Issue.class))).thenReturn(true);
    }


    private void verifyIssue(Issue issue, String severity, Integer line)
    {
        assertThat(issue).as("Issue should be available").isNotNull();
        assertThat(issue.severity()).as("Issue should have severity of " + severity).isEqualTo(severity);
        assertThat(issue.line()).as("Issue should be at line " + String.valueOf(line)).isEqualTo(line);
    }

    private List<Issue> verifyIssues(Integer count)
    {
        List<Issue> issues = new ArrayList<>();
        if (! (count > 0)) {
            verify(issuable, never()).addIssue(issue);
            return issues;
        }
        verify(issuable, times(count)).addIssue(issue);

        ArgumentCaptor<Integer> lineCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(issueBuilder, times(count)).line(lineCaptor.capture());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(issueBuilder, times(count)).message(messageCaptor.capture());
        ArgumentCaptor<String> severityCaptor = ArgumentCaptor.forClass(String.class);
        verify(issueBuilder, times(count)).severity(severityCaptor.capture());

        List<Integer> lines = lineCaptor.getAllValues();
        List<String> messages = messageCaptor.getAllValues();
        List<String> severities = severityCaptor.getAllValues();
        assertThat(lines).hasSize(count);
        assertThat(messages).hasSize(count);
        assertThat(severities).hasSize(count);

        for (int i = 0; i < count; i++) {
            DefaultIssue issue = new DefaultIssue();
            issue.setMessage(messages.get(i));
            issue.setLine(lines.get(i));
            issue.setSeverity(severities.get(i));
            issues.add(issue);
        }
        assertThat(issues).hasSize(count);
        return issues;
    }

    private void verifyLogContains(LoggingEvent event, Level level, String text)
    {
        assertThat(event).as("Log event should be available").isNotNull();
        assertThat(event.getLevel()).as("Log event should be at level " + String.valueOf(level)).isEqualTo(level);
        assertThat(event.getMessage()).as("Log message should contain '" + text + "'").contains(text);
    }

    private List<LoggingEvent> verifyLogEvents(Integer count)
    {
        ArgumentCaptor<LoggingEvent> logEventCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(count)).doAppend(logEventCaptor.capture());
        List<LoggingEvent> logEvents = logEventCaptor.getAllValues();
        assertThat(logEvents).hasSize(count);
        return logEvents;
    }

    private void addFile(String relativePath)
    {
        fileSystem.add(this.createInputFile(relativePath));
    }

    private InputFile createInputFile(String relativePath)
    {
        return new DefaultInputFile(relativePath).setAbsolutePath(new File(baseDir, relativePath).getAbsolutePath()).setLanguage("scala");
    }

    private void loadRules()
    {
        ScapegoatRulesDefinition def = new ScapegoatRulesDefinition();
        RulesDefinition.Context ruleContext = new RulesDefinition.Context();
        def.define(ruleContext);
        for (RulesDefinition.Rule ruleDefinition : ruleContext.repository(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY_KEY).rules()) {
            org.sonar.api.rules.Rule rule = org.sonar.api.rules.Rule.create(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY_KEY, ruleDefinition.key());
            profile.activateRule(rule, RulePriority.valueOf(ruleDefinition.severity()));
        }
    }

}
