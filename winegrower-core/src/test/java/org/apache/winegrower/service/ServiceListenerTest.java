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
package org.apache.winegrower.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.test.WithRipener;
import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

class ServiceListenerTest {
    @Test
    @WithRipener(includeResources = @WithRipener.Entry(path = "org.apache.winegrower.test.listener.filter"))
    void withFilter(@WithRipener.Service final Ripener ripener) throws InvalidSyntaxException {
        final BundleContext context = ripener.getRegistry().getBundles().get(0L).getBundle().getBundleContext();
        final ServiceReference<?>[] references = context.getAllServiceReferences(null, "(org.apache.winegrower.test.listener.filter.Registrator=true)");
        assertEquals(1, references.length);
    }
}
