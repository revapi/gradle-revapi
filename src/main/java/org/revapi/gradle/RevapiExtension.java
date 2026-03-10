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
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOperations;
import org.revapi.gradle.config.GroupAndName;
import org.revapi.gradle.config.GroupNameVersion;
import org.revapi.gradle.config.Version;

@SuppressWarnings("DesignForExtension")
public class RevapiExtension {
    private final Property<String> oldGroup;
    private final Property<String> oldName;
    private final ListProperty<String> oldVersions;
    private final Provider<GroupAndName> oldGroupAndName;

    public RevapiExtension(Project project) {
        ExecOperations execOperations =
                project.getObjects().newInstance(RevapiPlugin.ExecOpsHolder.class).getExecOperations();

        this.oldGroup = project.getObjects().property(String.class);
        this.oldGroup.set(
                project.getProviders().provider(() -> project.getGroup().toString()));

        this.oldName = project.getObjects().property(String.class);
        this.oldName.set(project.getProviders().provider(project::getName));

        this.oldVersions = project.getObjects().listProperty(String.class);
        this.oldVersions.set(project.getProviders()
                .provider(() -> GitVersionUtils.previousGitTags(execOperations)
                        .limit(3)
                        .collect(Collectors.toList())));

        this.oldGroupAndName = project.provider(() ->
                GroupAndName.builder().group(oldGroup.get()).name(oldName.get()).build());
    }

    public Property<String> getOldGroup() {
        return oldGroup;
    }

    public Property<String> getOldName() {
        return oldName;
    }

    public ListProperty<String> getOldVersions() {
        return oldVersions;
    }

    public void setOldVersion(String oldVersionValue) {
        oldVersions.set(Collections.singletonList(oldVersionValue));
    }

    GroupNameVersion oldGroupNameVersion() {
        return oldGroupAndName()
                .get()
                .withVersion(Version.fromString(oldVersions.get().get(0)));
    }

    Provider<GroupAndName> oldGroupAndName() {
        return oldGroupAndName;
    }
}
