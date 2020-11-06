/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.winegrower.extension.testing.junit5.internal;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.api.InjectedService;
import org.apache.winegrower.extension.testing.junit5.Winegrower;
import org.apache.winegrower.service.OSGiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Winegrower
@ExtendWith(WinegrowerExtensionTest.CustomInjector.class)
class WinegrowerExtensionTest {
    @InjectedService
    private Ripener ripener;

    @InjectedService
    private OSGiServices services;

    @Test
    void checkInjections(final Ripener ripener) {
        assertEquals(ripener, this.ripener);
        assertNotNull(services);
    }

    @Test
    void notOSGiInjectionWithoutQualifier(final CustomInjector extension) {
        assertNotNull(extension);
    }

    public static class CustomInjector implements ParameterResolver {
        @Override
        public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
            return CustomInjector.class == parameterContext.getParameter().getType();
        }

        @Override
        public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
            return this;
        }
    }
}
