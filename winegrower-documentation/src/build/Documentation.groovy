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
package org.apache.winegrower.build

import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.Placement

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

def menu = """<div class="menu">
</div>"""

def root = project.basedir.parentFile
def output = new File(project.build.directory, 'documentation')
def adoc = Asciidoctor.Factory.create()
def render = {file ->
    def module = file.parentFile.parentFile.parentFile.parentFile
    def relativePath = root.toPath().relativize(file.parentFile.toPath()).toString()
    def sanitizedpath = relativePath
            .replace('winegrower-', '')
            .replace('testing-', '')
            .replace('-plugin', '')
            .replace('/build', '')
            .replace('/testing', '')
            .replace(File.separator, '/')
            .replace('/src/main/asciidoc', '') + '/'
    def htmlName = file.name.substring(0, file.name.length() - 'adoc'.length()) + 'html'
    def fileName = (module.name == 'winegrower-documentation' ? '' : sanitizedpath) + htmlName
    log.info("Rendering ${file} to ${output}/${fileName}")
    def attributes = AttributesBuilder.attributes()
                .docType('article')
                .imagesDir("${project.basedir}/.asciidoctor/theme/manual/src/main/theme/images")
                .tableOfContents(Placement.RIGHT)
                .icons('font')
                .setAnchors(true)
                .stylesDir("${project.basedir}/.asciidoctor/theme/manual/src/main/asciidoc/style")
                .styleSheetName('apache.css')
                .attribute('source-highlighter', 'coderay')
                .attribute('data-uri')
                .attribute('idprefix')
                .attribute('idseparator', '-')
                .attribute('source-highlighter', 'coderay')
            .get()
    def options = OptionsBuilder.options()
            .backend('html5')
            .headerFooter(true)
            .inPlace(false)
            .baseDir(new File("${project.build.directory}"))
            .attributes(attributes)
    def rendered = adoc.render(file.text, options)
    def renderedFile = new File(output, fileName)
    renderedFile.parentFile.mkdirs()
    renderedFile.text = rendered.replace('<div id="header">', "<div id=\"header\">\n${menu}\n")
}

// only support flat .adoc in src/main/asciidoc for now
Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
    @Override
    FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        def asFile = dir.toFile()
        if (new File(asFile, 'pom.xml').exists()
                || new File(asFile, 'main').exists()
                || new File(asFile, 'asciidoc').exists()
                || 'asciidoc' == asFile.name) {
            return FileVisitResult.CONTINUE
        }
        return FileVisitResult.SKIP_SUBTREE
    }

    @Override
    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        def asFile = file.toFile()
        if (asFile.name.endsWith(".adoc") && 'README.adoc' != asFile.name) {
            render(asFile)
        }
        return FileVisitResult.CONTINUE
    }
})