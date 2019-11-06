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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class MetadataBuilder {
    private final Properties manifests = new Properties();
    private final Properties index = new Properties();

    private String currentJar;
    private List<String> files;

    public Map<String, Properties> getMetadata() {
        final HashMap<String, Properties> meta = new HashMap<>();
        meta.put("index", index);
        meta.put("manifests", manifests);
        return meta;
    }

    public void onJar(final String jarName, final JarInputStream jarInputStream) {
        final Manifest manifest = jarInputStream.getManifest();
        if (manifest != null) {
            try (final ByteArrayOutputStream manifestStream = new ByteArrayOutputStream()) {
                manifest.write(manifestStream);
                manifestStream.flush();
                manifests.put(jarName, new String(manifestStream.toByteArray(), StandardCharsets.UTF_8));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        this.currentJar = jarName;
        this.files = new ArrayList<>();
    }

    public void onFile(final String name) {
        files.add(name);
    }

    public void afterJar() {
        index.put(currentJar, String.join(",", files));
        currentJar = null;
        files = null;
    }
}
