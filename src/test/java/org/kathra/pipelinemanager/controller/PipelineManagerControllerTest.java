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
package org.kathra.pipelinemanager.controller;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.offbytwo.jenkins.JenkinsServer;
import org.kathra.core.model.*;

import org.kathra.pipelinemanager.model.Credential;
import org.kathra.pipelinemanager.service.JenkinsService;
import org.kathra.pipelinemanager.service.JenkinsTemplate;
import org.kathra.utils.KathraException;
import javassist.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;

/**
 * Test class {@linkplain JenkinsService}
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineManagerControllerTest {

    private PipelineManagerController underTest;

    @Mock
    private JenkinsService jenkinsService;

    private static String ARTIFACT_ID = "my-artifact";
    private static String GROUP = "my-group1/my-group2";
    private static String REPOSITORY = "git@git.kathra.org:KATHRA/example.git";
    private static JenkinsTemplate PIPELINE_TEMPLATE = JenkinsTemplate.JavaLibrary;


    @BeforeEach
    void setUpEach() throws IOException {
        jenkinsService =  Mockito.mock(JenkinsService.class);
        Mockito.reset(jenkinsService);
        JenkinsServer jenkinsServer = Mockito.mock(JenkinsServer.class);
        Mockito.when(jenkinsServer.isRunning()).thenReturn(true);
        Mockito.when(jenkinsService.getClient()).thenReturn(jenkinsServer);
        underTest = new PipelineManagerController(jenkinsService);
        underTest.setMaxAttempt(0);
    }

    @Test
    public void given_nominal_args_when_createPipeline_then_works() throws Exception {

        Pipeline pipeline = new Pipeline();
        pipeline.setId("New Pipeline");
        Mockito.when(jenkinsService.createPipeline(Mockito.any())).thenReturn(pipeline);
        Pipeline result = underTest.createPipeline(pipeline);
        Assertions.assertEquals(pipeline, result);
    }

    @Test
    public void given_existing_pipeline_when_createPipeline_then_throws_kathra_exception_conflict() throws Exception {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
        {
            IllegalStateException exceptionAlreadyExist = new IllegalStateException("Pipeline with name " + ARTIFACT_ID + " is already existing");
            Mockito.when(jenkinsService.createPipeline(Mockito.any())).thenThrow(exceptionAlreadyExist);
            underTest.createPipeline(new Pipeline().path(GROUP));
        });
        Assertions.assertEquals(IllegalStateException.class, exception.getClass());
    }

    @Test
    public void given_missing_args_when_createPipeline_then_throws_kathra_exception_bad_request() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        {
            IllegalArgumentException argException = new IllegalArgumentException("Arg error");
            Mockito.when(jenkinsService.createPipeline(Mockito.any())).thenThrow(argException);
            underTest.createPipeline(new Pipeline().path(GROUP));
        });
        Assertions.assertEquals(IllegalArgumentException.class, exception.getClass());
    }

    @Test
    public void given_generic_when_createPipeline_then_throws_kathra_exception_intern_error() throws Exception {
        Exception exception = assertThrows(Exception.class, () ->
        {
            Exception genericException = new Exception("Arg error");
            Mockito.when(jenkinsService.createPipeline(Mockito.any())).thenThrow(genericException);
            underTest.createPipeline(new Pipeline().path(GROUP));
        });
        Assertions.assertEquals(Exception.class, exception.getClass());
    }


    @Test
    public void given_nominal_args_when_createBuild_then_works() throws Exception {
        Build build = new Build().path(GROUP);

        Mockito.when(jenkinsService.createBuild(build)).thenReturn(build.status(Build.StatusEnum.SUCCESS));

        BuildArgument argumentBuild = new BuildArgument();
        argumentBuild.setKey("x");
        argumentBuild.setValue("1");

        Build result = underTest.createBuild(build);

        Assertions.assertEquals(result.getStatus(), Build.StatusEnum.SUCCESS);
    }

    @Test
    public void given_empty_build_when_createBuild_then_throws_kathra_exception_bad_request() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        {
            Mockito.when(jenkinsService.createBuild(null)).thenThrow(new IllegalArgumentException("Arg error"));
            underTest.createBuild(null);
        });
        Assertions.assertEquals(IllegalArgumentException.class, exception.getClass());
    }

    @Test
    public void given_no_existing_pipeline_when_createBuild_then_throws_kathra_exception_not_found() throws Exception {
        Build build = new Build().path(GROUP);

        NotFoundException exception = assertThrows(NotFoundException.class, () ->
        {
            Mockito.when(jenkinsService.createBuild(build)).thenThrow(new NotFoundException("Pipeline not found"));
            underTest.createBuild(build);
        });
        Assertions.assertEquals(NotFoundException.class, exception.getClass());
    }

    @Test
    public void given_generic_exception_when_createBuild_then_throws_kathra_exception_intern_error() throws Exception {
        Build build = new Build().path(GROUP);

        Exception exception = assertThrows(Exception.class, () ->
        {
            Mockito.when(jenkinsService.createBuild(build)).thenThrow(new Exception("Intern error"));
            underTest.createBuild(build);
        });
        Assertions.assertEquals(Exception.class, exception.getClass());
    }

    @Test
    public void given_unauthorized_exception_when_createBuild_then_throws_kathra_exception_401() throws Exception {
        Build build = new Build().path(GROUP);

        IllegalAccessException exception = assertThrows(IllegalAccessException.class, () ->
        {
            Mockito.when(jenkinsService.createBuild(build)).thenThrow(new IllegalAccessException("Intern error"));
            underTest.createBuild(build);
        });
        Assertions.assertEquals(IllegalAccessException.class, exception.getClass());
    }


    @Test
    public void given_nominal_args_when_getBuild_then_works() throws Exception {
        Build build = new Build().path(GROUP).status(Build.StatusEnum.SUCCESS);

        Mockito.when(jenkinsService.getBuild(GROUP,"1")).thenReturn(build);
        Build result = underTest.getBuild(GROUP,"1");

        Assertions.assertEquals(build.getStatus(), Build.StatusEnum.SUCCESS);
    }

    @Test
    public void given_nominal_args_when_getBuild_then_throws_kathra_exception_not_found() throws Exception {
        NotFoundException exception = assertThrows(NotFoundException.class, () ->
        {
            Mockito.when(jenkinsService.getBuild(GROUP,"1")).thenThrow(new NotFoundException("Pipeline not found"));
            underTest.getBuild(GROUP,"1");
        });
        Assertions.assertEquals(NotFoundException.class, exception.getClass());
    }

    @Test
    public void given_empty_pipelineId_when_getBuild_then_throws_kathra_exception_bad_request() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        {
            Mockito.when(jenkinsService.getBuild(null,"1")).thenThrow(new IllegalArgumentException("Arg error"));
            underTest.getBuild(null,"1");
        });
        Assertions.assertEquals(IllegalArgumentException.class, exception.getClass());
    }

    @Test
    public void given_nominal_args_when_getBuilds_then_works() throws Exception {

        Build build = new Build().path(GROUP).status(Build.StatusEnum.SUCCESS);

        Mockito.when(jenkinsService.getBuilds(GROUP,"dev",5)).thenReturn(new LinkedList<>(Arrays.asList(build)));

        List<Build> result = underTest.getBuilds(GROUP,"dev",5);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(build, result.get(0));

    }

    @Test
    public void given_generic_exception_when_getBuild_then_throws_kathra_exception_401() throws Exception {
        IllegalAccessException exception = assertThrows(IllegalAccessException.class, () ->
        {
            Mockito.when(jenkinsService.getBuild(GROUP,"1")).thenThrow(new IllegalAccessException("generic error"));
            underTest.getBuild(GROUP,"1");
        });
        Assertions.assertEquals(IllegalAccessException.class, exception.getClass());
    }

    @Test
    public void given_generic_exception_when_getBuild_then_throws_kathra_exception_intern_error() throws Exception {
        Exception exception = assertThrows(Exception.class, () ->
        {
            Mockito.when(jenkinsService.getBuild(GROUP,"1")).thenThrow(new Exception("generic error"));
            underTest.getBuild(GROUP,"1");
        });
        Assertions.assertEquals(Exception.class, exception.getClass());
    }

    @Test
    public void given_valid_membership_when_addMembership_then_works() throws Exception{
        Membership membership = new Membership().role(Membership.RoleEnum.GUEST).path(GROUP).memberType(Membership.MemberTypeEnum.GROUP).memberName("user");
        Mockito.when(jenkinsService.addMembership(membership)).thenReturn(membership);
        Membership result = underTest.addMembership(membership);

        assertEquals(membership,result);
    }

    @Test
    public void given_valid_path_when_getMembership_then_works() throws Exception{
        Membership membership = new Membership().role(Membership.RoleEnum.GUEST).path(GROUP).memberType(Membership.MemberTypeEnum.GROUP).memberName("user");
        Mockito.when(jenkinsService.getMemberships(GROUP)).thenReturn(new LinkedList<>(Arrays.asList(membership)));
        List<Membership> result = underTest.getMemberships(GROUP);

        assertEquals(1,result.size());
        assertEquals(membership, result.get(0));
    }

    @Test
    public void given_valid_path_when_addCredentials_then_works() throws Exception{
        Credential credentials = new Credential().path(GROUP).credentialId("toto").privateKey("key").username("git");
        Mockito.when(jenkinsService.addCredential(credentials)).thenReturn(credentials);
        Credential result = underTest.addCredential(credentials);

        assertEquals(credentials,result);

    }

    @Test
    public void given_valid_folder_name_when_createFolder_then_works() throws Exception{
            Mockito.when(jenkinsService.createFolder("folderName")).thenReturn(true);
            underTest.createFolder("folderName");

    }
}
