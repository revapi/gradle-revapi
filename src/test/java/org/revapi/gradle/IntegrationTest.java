/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.revapi.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IntegrationTest {

    @TempDir
    Path projectDir;

    static Stream<String> gradleVersions() {
        return Stream.of("8.7", "9.4.0");
    }

    @BeforeEach
    void setup() throws IOException {
        writeFile("settings.gradle", "rootProject.name = 'compat-test'\n");
    }

    @ParameterizedTest
    @MethodSource("gradleVersions")
    void pluginAppliesAndTasksAreRegistered(String gradleVersion) throws IOException {
        writeFile(
                "build.gradle",
                "plugins {\n"
                        + "    id 'org.revapi.revapi-gradle-plugin'\n"
                        + "}\n"
                        + "\n"
                        + "repositories {\n"
                        + "    mavenCentral()\n"
                        + "}\n"
                        + "\n"
                        + "revapi {\n"
                        + "    oldGroup = 'org.codehaus.cargo'\n"
                        + "    oldName = 'empty-jar'\n"
                        + "    oldVersion = '1.7.7'\n"
                        + "}\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .withArguments("tasks", "--all", "--stacktrace")
                .forwardOutput()
                .build();

        assertThat(result.getOutput()).contains("revapiAnalyze");
        assertThat(result.getOutput()).contains("revapiAcceptBreak");
        assertThat(result.getOutput()).contains("revapiAcceptAllBreaks");
        assertThat(result.getOutput()).contains("revapiVersionOverride");
    }

    @ParameterizedTest
    @MethodSource("gradleVersions")
    void revapiTaskSucceedsWithNoBreakingChanges(String gradleVersion) throws IOException {
        writeFile(
                "build.gradle",
                "plugins {\n"
                        + "    id 'org.revapi.revapi-gradle-plugin'\n"
                        + "}\n"
                        + "\n"
                        + "repositories {\n"
                        + "    mavenCentral()\n"
                        + "}\n"
                        + "\n"
                        + "revapi {\n"
                        + "    oldGroup = 'org.codehaus.cargo'\n"
                        + "    oldName = 'empty-jar'\n"
                        + "    oldVersion = '1.7.7'\n"
                        + "}\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .withArguments("revapi", "--stacktrace")
                .forwardOutput()
                .build();

        assertThat(result.task(":revapiAnalyze").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":revapi").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @ParameterizedTest
    @MethodSource("gradleVersions")
    void revapiSkipsWhenOldVersionsIsEmpty(String gradleVersion) throws IOException {
        writeFile(
                "build.gradle",
                "plugins {\n"
                        + "    id 'org.revapi.revapi-gradle-plugin'\n"
                        + "}\n"
                        + "\n"
                        + "repositories {\n"
                        + "    mavenCentral()\n"
                        + "}\n"
                        + "\n"
                        + "revapi {\n"
                        + "    oldGroup = 'org.revapi'\n"
                        + "    oldName = 'revapi'\n"
                        + "    oldVersions = []\n"
                        + "}\n");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .withArguments("revapi", "--stacktrace")
                .forwardOutput()
                .build();

        assertThat(result.task(":revapiAnalyze").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(result.task(":revapi").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
    }

    private void writeFile(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
