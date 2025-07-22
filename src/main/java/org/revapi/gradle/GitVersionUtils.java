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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
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

    public final Provider<Stream<String>> previousGitTags() {
        return execute("git", "tag", "-l", "--merged", "HEAD^", "--sort=-committerdate")
                .map(tagsResult -> {
                    if (tagsResult.exitCode() != 0 || tagsResult.stdout().isEmpty()) {
                        return Stream.<String>empty();
                    }

                    return Arrays.stream(tagsResult.stdout().split("\n"))
                            .filter(tag -> !isInitial000Tag(tag))
                            .map(GitVersionUtils::stripVFromTag);
                });
    }

    private boolean isInitial000Tag(String tag) {
        if (!tag.equals("0.0.0")) {
            return false;
        }

        GitResult result = execute("git", "rev-parse", "--verify", "--quiet", "0.0.0^").get();
        boolean parentDoesNotExist = result.exitCode() != 0;
        return parentDoesNotExist;
    }

    private static String stripVFromTag(String tag) {
        if (tag.startsWith("v")) {
            return tag.substring(1);
        } else {
            return tag;
        }
    }

    private Provider<GitResult> execute(String... command) {
        ExecOutput output = getProviderFactory().exec(execSpec -> {
            execSpec.commandLine((Object[]) command);
            execSpec.setIgnoreExitValue(true);
            execSpec.setWorkingDir(getProjectLayout().getProjectDirectory());
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
}
