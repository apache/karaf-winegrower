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
package org.apache.winegrower.examples;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import static org.osgi.framework.Constants.BUNDLE_ACTIVATOR;

@Header(name = BUNDLE_ACTIVATOR, value = "${@class}")
public class Simple implements BundleActivator {

    public void start(BundleContext bundleContext) {
        System.out.println("Starting simple winegrower application");
    }

    public void stop(BundleContext bundleContext) {
        System.out.println("Stopping simple winegrower application");
    }

}
