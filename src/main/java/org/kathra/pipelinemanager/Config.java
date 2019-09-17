/* 
 * Copyright 2019 The Kathra Authors.
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
 *
 *    IRT SystemX (https://www.kathra.org/)    
 *
 */

package org.kathra.pipelinemanager;

import org.kathra.utils.ConfigManager;
import org.kathra.utils.KathraException;

import java.io.IOException;

/**
 * @author Quentin Sémanne <Quentin.Semanne@kathra.org>
 */
public class Config extends ConfigManager {

    private String jenkinsAccountName;
    private String jenkinsAccountApiToken;
    private String jenkinsUrl;

    public Config() {
        jenkinsAccountName = getProperty("JENKINS_ACCOUNT_NAME","kathra-pipelinemanager");
        jenkinsAccountApiToken = getProperty("JENKINS_ACCOUNT_API_TOKEN");
        jenkinsUrl = getProperty("JENKINS_URL","https://jenkins.kathra.org");
    }

    public String getJenkinsAccountName() {
        return jenkinsAccountName;
    }

    public String getJenkinsAccountApiToken() {
        return jenkinsAccountApiToken;
    }

    public void setJenkinsAccountName(String jenkinsAccountName) {
        this.jenkinsAccountName = jenkinsAccountName;
    }

    public void setJenkinsAccountApiToken(String jenkinsAccountApiToken) {
        this.jenkinsAccountApiToken = jenkinsAccountApiToken;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }
}
