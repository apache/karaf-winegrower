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
package org.apache.winegrower.test.simpleactivator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class MyActivator implements BundleActivator {
    private BundleContext context;
    private int started = 0;
    private int stopped = 0;

    @Override
    public void start(BundleContext context) {
        this.context = context;
        this.started++;
    }

    @Override
    public void stop(final BundleContext context) {
        stopped++;
        assertEquals(this.context, context);
    }

    public int getStopped() {
        return stopped;
    }

    public BundleContext getContext() {
        return context;
    }

    public int getStarted() {
        return started;
    }
}
