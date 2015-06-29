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

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.utils.ValidationMessages;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ScapegoatQualityProfileTest {

    private ScapegoatQualityProfile profile;
    private ValidationMessages validationMessages;

    @Before
    public void prepare() {
        profile = Mockito.spy(new ScapegoatQualityProfile());
        validationMessages = ValidationMessages.create();
    }

    @Test
    public void testRulesExistence() {
        assertThat(profile.getRulesStream()).isNotNull();
    }

    @Test
    public void testRulesCount() {
        RulesProfile rulesProfile =  profile.createProfile(validationMessages);
        assertThat(rulesProfile.getActiveRules().size()).isEqualTo(117);
    }

    @Test
    public void testValidationError() {
        when(profile.getRulesStream()).thenReturn(IOUtils.toInputStream("<xml"));
        RulesProfile rulesProfile =  profile.createProfile(validationMessages);
        assertThat(rulesProfile.getActiveRules().size()).isEqualTo(0);
        assertThat(validationMessages.getErrors().size()).isEqualTo(1);
    }

}
