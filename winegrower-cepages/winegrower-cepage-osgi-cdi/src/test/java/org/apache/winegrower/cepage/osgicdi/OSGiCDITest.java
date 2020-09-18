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
package org.apache.winegrower.cepage.osgicdi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collection;
import java.util.Map;

import org.apache.winegrower.Ripener;
import org.apache.winegrower.api.InjectedService;
import org.apache.winegrower.deployer.OSGiBundleLifecycle;
import org.apache.winegrower.extension.testing.junit5.Winegrower;
import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.runtime.CDIComponentRuntime;
import org.osgi.service.cdi.runtime.dto.ContainerDTO;

@Winegrower
class OSGiCDITest {
    @InjectedService
    private Ripener ripener;

    @InjectedService
    private CDIComponentRuntime ccr;

    @Test
    void test() {
        assertNotNull(ccr);

        final Map<Long, OSGiBundleLifecycle> bundles = ripener.getRegistry().getBundles();
        final long id = bundles.entrySet().stream()
                .filter(e -> e.getValue().getBundle().getLocation().endsWith("test-classes"))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElseGet(() -> fail("no test-classes bundle"));

        final Collection<ContainerDTO> containerDTOs = ccr.getContainerDTOs(bundles.get(id).getBundle());
        assertNotNull(containerDTOs);
        assertEquals(1, containerDTOs.size());

        final ContainerDTO dto = containerDTOs.iterator().next();
        assertNotNull(dto);
        assertTrue(dto.errors.isEmpty(), () -> dto.errors.toString());
    }
}
