/*
 * Copyright (c) 2020. The Kathra Authors.
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
 *
 * Contributors:
 *    IRT SystemX (https://www.kathra.org/)
 *
 */
package org.kathra.pipelinemanager.service;

public enum JenkinsTemplate {
    JavaLibrary("JavaLibrary", "java"), JavaService("JavaService", "java"), PythonLibrary("PythonLibrary", "python"), PythonService("PythonService", "python"), DockerService("DockerService", "docker"), HelmChart("HelmChart", "helm");

    public final String name;
    public final String lang;

    JenkinsTemplate(String name, String lang) {
        this.name = name;
        this.lang = lang;
    }

}
