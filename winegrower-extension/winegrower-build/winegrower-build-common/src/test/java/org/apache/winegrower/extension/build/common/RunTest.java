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
package org.apache.winegrower.extension.build.common;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import org.apache.winegrower.Ripener;
import org.junit.jupiter.api.Test;

class RunTest {

    @Test
    void run() throws InterruptedException {
        final InputStream in = System.in;
        final ControlledStream controlledStream = new ControlledStream();
        System.setIn(controlledStream);
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        try {
            final String systemProp = getClass().getName() + ".run";
            final Run run = new Run(new Ripener.Configuration(), singletonMap(systemProp, "set")) {

                @Override
                protected void onStop() {
                    stopped.countDown();
                }

                @Override
                protected void onStart() {
                    started.countDown();
                }
            };
            new Thread(run).start();
            started.await();
            assertEquals("set", System.getProperty(systemProp));
            assertNotNull(run.getFramework());
            controlledStream.done();
            stopped.await();
            assertNull(System.getProperty(systemProp));
            assertNull(run.getFramework());
        } finally {
            System.setIn(in);
        }
    }

    private static class ControlledStream extends InputStream {

        private volatile ByteArrayInputStream delegate;

        private final CountDownLatch latch = new CountDownLatch(1);

        void done() {
            delegate = new ByteArrayInputStream(("exit" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            latch.countDown();
        }

        @Override
        public int read() {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                ;
            }
            return delegate.read();
        }
    }
}
