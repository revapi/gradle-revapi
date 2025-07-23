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

import java.util.Collections;
import java.util.Optional;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.revapi.gradle.config.AcceptedBreak;
import org.revapi.gradle.config.GroupNameVersion;
import org.revapi.gradle.config.Justification;

public abstract class RevapiAcceptBreakTask extends DefaultTask {
    private static final String CODE_OPTION = "code";
    private static final String OLD_OPTION = "old";
    private static final String NEW_OPTION = "new";
    private static final String JUSTIFICATION_OPTION = "justification";

    @Input
    @Option(option = CODE_OPTION, description = "Revapi change code")
    public abstract Property<String> getCode();

    @Input
    @Option(option = OLD_OPTION, description = "Old API element")
    public abstract Property<String> getOldElement();

    @Input
    @Option(option = NEW_OPTION, description = "New API element")
    public abstract Property<String> getNewElement();

    @Input
    @Option(option = JUSTIFICATION_OPTION, description = "Justification for why these breaks are ok")
    public abstract Property<String> getJustificationString();

    @Input
    protected abstract Property<GroupNameVersion> getGroupNameVersion();

    @Internal
    protected abstract Property<ConfigManager> getConfigManager();

    @TaskAction
    public final void addVersionOverride() {
        ensurePresent(getCode(), CODE_OPTION);
        ensurePresent(getJustificationString(), JUSTIFICATION_OPTION);

        getConfigManager()
                .get()
                .modifyConfigFile(revapiConfig -> revapiConfig.addAcceptedBreaks(
                        getGroupNameVersion().get(),
                        Collections.singleton(AcceptedBreak.builder()
                                .code(getCode().get())
                                .oldElement(Optional.ofNullable(getOldElement().getOrNull()))
                                .newElement(Optional.ofNullable(getNewElement().getOrNull()))
                                .justification(Justification.fromString(
                                        getJustificationString().get()))
                                .build())));
    }

    private void ensurePresent(Property<?> prop, String option) {
        if (!prop.isPresent()) {
            throw new IllegalArgumentException("Please supply the --" + option + " param to this task");
        }
    }
}
