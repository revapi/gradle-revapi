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

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.revapi.gradle.config.Justification;

public abstract class RevapiReportTask extends DefaultTask {

    @Internal
    protected abstract Property<String> getAcceptAllBreaksTaskPath();

    @Internal
    protected abstract Property<String> getAcceptAllBreaksProjectTaskPath();

    @InputFile
    protected abstract RegularFileProperty getAnalysisResultsFile();

    @OutputFile
    protected abstract RegularFileProperty getJunitOutputFile();

    @TaskAction
    public final void reportBreaks() throws Exception {
        AnalysisResults results =
                AnalysisResults.fromFile(getAnalysisResultsFile().getAsFile().get());

        Configuration freeMarkerConfiguration = createFreeMarkerConfiguration();
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("results", results);
        templateData.put("acceptBreakTask", getAcceptAllBreaksTaskPath().get());
        templateData.put(
                "acceptAllBreaksProjectTask",
                getAcceptAllBreaksProjectTaskPath().get());
        templateData.put("acceptAllBreaksEverywhereTask", RevapiPlugin.ACCEPT_ALL_BREAKS_TASK_NAME);
        templateData.put("explainWhy", Justification.YOU_MUST_ENTER_JUSTIFICATION);

        Template junitTemplate = freeMarkerConfiguration.getTemplate("gradle-revapi-junit-template.ftl");
        junitTemplate.process(
                templateData,
                Files.newBufferedWriter(getJunitOutputFile().getAsFile().get().toPath(), StandardCharsets.UTF_8));

        Template textTemplate = freeMarkerConfiguration.getTemplate("gradle-revapi-text-template.ftl");
        StringWriter textOutputWriter = new StringWriter();
        textTemplate.process(templateData, textOutputWriter);

        String textOutput = textOutputWriter.toString();

        if (!textOutput.trim().isEmpty()) {
            throw new RuntimeException("There were Java public API/ABI breaks reported by revapi:\n\n" + textOutput);
        }
    }

    private Configuration createFreeMarkerConfiguration() {
        DefaultObjectWrapperBuilder objectWrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_23);
        Configuration freeMarker = new Configuration(Configuration.VERSION_2_3_23);

        freeMarker.setObjectWrapper(objectWrapper.build());
        freeMarker.setAPIBuiltinEnabled(true);
        freeMarker.setTemplateLoader(new ClassTemplateLoader(getClass(), "/META-INF"));

        return freeMarker;
    }
}
