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

import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Scanner;

import org.apache.winegrower.Ripener;

public class Run implements Runnable {
    private final Ripener.Configuration configuration;
    private final Map<String, String> systemVariables;
    private Ripener framework;

    public Run(final Ripener.Configuration configuration,
               final Map<String, String> systemVariables) {
        this.configuration = configuration;
        this.systemVariables = systemVariables;
    }

    public Ripener getFramework() {
        return framework;
    }

    @Override
    public void run() {
        final Map<String, String> oldSystemPropsValues;
        if (systemVariables != null) {
            oldSystemPropsValues = systemVariables.keySet().stream()
                                                  .filter(it -> System.getProperty(it) != null)
                                                  .collect(toMap(identity(), System::getProperty));
            systemVariables.forEach(System::setProperty);
        } else {
            oldSystemPropsValues = emptyMap();
        }
        try (final Ripener framework = new Ripener.Impl(configuration).start()) {
            this.framework = framework;
            onStart();
            waitForExit();
        } finally {
            this.framework = null;
            if (systemVariables != null) {
                systemVariables.keySet().forEach(key -> {
                    final String value = oldSystemPropsValues.get(key);
                    if (value != null) {
                        System.setProperty(key, value);
                    } else {
                        System.clearProperty(key);
                    }
                });
            }
            onStop();
        }
    }

    protected void waitForExit() {
        final Scanner scanner = new Scanner(System.in);
        String command;
        while ((command = scanner.next()) != null) {
            if ("exit".equalsIgnoreCase(command)) {
                break;
            }
        }
    }

    protected void onStop() {
        // no-op
    }

    protected void onStart() {
        // no-op
    }
}
