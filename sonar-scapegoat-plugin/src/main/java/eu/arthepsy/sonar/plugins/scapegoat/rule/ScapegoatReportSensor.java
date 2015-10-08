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

import eu.arthepsy.sonar.plugins.scapegoat.ScapegoatConfiguration;
import eu.arthepsy.sonar.plugins.scapegoat.language.Scala;
import eu.arthepsy.sonar.plugins.scapegoat.util.XmlUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.ActiveRule;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScapegoatReportSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(ScapegoatReportSensor.class);
    private static final String LOG_PREFIX = ScapegoatConfiguration.LOG_PREFIX;
    private static final String SOURCES_PROPERTY_KEY = "sonar.sources";
    private static final String RESOLVED_SOURCES_KEY = "sonar.scala.scapegoat.resolved_sources";

    private final Settings settings;
    private final RulesProfile profile;
    private final FileSystem fs;
    private ResourcePerspectives perspectives;

    public ScapegoatReportSensor(Settings settings, RulesProfile profile, FileSystem fs, ResourcePerspectives perspectives) {
        this.fs = fs;
        this.profile = profile;
        this.settings = settings;
        this.perspectives = perspectives;
    }

    @Override
    public boolean shouldExecuteOnProject(Project project) {
        return fs.hasFiles(fs.predicates().hasLanguage(Scala.KEY)) &&
                !profile.getActiveRulesByRepository(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY_KEY).isEmpty();
    }

    @Override
    public void analyse(Project project, SensorContext context) {
        String reportPath = settings.getString(ScapegoatConfiguration.REPORT_PATH_PROPERTY_KEY);
        File report = this.getReport(reportPath);
        if (report != null) {
            LOG.info(LOG_PREFIX + "analyzing report: " + String.valueOf(reportPath));
            this.parseReport(report);
        } else {
            LOG.warn(LOG_PREFIX + "report not found: " + String.valueOf(reportPath));
        }
    }

    private File getReport(String reportPath) {
        if (StringUtils.isNotBlank(reportPath)) {
            File report = new File(reportPath);
            if (! report.isAbsolute()) {
                report = new File(fs.baseDir(), reportPath);
            }
            if (report.isFile()) {
                return report;
            }
        }
        return null;
    }

    private void parseReport(File report) {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(report);
            this.parseReport(stream);
        } catch (FileNotFoundException e) {
            LOG.error(LOG_PREFIX + " file not found error: " + e.getMessage());
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    private void parseReport(InputStream stream) {
        SMInputFactory factory = XmlUtils.createFactory();
        try {
            SMHierarchicCursor rootC = factory.rootElementCursor(stream);
            rootC.advance(); // scapegoat
            SMInputCursor ruleC = rootC.childElementCursor("warning");
            while (ruleC.getNext() != null) {
                this.parseWarning(ruleC);
            }
        } catch (XMLStreamException e) {
            LOG.error(LOG_PREFIX + " xml error parsing report: " + e.getMessage());
        }
    }

    private void parseWarning(SMInputCursor cursor) throws XMLStreamException {
        String warnText = StringUtils.trim(cursor.getAttrValue("text"));
        int warnLine = Integer.valueOf(StringUtils.trim(cursor.getAttrValue("line")));
        String warnFile = StringUtils.trim(cursor.getAttrValue("file"));
        String warnInspection = StringUtils.trim(cursor.getAttrValue("inspection"));

        RuleKey ruleKey = RuleKey.of(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY_KEY, warnInspection);
        ActiveRule rule = profile.getActiveRule(ruleKey.repository(), ruleKey.rule());
        if (rule != null) {
            warnFile = this.parseFilePath(warnFile);
            InputFile inputFile = this.resolveFile(warnFile);
            if (inputFile != null) {
                Issuable issuable = perspectives.as(Issuable.class, inputFile);
                if (issuable != null) {
                    Issue issue = issuable.newIssueBuilder()
                            .ruleKey(ruleKey)
                            .line(warnLine)
                            .message(warnText)
                            .severity(rule.getSeverity().name())
                            .build();
                    issuable.addIssue(issue);
                } else {
                    LOG.warn(LOG_PREFIX + "could not create issue from file: " + inputFile.toString());
                }
            } else {
                LOG.warn(LOG_PREFIX + "report source file not found: " + warnFile);
            }
        } else {
            LOG.warn(LOG_PREFIX + "report rule not active: " + warnInspection);
        }
    }

    private String[] getSourceDirectories() {
        List<String> sourceDirectories = new ArrayList<String>();
        Set<String> uniqueSourceDirectories = new HashSet<String>();
        File baseDir = fs.baseDir();
        String baseDirPath = baseDir.getAbsolutePath();
        String sources = StringUtils.defaultString(settings.getString(SOURCES_PROPERTY_KEY));
        for (String sourceDirectory : StringUtils.stripAll(StringUtils.split(sources, ','))) {
            File sourceDirectoryFile = new File(baseDir, sourceDirectory);
            String sourceDirectoryPath = sourceDirectoryFile.toPath().normalize().toFile().getAbsolutePath();
            if (! sourceDirectoryPath.equals(baseDirPath)) {
                if (uniqueSourceDirectories.add(sourceDirectoryPath)) {
                    sourceDirectories.add(sourceDirectoryPath);
                }
            }
        }
        return sourceDirectories.toArray(new String[sourceDirectories.size()]);
    }

    private InputFile resolveFile(String filePath)
    {
        if (! settings.hasKey(RESOLVED_SOURCES_KEY)) {
            String directories[] = this.getSourceDirectories();
            settings.setProperty(RESOLVED_SOURCES_KEY, StringUtils.join(directories, ','));
        }
        String[] sourceDirectories = settings.getStringArray(RESOLVED_SOURCES_KEY);
        FilePredicates p = fs.predicates();
        String resolvedPath = parseFilePath(filePath);
        // absolute path
        if (new File(resolvedPath).isAbsolute()) {
            return fs.inputFile(p.hasAbsolutePath(resolvedPath));
        }
        // relative path in source directory
        InputFile foundFile = null;
        List<String> foundInDirectories = new ArrayList<String>();
        for (String sourceDirectory: sourceDirectories) {
            File sourceFile = new File(sourceDirectory, resolvedPath);
            InputFile sourceInputFile = fs.inputFile(p.hasAbsolutePath(sourceFile.getAbsolutePath()));
            if (sourceInputFile != null) {
                foundFile = sourceInputFile;
                foundInDirectories.add(sourceDirectory);
            }
        }
        if (foundInDirectories.size() == 1) {
            return foundFile;
        } else if (foundInDirectories.size() > 0) {
            LOG.warn(LOG_PREFIX + "source file " + resolvedPath + " found in multiple source directories: " + StringUtils.join(foundInDirectories, ','));
            return null;
        }
        // relative path in base directory
        return fs.inputFile(p.hasRelativePath(resolvedPath));
    }

    private String parseFilePath(String filePath)
    {
        int last = filePath.lastIndexOf('.');
        if (last > 0) {
            return filePath.substring(0, last).replace('.', '/').concat(filePath.substring(last));
        } else {
            return filePath;
        }
    }

}
