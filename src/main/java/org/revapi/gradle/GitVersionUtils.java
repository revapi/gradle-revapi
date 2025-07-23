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

import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecResult;
import org.immutables.value.Value;

public abstract class GitVersionUtils {

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    public final Stream<String> previousGitTags() {
        return StreamSupport.stream(
                        new PreviousGitTags(
                                getProviderFactory(), getProjectLayout().getProjectDirectory()),
                        false)
                .filter(tag -> !isInitial000Tag(
                        getProviderFactory(), getProjectLayout().getProjectDirectory(), tag))
                .map(GitVersionUtils::stripVFromTag);
    }

    private static Optional<String> previousGitTagFromRef(
            ProviderFactory providerFactory, Directory directory, String ref) {
        String beforeLastRef = ref + "^";

        GitResult beforeLastRefTypeResult = execute(providerFactory, directory, "git", "cat-file", "-t", beforeLastRef)
                .get();

        boolean thereIsNoCommitBeforeTheRef = !beforeLastRefTypeResult.stdout().equals("commit");
        if (thereIsNoCommitBeforeTheRef) {
            return Optional.empty();
        }

        GitResult describeResult = execute(
                        providerFactory, directory, "git", "describe", "--tags", "--abbrev=0", beforeLastRef)
                .get();

        if (describeResult.stderr().contains("No tags can describe")
                || describeResult.stderr().contains("No names found, cannot describe anything")) {
            return Optional.empty();
        }

        return Optional.of(describeResult.stdoutOrThrowIfNonZero());
    }

    private static boolean isInitial000Tag(ProviderFactory providerFactory, Directory directory, String tag) {
        if (!tag.equals("0.0.0")) {
            return false;
        }

        return execute(providerFactory, directory, "git", "rev-parse", "--verify", "--quiet", "0.0.0^")
                        .get()
                        .exitCode()
                != 0;
    }

    private static String stripVFromTag(String tag) {
        if (tag.startsWith("v")) {
            return tag.substring(1);
        } else {
            return tag;
        }
    }

    private static Provider<GitResult> execute(
            ProviderFactory providerFactory, Directory directory, String... command) {
        ExecOutput output = providerFactory.exec(execSpec -> {
            execSpec.commandLine((Object[]) command);
            execSpec.setIgnoreExitValue(true);
            execSpec.setWorkingDir(directory);
        });

        Provider<String> stdout = output.getStandardOutput().getAsText();
        Provider<String> stderr = output.getStandardError().getAsText();
        Provider<ExecResult> result = output.getResult();

        return stdout.zip(stderr, (out, err) -> new Object[] {out, err})
                .zip(result, (outErr, res) -> GitResult.builder()
                        .exitCode(res.getExitValue())
                        .stdout(((String) outErr[0]).trim())
                        .stderr(((String) outErr[1]).trim())
                        .build());
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
        private final ProviderFactory providerFactory;
        private final Directory directory;
        private String lastSeenRef = "HEAD";

        PreviousGitTags(ProviderFactory providerFactory, Directory directory) {
            this.providerFactory = providerFactory;
            this.directory = directory;
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            Optional<String> tag = previousGitTagFromRef(providerFactory, directory, lastSeenRef);

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
