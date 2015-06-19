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
package eu.arthepsy.sonar.plugins.scapegoat;

import com.google.common.collect.ImmutableList;
//import eu.arthepsy.sonar.plugins.scapegoat.language.Scala;
import eu.arthepsy.sonar.plugins.scapegoat.rule.ScapegoatQualityProfile;
import eu.arthepsy.sonar.plugins.scapegoat.rule.ScapegoatReportSensor;
import eu.arthepsy.sonar.plugins.scapegoat.rule.ScapegoatRulesDefinition;
import org.sonar.api.SonarPlugin;

import java.util.List;

public class ScapegoatPlugin extends SonarPlugin {

    @Override
    public List getExtensions() {
        ImmutableList.Builder<Object> builder = ImmutableList.builder();
        builder.addAll(ScapegoatConfiguration.getPropertyDefinitions());
        //builder.add(Scala.class);
        builder.add(ScapegoatRulesDefinition.class);
        builder.add(ScapegoatQualityProfile.class);
        builder.add(ScapegoatReportSensor.class);
        return builder.build();
    }

}
