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
package org.apache.winegrower.api;

import org.apache.winegrower.Ripener;

/**
 * Enables to interact with Ripener before/after it is active.
 * Very convenient to register custom built in services for example.
 * It is registered as a plain java SPI (META-INF/services/org.apache.winegrower.api.LifecycleCallbacks).
 */
public interface LifecycleCallbacks {
    /**
     * @return callbacks are sorted thanks to this order (natural int order).
     */
    default int order() {
        return 1000;
    }

    // called before ripener is setup
    default void processConfiguration(final Ripener.Configuration configuration) {
        // no-op
    }

    default void beforeStart(final Ripener ripener) {
        // no-op
    }

    default void afterStart(final Ripener ripener) {
        // no-op
    }

    default void beforeStop(final Ripener ripener) {
        // no-op
    }

    default void afterStop(final Ripener ripener) {
        // no-op
    }
}
