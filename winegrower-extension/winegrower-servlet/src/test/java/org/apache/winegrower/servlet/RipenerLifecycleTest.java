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
package org.apache.winegrower.servlet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.inject.Inject;
import javax.servlet.ServletContext;

import org.apache.logging.log4j.spi.Provider;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.apache.winegrower.Ripener;
import org.apache.winegrower.api.InjectedService;
import org.junit.jupiter.api.Test;

@MeecrowaveConfig
class RipenerLifecycleTest {
    @Inject
    private ServletContext context;

    @InjectedService
    private Provider log4j;

    @Test
    void ensureIsStarted() {
        final Object attribute = context.getAttribute(Ripener.class.getName());
        assertNotNull(attribute);
        final Ripener ripener = Ripener.class.cast(attribute);
        assertTrue(ripener.getRegistry().getBundles().size() > 3 /*cxf, log4j, ....*/);
        assertTrue(ripener.getStartTime() > 0);
        assertNull(log4j);
        ripener.getServices().inject(this);
        assertNotNull(log4j);
    }
}
