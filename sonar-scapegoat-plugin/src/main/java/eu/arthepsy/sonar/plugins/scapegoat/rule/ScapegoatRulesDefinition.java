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
import org.apache.commons.lang.StringUtils;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.LoggerFactory;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition;
import eu.arthepsy.sonar.plugins.scapegoat.language.Scala;
import eu.arthepsy.sonar.plugins.scapegoat.util.XmlUtils;
import org.sonar.api.utils.ValidationMessages;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

public class ScapegoatRulesDefinition implements RulesDefinition {

    public static final String SCAPEGOAT_REPOSITORY_KEY = "scapegoat";
    public static final String SCAPEGOAT_REPOSITORY_NAME = "Scapegoat";

    protected InputStream getRulesStream() {
        return getClass().getResourceAsStream(ScapegoatConfiguration.RULES_FILE);
    }

    @Override
    public void define(Context context) {
        GenericRulesParser<NewRepository> parser = new GenericRulesParser<NewRepository>(this.getRulesStream()) {
            @Override
            void parseRule(NewRepository repository, SMInputCursor ruleCursor, ValidationMessages messages) throws XMLStreamException {
                String key = null;
                String name = null;
                String description = null;
                String severity = Severity.defaultSeverity();
                String status = null;

                SMInputCursor cursor = ruleCursor.childElementCursor();
                while (cursor.getNext() != null) {
                    String nodeName = cursor.getLocalName();
                    if (StringUtils.equalsIgnoreCase("key", nodeName)) {
                        key = XmlUtils.getNodeText(cursor);
                    } else if (StringUtils.equalsIgnoreCase("name", nodeName)) {
                        name = XmlUtils.getNodeText(cursor);
                    } else if (StringUtils.equalsIgnoreCase("description", nodeName)) {
                        description = XmlUtils.getNodeText(cursor);
                    } else if (StringUtils.equalsIgnoreCase("severity", nodeName)) {
                        severity = XmlUtils.getNodeText(cursor);
                    } else if (StringUtils.equalsIgnoreCase("status", nodeName)) {
                        status = XmlUtils.getNodeText(cursor);
                    }
                }
                RulesDefinition.NewRule rule = repository.createRule(key)
                        .setInternalKey(key)
                        .setName(name)
                        .setMarkdownDescription(description)
                        .setSeverity(severity)
                        .setTemplate(false);
                if (status != null) {
                    rule.setStatus(RuleStatus.valueOf(status));
                }
            }
        };

        final NewRepository repository = context.createRepository(SCAPEGOAT_REPOSITORY_KEY, Scala.KEY).setName(SCAPEGOAT_REPOSITORY_NAME);
        parser.parse(repository);
        parser.log(LoggerFactory.getLogger(ScapegoatRulesDefinition.class), ScapegoatConfiguration.LOG_PREFIX);
        repository.done();
    }
}
