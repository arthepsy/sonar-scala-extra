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
import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.ValidationMessages;
import eu.arthepsy.sonar.plugins.scapegoat.language.Scala;
import eu.arthepsy.sonar.plugins.scapegoat.util.XmlUtils;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

public class ScapegoatQualityProfile extends ProfileDefinition {

    private static final String SCAPEGOAT_PROFILE = "Sonar way";

    protected InputStream getRulesStream() {
        return ScapegoatQualityProfile.class.getClass().getResourceAsStream(ScapegoatConfiguration.RULES_FILE);
    }

    @Override
    public RulesProfile createProfile(ValidationMessages validationMessages) {
        GenericRulesParser<RulesProfile> parser = new GenericRulesParser<RulesProfile>(this.getRulesStream(), validationMessages) {
            @Override
            void parseRule(RulesProfile profile, SMInputCursor ruleCursor, ValidationMessages messages) throws XMLStreamException {
                String key = null;
                String severity = Severity.defaultSeverity();
                String status = null;

                SMInputCursor cursor = ruleCursor.childElementCursor();
                while (cursor.getNext() != null) {
                    String nodeName = cursor.getLocalName();
                    if (StringUtils.equalsIgnoreCase("key", nodeName)) {
                        key = XmlUtils.getNodeText(cursor);
                    } else if (StringUtils.equalsIgnoreCase("severity", nodeName)) {
                        severity = XmlUtils.getNodeText(cursor);
                    } else if (StringUtils.equalsIgnoreCase("status", nodeName)) {
                        status = XmlUtils.getNodeText(cursor);
                    }
                }
                if (status != null && RuleStatus.valueOf(status) != RuleStatus.READY) {
                    return;
                }
                profile.activateRule(Rule.create(ScapegoatRulesDefinition.SCAPEGOAT_REPOSITORY_KEY, key), RulePriority.valueOf(severity));
            }
        };

        final RulesProfile profile = RulesProfile.create(SCAPEGOAT_PROFILE, Scala.KEY);
        parser.parse(profile);
        parser.log(LoggerFactory.getLogger(ScapegoatQualityProfile.class), ScapegoatConfiguration.LOG_PREFIX);
        return profile;
    }

}
