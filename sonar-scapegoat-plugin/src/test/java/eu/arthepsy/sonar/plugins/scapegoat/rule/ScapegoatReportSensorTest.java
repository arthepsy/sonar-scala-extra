/*
 * Scapegoat
 * Copyright (C) 2014 Andris Raugulis
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

import eu.arthepsy.sonar.plugins.scapegoat.ScapegoatConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.issue.internal.DefaultIssueBuilder;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ScapegoatReportSensorTest {

    private Project project;
    ResourcePerspectives perspectives;
    private SensorContext context;
    private ScapegoatReportSensor sensor;
    private DefaultFileSystem fileSystem;
    private Settings settings;

    private static final String SOURCES_PROPERTY_KEY = "sonar.sources";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private File baseDir;

    @Before
    public void prepare() throws IOException {
        baseDir = temp.newFolder();
        project = mock(Project.class);
        perspectives = mock(ResourcePerspectives.class);
        context = mock(SensorContext.class);

        when(project.key()).thenReturn("pr0j");

        sensor = new ScapegoatReportSensor(project, perspectives);
        fileSystem = new DefaultFileSystem();
        fileSystem.setBaseDir(baseDir);
        settings = new Settings();
        when(context.fileSystem()).thenReturn(fileSystem);
        when(context.settings()).thenReturn(settings);
    }

    @Test
    public void testDescriptor() {
        sensor.describe(new DefaultSensorDescriptor());
    }

    @Test
    public void testSensor() throws IOException {
        File report = new File(baseDir, "scapegoat-report.xml");
        String dirPrefix = baseDir.getPath().replace('/', '.');
        FileUtils.write(report,
            "<scapegoat>" +
                "<warning line=\"69\" text=\"Unused method parameter\" snippet=\"Unused method parameter (val app: play.api.Application = _)\" level=\"Warning\" file=\"" + dirPrefix + ".src.app.foo.scala\" inspection=\"com.sksamuel.scapegoat.inspections.unneccesary.UnusedMethodParameter\"/>" +
                "<warning line=\"123\" text=\"Use of Option.get\" level=\"Error\" file=\"bar.scala\" inspection=\"com.sksamuel.scapegoat.inspections.option.OptionGet\"/>" +
                "<warning line=\"256\" text=\"Looks for interpolated strings that have no arguments\" level=\"Error\" file=\"src.foobar.scala\" inspection=\"com.sksamuel.scapegoat.inspections.string.EmptyInterpolatedString\"/> " +
            "</scapegoat>");
        fileSystem.add(this.createInputFile("src/app/foo.scala"));
        fileSystem.add(this.createInputFile("src/app/bar.scala"));
        fileSystem.add(this.createInputFile("src/foobar.scala"));

        settings.setProperty(SOURCES_PROPERTY_KEY, "src/app");
        settings.setProperty(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY, report.getAbsolutePath());

        this.loadRules();

        Issuable issuable = mock(Issuable.class);
        when(perspectives.as(eq(Issuable.class), any(Resource.class))).thenReturn(issuable);
        when(issuable.addIssue(any(Issue.class))).thenReturn(true);

        sensor.execute(context);

        ArgumentCaptor<Issue> argCaptor = ArgumentCaptor.forClass(Issue.class);
        verify(issuable, times(3)).addIssue(argCaptor.capture());
        List<Issue> values = argCaptor.getAllValues();
        assertThat(values).hasSize(3);
        assertThat(values.get(0).severity()).isEqualTo(Severity.MINOR);
        assertThat(values.get(0).line()).isEqualTo(69);
        assertThat(values.get(1).severity()).isEqualTo(Severity.MAJOR);
        assertThat(values.get(1).line()).isEqualTo(123);
        assertThat(values.get(2).severity()).isEqualTo(Severity.MAJOR);
        assertThat(values.get(2).line()).isEqualTo(256);
    }

    private InputFile createInputFile(String relativePath)
    {
        return new DefaultInputFile(relativePath).setAbsolutePath(new File(baseDir, relativePath).getAbsolutePath()).setLanguage("scala");
    }

    private void loadRules()
    {
        ActiveRulesBuilder builder = new ActiveRulesBuilder();
        ScapegoatRulesDefinition def = new ScapegoatRulesDefinition();
        RulesDefinition.Context ruleContext = new RulesDefinition.Context();
        def.define(ruleContext);
        for (RulesDefinition.Rule rule : ruleContext.repository(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY).rules()) {
            RuleKey ruleKey = RuleKey.of(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY, rule.key());
            builder
                    .create(ruleKey)
                    .setInternalKey(rule.key())
                    .setSeverity(rule.severity())
                    .setLanguage("scala")
                    .activate();
        }
        ActiveRules activeRules = builder.build();
        when(context.activeRules()).thenReturn(activeRules);
        when(context.issueBuilder()).thenReturn(new DefaultIssueBuilder());
    }

}
