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

package org.kathra.pipelinemanager.controller;

import javassist.NotFoundException;
import org.apache.camel.cdi.ContextName;
import org.apache.http.HttpStatus;
import org.apache.log4j.Logger;
import org.kathra.core.model.Build;
import org.kathra.core.model.Membership;
import org.kathra.core.model.Pipeline;
import org.kathra.pipelinemanager.Config;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.pipelinemanager.service.JenkinsService;
import org.kathra.pipelinemanager.service.PipelineManagerService;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;

import javax.inject.Named;
import java.util.List;

/**
 * @author Quentin Sémanne <Quentin.Semanne@kathra.org>
 */
@Named("PipelineManagerController")
@ContextName("PipelineManager")
public class PipelineManagerController implements PipelineManagerService {


    private int maxAttempt = 5;
    private int attemptWaitMs = 250;

    private Config config;
    private JenkinsService jenkinsService;

    private static Logger logger = Logger.getLogger(PipelineManagerController.class);

    /**
     * Default, auto configuring PipelineManagerController, JenkinsServer, JenkinsService
     */
    public PipelineManagerController() {
        config = new Config();
    }

    /**
     * Constructor injecting JenkinsService
     *
     * @param jenkinsService
     */
    public PipelineManagerController(JenkinsService jenkinsService) {
        this.jenkinsService = jenkinsService;
    }

    private JenkinsService getJenkinsService() throws Exception {
        if (jenkinsService != null)  {
            return jenkinsService;
        }
        return new JenkinsService(config);
    }

    public void setMaxAttempt(int maxAttempt) {
        this.maxAttempt = maxAttempt;
    }

    /**
     * Manager generic exception to kathra exception
     *
     * @param e
     * @throws KathraException
     */
    private void manageException(Exception e) throws KathraException {
        logger.warn(e.getStackTrace());
        if (e instanceof IllegalArgumentException) {
            throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.BAD_REQUEST);
        } else if (e instanceof IllegalAccessException) {
            throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.UNAUTHORIZED);
        } else if (e instanceof IllegalStateException) {
            if (e.getMessage().matches(".*Pipeline.*already existing.*")) {
                throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.CONFLICT);
            } else {
                throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } else if (e instanceof NotFoundException) {
            throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.NOT_FOUND);
        } else if (e instanceof ApiException && ((ApiException) e).getCode() == HttpStatus.SC_FORBIDDEN) {
            throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.UNAUTHORIZED);
        } else if (e instanceof KathraException) {
            throw (KathraException) e;
        } else {
            throw new KathraException(e.getMessage()).errorCode(KathraException.ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Credential addCredential(Credential credential) throws Exception {
        return getJenkinsService().addCredential(credential);
    }

    @Override
    public Membership addMembership(Membership membership) throws Exception {
        try {
            return getJenkinsService().addMembership(membership);
        } catch (Exception e) {
            manageException(e);
            return null;
        }
    }

    @Override
    public Build createBuild(Build build) throws Exception {
        return getJenkinsService().createBuild(build);
    }

    @Override
    public Boolean createFolder(String path) throws Exception {
        return getJenkinsService().createFolder(path);
    }

    @Override
    public Pipeline createPipeline(Pipeline pipeline) throws Exception {
        int attempt = 0;
        Exception lastException;
        do {
            try {
                pipeline = getJenkinsService().createPipeline(pipeline);
                lastException = null;
                break;
            } catch (Exception e) {
                long wait = (long) (attemptWaitMs * Math.pow(2, attempt));
                logger.warn("Unable to create pipeline " + pipeline.getPath() + ", wait  " + wait + " ms and retry (" + attempt + "/" + maxAttempt + ")");
                lastException = e;
                Thread.sleep(wait);
                attempt++;
            }
        } while(attempt < maxAttempt);
        if (lastException != null) {
            lastException.printStackTrace();
            throw lastException;
        }
        return pipeline;
    }

    @Override
    public String deletePipeline(String path) throws Exception {
        int attempt = 0;
        Exception lastException;
        do {
            try {
                getJenkinsService().deletePipeline(new Pipeline().path(path));
                lastException = null;
                break;
            } catch (Exception e) {
                long wait = (long) (attemptWaitMs * Math.pow(2, attempt));
                logger.warn("Unable to create pipeline " + path + ", wait  " + wait + " ms and retry (" + attempt + "/" + maxAttempt + ")");
                lastException = e;
                Thread.sleep(wait);
                attempt++;
            }
        } while(attempt < maxAttempt);
        if (lastException != null) {
            lastException.printStackTrace();
            throw lastException;
        }
        return "OK";
    }

    @Override
    public Build getBuild(String path, String buildNumber) throws Exception {
        return getJenkinsService().getBuild(path, buildNumber);
    }

    @Override
    public List<Build> getBuilds(String path, String branch, Integer maxResult) throws Exception {
        return getJenkinsService().getBuilds(path, branch, maxResult);
    }

    @Override
    public List<Membership> getMemberships(String path) throws Exception {
        return getJenkinsService().getMemberships(path);
    }
}
