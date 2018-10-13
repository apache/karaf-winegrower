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
package org.apache.winegrower.scanner.manifest;

import java.util.function.Supplier;
import java.util.jar.Manifest;

public class ManifestCreator implements Supplier<Manifest> {
    private Manifest manifest;
    private final String name;

    public ManifestCreator(final String name) {
        this.name = name;
    }

    @Override
    public Manifest get() {
        return manifest == null ? manifest = create() : manifest;
    }

    public Manifest getManifest() {
        return manifest;
    }

    private Manifest create() {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Bundle-SymbolicName", name);
        return manifest;
    }
}
