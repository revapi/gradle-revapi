/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.immutables.value.Value;

final class GitVersionUtils {
    private GitVersionUtils() {}

    public static Stream<String> previousGitTags(ExecOperations execOperations) {
        return StreamSupport.stream(new PreviousGitTags(execOperations), false)
                .filter(tag -> !isInitial000Tag(execOperations, tag))
                .map(GitVersionUtils::stripVFromTag);
    }

    private static Optional<String> previousGitTagFromRef(ExecOperations execOperations, String ref) {
        String beforeLastRef = ref + "^";

        GitResult beforeLastRefTypeResult = execute(execOperations, "git", "cat-file", "-t", beforeLastRef);

        boolean thereIsNoCommitBeforeTheRef = !beforeLastRefTypeResult.stdout().equals("commit");
        if (thereIsNoCommitBeforeTheRef) {
            return Optional.empty();
        }

        GitResult describeResult = execute(execOperations, "git", "describe", "--tags", "--abbrev=0", beforeLastRef);

        if (describeResult.stderr().contains("No tags can describe")
                || describeResult.stderr().contains("No names found, cannot describe anything")) {
            return Optional.empty();
        }

        return Optional.of(describeResult.stdoutOrThrowIfNonZero());
    }

    private static boolean isInitial000Tag(ExecOperations execOperations, String tag) {
        if (!tag.equals("0.0.0")) {
            return false;
        }

        GitResult foo = execute(execOperations, "git", "rev-parse", "--verify", "--quiet", "0.0.0^");
        boolean parentDoesNotExist = foo.exitCode() != 0;
        return parentDoesNotExist;
    }

    private static String stripVFromTag(String tag) {
        if (tag.startsWith("v")) {
            return tag.substring(1);
        } else {
            return tag;
        }
    }

    private static GitResult execute(ExecOperations execOperations, String... command) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ExecResult execResult = execOperations.exec(spec -> {
            spec.setCommandLine(Arrays.asList(command));
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.setIgnoreExitValue(true);
        });

        return GitResult.builder()
                .exitCode(execResult.getExitValue())
                .stdout(new String(stdout.toByteArray(), StandardCharsets.UTF_8).trim())
                .stderr(new String(stderr.toByteArray(), StandardCharsets.UTF_8).trim())
                .build();
    }

    @Value.Immutable
    interface GitResult {
        int exitCode();

        String stdout();

        String stderr();

        List<String> command();

        default String stdoutOrThrowIfNonZero() {
            if (exitCode() == 0) {
                return stdout();
            }

            throw new RuntimeException("Failed running command:\n"
                    + "\tCommand:" + command() + "\n"
                    + "\tExit code: " + exitCode() + "\n"
                    + "\tStdout:" + stdout() + "\n"
                    + "\tStderr:" + stderr() + "\n");
        }

        class Builder extends ImmutableGitResult.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }

    private static final class PreviousGitTags implements Spliterator<String> {
        private final ExecOperations execOperations;
        private String lastSeenRef = "HEAD";

        PreviousGitTags(ExecOperations execOperations) {
            this.execOperations = execOperations;
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            Optional<String> tag = previousGitTagFromRef(execOperations, lastSeenRef);

            if (!tag.isPresent()) {
                return false;
            }

            lastSeenRef = tag.get();
            action.accept(lastSeenRef);
            return true;
        }

        @Override
        public Spliterator<String> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return 0;
        }
    }
}
