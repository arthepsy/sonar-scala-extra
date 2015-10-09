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

import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

import org.sonar.api.server.rule.RulesDefinition;

public class ScapegoatRulesDefinitionTest {

    @Test
    public void testRepository() {
        ScapegoatRulesDefinition def = new ScapegoatRulesDefinition();
        RulesDefinition.Context context = new RulesDefinition.Context();
        def.define(context);

        RulesDefinition.Repository repo = context.repository("scapegoat");
        assertThat(repo).as("Repository should be available").isNotNull();
        assertThat(repo.name()).as("Repository name should be Scapegoat").isEqualTo("Scapegoat");
        assertThat(repo.language()).as("Repository language should be scala").isEqualTo("scala");
        assertThat(repo.rules()).as("Repository should contain 117 rules").hasSize(117);
    }

}
