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
package org.kathra.pipelinemanager.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.HttpRequestWithBody;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import java.util.Map;

/**
 * Allows direct calls to the Jenkins REST API
 */
public class JenkinsRestService {

    private String defaultLogin;
    private String defaultPassword;

    public JenkinsRestService(String defaultLogin, String defaultPassword) {
        this.defaultLogin = defaultLogin;
        this.defaultPassword = defaultPassword;
    }


    /**
     * Post request with using default values
     * @param url Jenkins service url
     * @param jsonBody post request body
     * @param headers optionnals headers
     * @return
     * @throws Exception
     */
    public HttpResponse<String> postBasicAuth(String url, JSONObject jsonBody,  Map<String, String> headers) throws Exception {
        return postBasicAuth(url,jsonBody,headers,defaultLogin,defaultPassword);
    }

    /**
     * Post request with all parameters specified
     * @param url Jenkins service url
     * @param jsonBody post request body
     * @param headers optionnals headers
     * @param login Jenkins user login
     * @param password Jenkins user password
     * @return
     * @throws Exception
     */
    public HttpResponse<String> postBasicAuth(String url, JSONObject jsonBody, Map<String, String> headers, String login, String password) throws Exception {

        HttpRequestWithBody request = Unirest.post(url);

        String requestLogin = StringUtils.isEmpty(login) ? defaultLogin : login;
        String requestPassword = StringUtils.isEmpty(password) ? defaultPassword : password;

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.header(header.getKey(), header.getValue());
            }
        }

        request.field("json",jsonBody);

        request.basicAuth(requestLogin, requestPassword);

        return request.asString();

    }
}

