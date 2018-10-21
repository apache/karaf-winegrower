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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.xbean.asm7.Opcodes.ACC_PUBLIC;
import static org.apache.xbean.asm7.Opcodes.ACC_SUPER;
import static org.apache.xbean.asm7.Opcodes.ALOAD;
import static org.apache.xbean.asm7.Opcodes.INVOKESPECIAL;
import static org.apache.xbean.asm7.Opcodes.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.winegrower.scanner.manifest.ActivatorManifestContributor;
import org.apache.xbean.asm7.ClassWriter;
import org.apache.xbean.asm7.MethodVisitor;
import org.junit.jupiter.api.Test;

class ManifestCreatorTest {
    @Test
    void create() {
        final File output = new File("target/manifest/create.jar");
        final File module = createModuleWithActivator(new File(output.getParentFile(), "module.jar"));
        if (output.exists()) {
            output.delete();
        }
        new ManifestCreator(new ManifestCreator.Configuration(
                emptyList(),
                module,
                singletonList(ActivatorManifestContributor.class.getName()),
                null,
                singletonMap("test", "true"),
                output
        )).run();
        assertTrue(output.exists());
        final Manifest manifest = new Manifest();
        try (final InputStream in = new FileInputStream(output)) {
            manifest.read(in);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        final Attributes mainAttributes = manifest.getMainAttributes();
        assertEquals("1.0", mainAttributes.getValue("Manifest-Version"));
        assertEquals("org.Activator", mainAttributes.getValue("Bundle-Activator"));
        assertEquals("true", mainAttributes.getValue("test"));
    }

    private File createModuleWithActivator(final File file) {
        file.getParentFile().mkdirs();
        try (final JarOutputStream out = new JarOutputStream(new FileOutputStream(file))) {
            out.putNextEntry(new JarEntry("org/"));
            out.closeEntry();

            out.putNextEntry(new JarEntry("org/Activator.class"));

            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(52, ACC_PUBLIC + ACC_SUPER, "org/Activator", null, "java/lang/Object", new String[] { "org/osgi/framework/BundleActivator" });
            cw.visitAnnotation("Lorg/apache/winegrower/api/ImplicitActivator;", true).visitEnd();
            {
                final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "start", "(Lorg/osgi/framework/BundleContext;)V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 2);
                mv.visitEnd();
            }
            {
                final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "stop", "(Lorg/osgi/framework/BundleContext;)V", null, null);
                mv.visitCode();
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 2);
                mv.visitEnd();
            }
            cw.visitEnd();

            out.write(cw.toByteArray());
            out.closeEntry();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return file;
    }
}
