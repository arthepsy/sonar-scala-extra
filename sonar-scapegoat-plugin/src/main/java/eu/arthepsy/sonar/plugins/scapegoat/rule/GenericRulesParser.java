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

import eu.arthepsy.sonar.plugins.scapegoat.util.XmlUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.slf4j.Logger;
import org.sonar.api.utils.ValidationMessages;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

public abstract class GenericRulesParser<T> {
    private InputStream stream;
    private ValidationMessages messages;

    GenericRulesParser(InputStream rulesStream) {
        this(rulesStream, ValidationMessages.create());
    }
    GenericRulesParser(InputStream rulesStream, ValidationMessages validationMessages) {
        stream = rulesStream;
        messages = validationMessages;
    }

    abstract void parseRule(T container, SMInputCursor cursor, ValidationMessages messages) throws XMLStreamException;

    public void parse(T container) {
        SMInputFactory factory = XmlUtils.createFactory();
        try {
            SMHierarchicCursor rootC = factory.rootElementCursor(stream);
            rootC.advance();
            SMInputCursor ruleC = rootC.childElementCursor("rule");
            while (ruleC.getNext() != null) {
                this.parseRule(container, ruleC, messages);
            }
        } catch (XMLStreamException e) {
            messages.addErrorText("rules file is not valid: " + e.getMessage());
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    public void log(Logger logger, String prefix) {
        if (prefix == null) {
            prefix = "";
        }
        for (String msg : messages.getErrors()) {
            logger.error(prefix + msg);
        }
        for (String msg : messages.getWarnings()) {
            logger.warn(prefix + msg);
        }
        for (String msg : messages.getInfos()) {
            logger.info(prefix + msg);
        }
    }

}
