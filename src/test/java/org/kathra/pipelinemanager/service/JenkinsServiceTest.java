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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mashape.unirest.http.HttpResponse;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.*;
import org.kathra.core.model.Build;
import org.kathra.core.model.BuildArgument;
import org.kathra.core.model.Membership;
import org.kathra.core.model.Pipeline;
import org.kathra.core.model.SourceRepository;
import org.kathra.pipelinemanager.Config;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.utils.Session;
import javassist.NotFoundException;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.hamcrest.MockitoHamcrest;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test class {@linkplain org.kathra.pipelinemanager.service.JenkinsService}
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JenkinsServiceTest {

    private JenkinsService underTest;

    @Mock
    private JenkinsServer client;

    @Mock
    private JenkinsRestService jenkinsRestService;

    @Mock
    private Config config;

    private ArgumentCaptor<String> foldersNames = ArgumentCaptor.forClass(String.class);
    private ArgumentCaptor<FolderJob> folderJobs = ArgumentCaptor.forClass(FolderJob.class);
    private ArgumentCaptor<FolderJob> jobFolder = ArgumentCaptor.forClass(FolderJob.class);
    private ArgumentCaptor<String> jobName = ArgumentCaptor.forClass(String.class);
    private ArgumentCaptor<String> jobNameXml = ArgumentCaptor.forClass(String.class);
    private ArgumentCaptor<HashMap<String, String>> buildArgsCaptor = ArgumentCaptor.forClass(HashMap.class);
    private static String GROUP = "my-group1/my-group2";
    private static String ARTIFACT_ID = "my-artifact";
    private static String ARTIFACT_ID_PATH = GROUP+"/"+ARTIFACT_ID;


    private static String BASE_REPOSITORY = "git@git.kathra.org:My-group1/my-group2/my-artifact";
    private static String REPOSITORY = BASE_REPOSITORY+".git";

    private static String genericJenkinsXml="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<com.cloudbees.hudson.plugins.folder.Folder plugin=\"cloudbees-folder@6.6\">\n" +
            "   <displayName>KATHRA</displayName>\n" +
            "   <properties>\n" +
            "      <com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>\n" +
            "         <inheritanceStrategy class=\"org.jenkinsci.plugins.matrixauth.inheritance.InheritGlobalStrategy\" />\n" +
            "         <permission>hudson.model.Item.Build:contributorMember</permission>\n" +
            "         <permission>hudson.model.Item.Build:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Cancel:contributorMember</permission>\n" +
            "         <permission>hudson.model.Item.Cancel:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Configure:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Create:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Delete:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Discover:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Move:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Read:contributorMember</permission>\n" +
            "         <permission>hudson.model.Item.Read:guestMember</permission>\n" +
            "         <permission>hudson.model.Item.Read:managerMember</permission>\n" +
            "         <permission>hudson.model.Item.Workspace:managerMember</permission>\n" +
            "         <permission>hudson.model.Run.Delete:managerMember</permission>\n" +
            "         <permission>hudson.model.Run.Replay:managerMember</permission>\n" +
            "         <permission>hudson.model.Run.Update:managerMember</permission>\n" +
            "         <permission>hudson.model.View.Configure:managerMember</permission>\n" +
            "         <permission>hudson.model.View.Create:managerMember</permission>\n" +
            "         <permission>hudson.model.View.Delete:managerMember</permission>\n" +
            "         <permission>hudson.model.View.Read:contributorMember</permission>\n" +
            "         <permission>hudson.model.View.Read:guestMember</permission>\n" +
            "         <permission>hudson.model.View.Read:managerMember</permission>\n" +
            "      </com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty>\n" +
            "   </properties>\n" +
            "</com.cloudbees.hudson.plugins.folder.Folder>";

    @BeforeEach
    void setUpEach() throws Exception {
        underTest = new JenkinsService(client, jenkinsRestService, config);
        Mockito.reset(client);
    }


    /**
     * Check job creation from empty jenkins
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void given_nominal_args_with_no_existing_folder_when_createPipeline_then_works() throws Exception {

        // Mock create folder
        mockFolderCreation();
        // Mock create job
        mockJobCreation();



        Pipeline pipeline =  underTest.createPipeline(new Pipeline().path(ARTIFACT_ID_PATH).template(Pipeline.TemplateEnum.JAVA_LIBRARY).sourceRepository(new SourceRepository().sshUrl(REPOSITORY)).credentialId("pullKeyId"));
        Assertions.assertNotNull(pipeline);
        Assertions.assertNotNull(pipeline.getStatus());
        Assertions.assertNotNull(pipeline.getProvider());
        Assertions.assertNotNull(pipeline.getProviderId());

        Assertions.assertEquals("MY-GROUP1/my-group2/my-artifact", pipeline.getProviderId());

        // Verify folder creation
        verifyFolderCreation("MY-GROUP1", null);
        verifyFolderCreation("my-group2", "MY-GROUP1");

        // Verify job creation
        verifyJobCreation(ARTIFACT_ID, "my-group2");

        // Test get job from client
        FolderJob jobParent = jobFolder.getValue();

        Assertions.assertEquals(ARTIFACT_ID, jobName.getValue());
        Assertions.assertEquals("my-group2", jobParent.getName());
        Assertions.assertIterableEquals(ImmutableList.of("MY-GROUP1","my-group2"), foldersNames.getAllValues());

        Job jobCreated = client.getJob(jobParent, ARTIFACT_ID);

        Assertions.assertEquals(ARTIFACT_ID, jobCreated.getName());

    }

    /**
     * Check job creation from existing folders
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void given_nominal_args_with_existing_folder_when_createPipeline_then_works() throws Exception {

        // Mock create folder
        mockFolderCreation();
        // Mock create job
        mockJobCreation();

        FolderJob myGroup1 = mockFolderExisting("MY-GROUP1", null);
        FolderJob myGroup2 = mockFolderExisting("my-group2", myGroup1);
        mockFolderExisting("my", myGroup2);


        Pipeline pipeline =  underTest.createPipeline(new Pipeline().path(ARTIFACT_ID_PATH).template(Pipeline.TemplateEnum.JAVA_LIBRARY).sourceRepository(new SourceRepository().sshUrl(REPOSITORY)).credentialId("pullKeyId"));
        Assertions.assertNotNull(pipeline);
        Assertions.assertEquals("MY-GROUP1/my-group2/my-artifact", pipeline.getProviderId());

        // Verify folder creation
        verifyFolderCreation("MY-GROUP1", null, 0 );
        verifyFolderCreation("my-group2", "MY-GROUP1", 0);

        // Verify job creation
        verifyJobCreation(ARTIFACT_ID, "my-group2");
    }

    /**
     * When create job existing when createPipeline throws IllegalStateException
     */
    @Test
    public void given_nominal_args_with_existing_job_when_createPipeline_then_throws_IllegalStateException() {
        assertThrows(IllegalStateException.class,()->
        {
            // Mock create folder
            mockFolderCreation();
            // Mock create job
            mockJobCreation();

            FolderJob group1Folder = mockFolderExisting("MY-GROUP1", null);
            FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);

            mockJob(ARTIFACT_ID, group2Folder);

            underTest.createPipeline(new Pipeline().path(GROUP+"/"+ARTIFACT_ID).template(Pipeline.TemplateEnum.JAVA_LIBRARY).sourceRepository(new SourceRepository().sshUrl(REPOSITORY)));

            // Verify folder creation
            verifyFolderCreation("MY-GROUP1", null, 0 );
            verifyFolderCreation("my-group2", "MY-GROUP1", 0);

            // Verify job creation
            verifyJobCreation(ARTIFACT_ID, "my-group2", 0);
        });
    }

    /**
     * When empty arg should throw IllegalArgumentException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void given_empty_args_when_createPipeline_then_throws_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createPipeline(null);
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createPipeline(new Pipeline().path(null).template(Pipeline.TemplateEnum.JAVA_LIBRARY).sourceRepository(new SourceRepository().sshUrl(REPOSITORY)));
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createPipeline(new Pipeline().path(GROUP+"/"+ARTIFACT_ID).template(null).sourceRepository(new SourceRepository().sshUrl(REPOSITORY)));
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createPipeline(new Pipeline().path(GROUP+"/"+ARTIFACT_ID).template(Pipeline.TemplateEnum.JAVA_LIBRARY).sourceRepository(null));
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createPipeline(new Pipeline().path(GROUP+"/"+ARTIFACT_ID).template(Pipeline.TemplateEnum.JAVA_LIBRARY).sourceRepository(new SourceRepository().sshUrl(null)));
        });
    }

    /**
     * When existing pipeline when build then works
     * @throws Exception
     */
    @Test
    public void given_existing_pipeline_when_build_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        mockJobBuild(job, Long.valueOf(5));

        Build build = underTest.createBuild(new Build().path(ARTIFACT_ID_PATH));

        Mockito.verify(job).build();
        Assertions.assertEquals("5", build.getBuildNumber(), "Build number is not the same");
    }

    /**
     * When existing pipeline when build then works
     * @throws Exception
     */
    @Test
    public void given_existing_pipeline_when_build_then_works_without_detail() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        mockJobBuild(job, Long.valueOf(5));

        Mockito.when(job.details().getLastBuild().details()).thenReturn(null);
        Build build = underTest.createBuild(new Build().path(ARTIFACT_ID_PATH));

        Mockito.verify(job).build();
        Assertions.assertEquals("5", build.getBuildNumber(), "Build number is not the same");
        Assertions.assertEquals("", build.getLogs());
        Assertions.assertEquals(Build.StatusEnum.PROCESSING, build.getStatus());

    }

    /**
     * When existing pipeline with build args when build then works
     * @throws Exception
     */
    @Test
    public void given_existing_pipeline_and_build_args_when_build_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        mockJobBuild(job, Long.valueOf(5) );
        Map<String,String> buildArgs = ImmutableMap.of("x", "1", "y", "2");

        org.kathra.core.model.Build build = underTest.createBuild(new Build().path(ARTIFACT_ID_PATH)
                .addBuildArgumentsItem(new BuildArgument().key("x").value("1"))
                .addBuildArgumentsItem(new BuildArgument().key("y").value("2")));

        Mockito.verify(job).build(buildArgs);
        Assertions.assertEquals("5", build.getBuildNumber(), "Build number is not the same");
    }

    /**
     * When no existing pipeline when build then throws NotFoundException
     * @throws Exception
     */
    @Test
    public void given_no_existing_pipeline_when_build_then_throws_NotFoundException() {
        assertThrows(NotFoundException.class,()-> {
            underTest.createBuild(new Build().path("unexists"));
        });
    }

    /**
     * When empty pipeline id when build then throws IllegalArgumentException
     * @throws Exception
     */
    @Test
    public void given_empty_arg_pipelineId_when_build_then_throws_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createBuild(null);
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createBuild(new Build().path(null));
        });
    }

    /**
     * When no existing pipeline when getLastBuildStatus then throws NotFoundException
     * @throws Exception
     */
    @Test
    public void given_no_existing_pipeline_when_getStatus_then_throws_NotFoundException() {
        assertThrows(NotFoundException.class, () -> {
            underTest.getBuild("no-exist", null);
        });
    }

    /**
     * When empty pipeline id when build then throws IllegalArgumentException
     * @throws Exception
     */
    @Test
    public void given_empty_arg_pipelineId_when_getStatus_then_throws_NotFoundException() {
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.getBuild(null,null);
        });
    }

    @Test
    public void given_pipeline_built_when_getBuildStatus_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder);
        Mockito.when(client.getJob(GROUP + "/" + ARTIFACT_ID)).thenReturn(job);

        mockJobBuild(job, Long.valueOf(3) );
        mockJobBuildResult(job, Long.valueOf(3), BuildResult.SUCCESS);
        mockJobBuild(job, Long.valueOf(4) );
        mockJobBuildResult(job, Long.valueOf(4), BuildResult.FAILURE);

        Build build = underTest.getBuild(GROUP + "/" + ARTIFACT_ID, "3");

        Assertions.assertEquals("3", build.getBuildNumber());
        Assertions.assertEquals(Build.StatusEnum.SUCCESS, build.getStatus());
        Assertions.assertEquals("", build.getLogs());
    }

    @Test
    public void given_pipeline_built_when_getBuilds_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder);
        Mockito.when(client.getJob(GROUP + "/" + ARTIFACT_ID)).thenReturn(job);

        mockJobBuild(job, Long.valueOf(4) );
        com.offbytwo.jenkins.model.Build build3 = mockJobBuildResult(job, Long.valueOf(4), BuildResult.SUCCESS);
        mockJobBuild(job, Long.valueOf(3) );
        com.offbytwo.jenkins.model.Build build4 = mockJobBuildResult(job, Long.valueOf(3), BuildResult.FAILURE);

        List<com.offbytwo.jenkins.model.Build> buildList = new LinkedList<>();
        buildList.add(build3);
        buildList.add(build4);
        mockJobBuildResults(job,buildList);

        List<Build> listBuild = underTest.getBuilds(GROUP + "/" + ARTIFACT_ID, null, null);
        Assertions.assertEquals(2, listBuild.size());

        Assertions.assertEquals("4", listBuild.get(0).getBuildNumber());
        Assertions.assertEquals("3", listBuild.get(1).getBuildNumber());

    }

    @Test
    public void given_pipeline_built_with_branch_parameter_when_getBuilds_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder);
        Mockito.when(client.getJob(GROUP + "/" + ARTIFACT_ID)).thenReturn(job);

        Map<String, String> params3 = new HashMap<>();
        params3.put("GIT_BRANCH","dev");

        mockJobBuild(job, Long.valueOf(3));
        com.offbytwo.jenkins.model.Build build3 = mockJobBuildResult(job, Long.valueOf(3), BuildResult.SUCCESS,params3);
        mockJobBuild(job, Long.valueOf(4));
        com.offbytwo.jenkins.model.Build build4 = mockJobBuildResult(job, Long.valueOf(4), BuildResult.FAILURE);

        List<com.offbytwo.jenkins.model.Build> buildList = new LinkedList<>();
        buildList.add(build3);
        buildList.add(build4);
        mockJobBuildResults(job,buildList);

        List<Build> listBuild = underTest.getBuilds(GROUP + "/" + ARTIFACT_ID, "dev", null);
        Assertions.assertEquals(1, listBuild.size());

        Assertions.assertEquals("3", listBuild.get(0).getBuildNumber());
    }

    @Test
    public void given_pipeline_built_with_maxresult_parameter_when_getBuilds_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder);
        Mockito.when(client.getJob(GROUP + "/" + ARTIFACT_ID)).thenReturn(job);

        Map<String, String> params3 = new HashMap<>();
        params3.put("GIT_BRANCH","dev");

        mockJobBuild(job, Long.valueOf(4));
        com.offbytwo.jenkins.model.Build build3 = mockJobBuildResult(job, Long.valueOf(4), BuildResult.SUCCESS,params3);
        mockJobBuild(job, Long.valueOf(3));
        com.offbytwo.jenkins.model.Build build4 = mockJobBuildResult(job, Long.valueOf(3), BuildResult.FAILURE);

        List<com.offbytwo.jenkins.model.Build> buildList = new LinkedList<>();
        buildList.add(build3);
        buildList.add(build4);
        mockJobBuildResults(job,buildList);

        List<Build> listBuild = underTest.getBuilds(GROUP + "/" + ARTIFACT_ID, null, 1);
        Assertions.assertEquals(1, listBuild.size());

        Assertions.assertEquals("4", listBuild.get(0).getBuildNumber());
    }

    @Test
    public void given_pipeline_built_with_maxresult_and_branch_parameter_when_getBuilds_then_works() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder);
        Mockito.when(client.getJob(GROUP + "/" + ARTIFACT_ID)).thenReturn(job);

        Map<String, String> params3 = new HashMap<>();
        params3.put("GIT_BRANCH","dev");

        mockJobBuild(job, Long.valueOf(4));
        com.offbytwo.jenkins.model.Build build3 = mockJobBuildResult(job, Long.valueOf(4), BuildResult.SUCCESS,params3);
        mockJobBuild(job, Long.valueOf(3));
        com.offbytwo.jenkins.model.Build build4 = mockJobBuildResult(job, Long.valueOf(3), BuildResult.FAILURE, params3);

        List<com.offbytwo.jenkins.model.Build> buildList = new LinkedList<>();
        buildList.add(build3);
        buildList.add(build4);
        mockJobBuildResults(job,buildList);

        List<Build> listBuild = underTest.getBuilds(GROUP + "/" + ARTIFACT_ID, "dev", 1);
        Assertions.assertEquals(1, listBuild.size());

        Assertions.assertEquals("4", listBuild.get(0).getBuildNumber());
    }

    @Test
    public void given_pipeline_built_withouth_path_parameter_when_getBuilds_then_throws_Exception() throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder);

        mockJobBuild(job, Long.valueOf(3));
        com.offbytwo.jenkins.model.Build build3 = mockJobBuildResult(job, Long.valueOf(3), BuildResult.SUCCESS);
        mockJobBuild(job, Long.valueOf(4));
        com.offbytwo.jenkins.model.Build build4 = mockJobBuildResult(job, Long.valueOf(4), BuildResult.FAILURE);

        List<com.offbytwo.jenkins.model.Build> buildList = new LinkedList<>();
        buildList.add(build3);
        buildList.add(build4);
        mockJobBuildResults(job,buildList);

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.getBuilds(null, "dev", 1);
        });
    }

    @Test
    public void given_folder_no_parent_when_create_then_works () throws Exception {
        mockFolderCreation();
        underTest.createFolder("A");

        // Verify folder creation
        verifyFolderCreation("A", null);
    }

    @Test
    public void given_folder_new_parent_when_create_then_works () throws Exception {
        mockFolderCreation();

        underTest.createFolder("B/C");
        verifyFolderCreation("B", null);
        verifyFolderCreation("C", "B");
    }

    @Test
    public void given_folder_existing_parent_when_create_then_works () throws Exception {
        mockFolderCreation();

        mockFolderExisting("B", null);

        underTest.createFolder("B/C");
        verifyFolderCreation("C", "B");
    }

    @Test
    public void given_folder_with_no_path_when_create_then_thows_Exception () throws Exception {
        mockFolderCreation();

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createFolder("");
        });

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.createFolder(null);
        });

    }

    @Test
    public void given_path_when_get_memberships_then_works () throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        String relativeUrl="my-group1/job/my-group2/job/my-artifact";
        String jenkinsUrl = "http://jenkins/job/"+relativeUrl;

        Mockito.when(job.getUrl()).thenReturn(jenkinsUrl);

        mockJobConfigurationExisting(relativeUrl,genericJenkinsXml);
        List<Membership> result = underTest.getMemberships(ARTIFACT_ID_PATH);

        Assertions.assertEquals(3,result.size());

        for (Membership membership : result){
            Assertions.assertEquals(Membership.MemberTypeEnum.GROUP,membership.getMemberType());
            Assertions.assertEquals(ARTIFACT_ID_PATH,membership.getPath());
            if (membership.getMemberName().equals("managerMember"))
                Assertions.assertEquals(Membership.RoleEnum.MANAGER,membership.getRole());
            else if (membership.getMemberName().equals("contributorMember"))
                Assertions.assertEquals(Membership.RoleEnum.CONTRIBUTOR,membership.getRole());
            else if (membership.getMemberName().equals("guestMember"))
                Assertions.assertEquals(Membership.RoleEnum.GUEST,membership.getRole());
        }
    }

    @Test
    public void given_not_existing_path_when_get_memberships_then_works () throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        String relativeUrl="my-group1/job/my-group2/job/my-artifact";
        String jenkinsUrl = "http://jenkins/job/"+relativeUrl;

        Mockito.when(job.getUrl()).thenReturn(jenkinsUrl);

        mockJobConfigurationExisting(relativeUrl,genericJenkinsXml);
        List<Membership> result = underTest.getMemberships("FakePath");

        Assertions.assertEquals(0,result.size());
    }

    @Test
    public void given_null_path_when_get_memberships_then_throws_Exeception () throws Exception {
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.getMemberships("");
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.getMemberships(null);
        });
    }

    @Test
    public void given_membership_when_add_memberships_then_works () throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        JobWithDetails jobGroup2 = mockJob("my-group2", group1Folder,"my-group1");
        JobWithDetails jobGroup1 = mockJob("my-group1", null,null);
        String relativeUrl="my-group1/job/my-group2/job/my-artifact";
        String jenkinsUrl = "http://jenkins/job/"+relativeUrl;

        Mockito.when(job.getUrl()).thenReturn(jenkinsUrl);
        Mockito.when(jobGroup1.getUrl()).thenReturn("http://jenkins/job/my-group1");
        Mockito.when(jobGroup2.getUrl()).thenReturn("http://jenkins/job/my-group1/job/my-group2");

        mockJobConfigurationExisting(relativeUrl,genericJenkinsXml);
        mockJobConfigurationExisting("my-group1/job/my-group2",genericJenkinsXml);
        mockJobConfigurationExisting("my-group1",genericJenkinsXml);

        underTest.addMembership(new Membership().memberName("newguest").memberType(Membership.MemberTypeEnum.GROUP).path(ARTIFACT_ID_PATH).role(Membership.RoleEnum.GUEST));
        verifyJobConfigurationUpdate(relativeUrl,"<permission>hudson.model.Item.Read:newguest</permission>");
        verifyJobConfigurationUpdate(relativeUrl,"<permission>hudson.model.View.Read:newguest</permission>");

    }

    @Test
    public void given_membership_null_when_add_memberships_then_throws_exception () throws Exception {
        FolderJob group1Folder = mockFolderExisting("my-group1", null);
        FolderJob group2Folder = mockFolderExisting("my-group2", group1Folder);
        JobWithDetails job = mockJob(ARTIFACT_ID, group2Folder,GROUP);
        String relativeUrl="my-group1/job/my-group2/job/my-artifact";
        String jenkinsUrl = "http://jenkins/job/"+relativeUrl;

        Mockito.when(job.getUrl()).thenReturn(jenkinsUrl);

        mockJobConfigurationExisting(relativeUrl,genericJenkinsXml);
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.addMembership(null);
        });

    }

    @Test
    public void given_credential_when_add_credential_then_works () throws Exception {
        Credential credential = new Credential().credentialId("toto").path("youhou").privateKey("key").username("git");
        mockCredentialCreation(200);
        Credential credentialResult = underTest.addCredential(credential);
        Assertions.assertEquals(credentialResult, credential);
    }

    @Test
    public void given_credential_when_add_credential_then_throws_exception () throws Exception {
        Credential credential = new Credential().credentialId("toto").path("youhou").privateKey("key").username("git");
        mockCredentialCreation(400);
        assertThrows(Exception.class,()-> {
            underTest.addCredential(credential);
        });
    }

    @Test
    public void given_non_valid_credential_when_add_credential_then_throws_exception () throws Exception {

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.addCredential(null);
        });
        assertThrows(IllegalArgumentException.class,()-> {
            underTest.addCredential(new Credential().path("youhou").privateKey("key").username("git"));
        });

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.addCredential(new Credential().credentialId("toto").privateKey("key").username("git"));
        });

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.addCredential(new Credential().credentialId("toto").path("youhou").username("git"));
        });

        assertThrows(IllegalArgumentException.class,()-> {
            underTest.addCredential(new Credential().credentialId("toto").path("youhou").privateKey("key"));
        });

    }

    /**
     * Mock job creation
     * @throws IOException
     */
    private void mockJobCreation() throws IOException {

        jobFolder = ArgumentCaptor.forClass(FolderJob.class);
        jobName = ArgumentCaptor.forClass(String.class);
        jobNameXml = ArgumentCaptor.forClass(String.class);

        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws IOException {
                FolderJob folderJobParent = invocation.getArgument(0);
                String jobName = invocation.getArgument(1);
                mockJob(jobName, folderJobParent);
                return null;
            }
        }).when(client).createJob(jobFolder.capture(), jobName.capture(), jobNameXml.capture());
    }

    /**
     * Verify job creation
     * @param jobName
     * @param folderName
     * @throws IOException
     */
    private void verifyJobCreation(String jobName, String folderName) throws IOException {
        verifyJobCreation(jobName, folderName, 1);
    }

    /**
     * Mock job building
     * @param job
     * @param buildNumber
     * @return
     * @throws IOException
     */
    private QueueItem mockJobBuild(JobWithDetails job, Long buildNumber) throws IOException {
        return mockJobBuild(job, buildNumber, true);
    }

    /**
     * Mock job building
     * @param job
     * @param buildNumber
     * @return
     * @throws IOException
     */
    private QueueItem mockJobBuild(JobWithDetails job, Long buildNumber, boolean withDetails) throws IOException {
        QueueReference queue = Mockito.mock(QueueReference.class);
        QueueItem queueItem = Mockito.mock(QueueItem.class);


        mockJobBuildResult(job, buildNumber-1, BuildResult.SUCCESS, withDetails);


        Mockito.when(queueItem.getId()).thenReturn(buildNumber);

        Answer<QueueReference> answer = invocation -> {
            mockJobBuildResult(job, buildNumber, BuildResult.BUILDING, withDetails);
            return queue;
        };

        Mockito.doAnswer(answer).when(job).build();
        Mockito.doAnswer(answer).when(job).build(buildArgsCaptor.capture());

        Mockito.when(client.getQueueItem(queue)).thenReturn(queueItem);

        return queueItem;
    }

    private com.offbytwo.jenkins.model.Build mockJobBuildResult(Job job, Long buildNumber, BuildResult buildResult, Map<String, String> buildParams) throws IOException {
        return mockJobBuildResult(job,buildNumber,buildResult,buildParams,true);
    }

    private com.offbytwo.jenkins.model.Build mockJobBuildResult(Job job, Long buildNumber, BuildResult buildResult, Map<String, String> buildParams, boolean withDetails) throws IOException {
        JobWithDetails details = job.details() == null ? Mockito.mock(JobWithDetails.class) : job.details();
        if (job.details() == null) {
            Mockito.when(job.details()).thenReturn(details);
        }

        com.offbytwo.jenkins.model.Build build = Mockito.mock(com.offbytwo.jenkins.model.Build.class);
        Mockito.when(build.getNumber()).thenReturn(buildNumber.intValue());
        Mockito.when(build.getQueueId()).thenReturn(buildNumber.intValue());

        if (withDetails) {
            BuildWithDetails buildDetails = Mockito.mock(BuildWithDetails.class);
            Mockito.when(build.details()).thenReturn(buildDetails);
            Mockito.when(buildDetails.getNumber()).thenReturn(buildNumber.intValue());
            Mockito.when(buildDetails.getQueueId()).thenReturn(buildNumber.intValue());
            Mockito.when(buildDetails.getId()).thenReturn(buildNumber.toString());
            Mockito.when(buildDetails.getConsoleOutputText()).thenReturn("build log text");
            Mockito.when(buildDetails.getConsoleOutputHtml()).thenReturn("build log html");
            Mockito.when(buildDetails.getResult()).thenReturn(buildResult);
            Mockito.when(buildDetails.getParameters()).thenReturn(buildParams);
        }
        Mockito.when(details.getLastBuild()).thenReturn(build);
        Mockito.when(details.getBuildByNumber(buildNumber.intValue())).thenReturn(build);
        return build;
    }

    /**
     * Mock build status
     * @param job
     * @param buildNumber
     * @param buildResult
     * @return
     * @throws IOException
     */
    private com.offbytwo.jenkins.model.Build mockJobBuildResult(Job job, Long buildNumber, BuildResult buildResult) throws IOException {
        return mockJobBuildResult(job,buildNumber,buildResult,null ,true);
    }

    /**
     * Mock build status
     * @param job
     * @param buildNumber
     * @param buildResult
     * @return
     * @throws IOException
     */
    private com.offbytwo.jenkins.model.Build mockJobBuildResult(Job job, Long buildNumber, BuildResult buildResult,boolean withDetails) throws IOException {
        return mockJobBuildResult(job,buildNumber,buildResult,null ,withDetails);
    }

    private void mockJobBuildResults(Job job, List<com.offbytwo.jenkins.model.Build> allBuilds) throws IOException {
        JobWithDetails details = job.details() == null ? Mockito.mock(JobWithDetails.class) : job.details();
        if (job.details() == null) {
            Mockito.when(job.details()).thenReturn(details);
        }
        Mockito.when(details.getBuilds()).thenReturn(allBuilds);
    }

    /**
     *
     * @param jobName
     * @param folderName
     * @param times
     * @throws IOException
     */
    private void verifyJobCreation(String jobName, String folderName, int times) throws IOException {
        if (folderName == null) {
            verify(client, times(times)).createJob(Mockito.eq(jobName), Mockito.anyString());
        } else {
            Matcher<FolderJob> matchersParentName = Matchers.hasProperty("displayName", Matchers.equalTo(folderName));
            verify(client, times(times)).createJob(MockitoHamcrest.argThat(matchersParentName), Mockito.eq(jobName), Mockito.anyString());
        }
    }


    /**
     * Verify folder creation
     * @param name
     * @param parentName
     * @throws IOException
     */
    private void verifyFolderCreation(String name, String parentName) throws IOException {
        verifyFolderCreation(name, parentName, 1);
    }

    /**
     *
     * @param name
     * @param parentName
     * @param times
     * @throws IOException
     */
    private void verifyFolderCreation(String name, String parentName, int times) throws IOException {
        if (parentName == null) {
            verify(client, times(times)).createFolder(name);
        } else {
            Matcher<FolderJob> matchersParentName = Matchers.hasProperty("displayName", Matchers.equalTo(parentName));
            verify(client, times(times)).createFolder(MockitoHamcrest.argThat(matchersParentName), Mockito.eq(name));
        }
    }

    /**
     * Mock job existing
     * @param jobName
     * @param folderJobParent
     * @throws IOException
     */
    public JobWithDetails mockJob(String jobName, FolderJob folderJobParent) throws IOException {
        return mockJob(jobName,folderJobParent,null);
    }

    /**
     * Mock job existing
     * @param jobName
     * @param folderJobParent
     * @throws IOException
     */
    public JobWithDetails mockJob(String jobName, FolderJob folderJobParent, String folderPath) throws IOException {

        JobWithDetails job = Mockito.mock(JobWithDetails.class);
        Mockito.when(job.getName()).thenReturn(jobName);
        Mockito.when(job.getDisplayName()).thenReturn(jobName);
        Mockito.when(client.getJob(folderJobParent, jobName)).thenReturn(job);
        if (folderPath!=null)
            Mockito.when(client.getJob(folderPath+"/"+jobName)).thenReturn(job);
        else
            Mockito.when(client.getJob(jobName)).thenReturn(job);

        addJobMockedToList(job, folderJobParent);
        return job;
    }

    /**
     * Mock folderJob existing
     * @param folderName
     * @param folderJobParent
     * @throws IOException
     */
    private FolderJob mockFolderExisting(String folderName, FolderJob folderJobParent) throws IOException {

        JobWithDetails job = Mockito.mock(JobWithDetails.class);
        Mockito.when(job.getName()).thenReturn(folderName);
        addJobMockedToList(job, folderJobParent);
        if (folderJobParent == null) {
            Mockito.when(client.getJob(folderName)).thenReturn(job);
        } else {
            Mockito.when(client.getJob(folderJobParent, folderName)).thenReturn(job);
        }
        FolderJob folderJob = Mockito.mock(FolderJob.class);
        Mockito.when(folderJob.getName()).thenReturn(folderName);
        Mockito.when(folderJob.getDisplayName()).thenReturn(folderName);

        Mockito.when(client.getFolderJob(job)).thenReturn(Optional.of(folderJob));
        return folderJob;
    }

    private void mockJobConfigurationExisting(String path, String xmlValue) throws IOException {
        Mockito.when(client.getJobXml(path)).thenReturn(xmlValue);
    }

    private void verifyJobConfigurationUpdate(String path, String xmlValue) throws IOException {
        verify(client, times(1)).updateJob(Mockito.eq(path), Mockito.contains(xmlValue));
    }


    /**
     * Add job mock to list
     * @param job
     * @param folderJobParent
     * @throws IOException
     */
    private void addJobMockedToList(Job job, FolderJob folderJobParent) throws IOException {
        Map<String, Job> jobsList = (folderJobParent == null) ? client.getJobs() : client.getJobs(folderJobParent);
        // Override jobList with mock if the list is empty
        if (jobsList.isEmpty()) {
            jobsList = new HashMap<>();
            if (folderJobParent == null) {
                Mockito.when(client.getJobs()).thenReturn(jobsList);
            } else {
                Mockito.when(client.getJobs(folderJobParent)).thenReturn(jobsList);
            }
        }
        jobsList.put(job.getName(), job);
    }

    /**
     * Mock folder creation
     *
     * @throws IOException
     */
    private void mockFolderCreation() throws IOException {
        foldersNames = ArgumentCaptor.forClass(String.class);
        folderJobs = ArgumentCaptor.forClass(FolderJob.class);

        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws IOException {
                String folderName = invocation.getArgument(0);
                mockFolderExisting(folderName.toUpperCase(), null);
                return null;
            }
        }).when(client).createFolder(foldersNames.capture());

        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) throws IOException {
                FolderJob folderJobParent = invocation.getArgument(0);
                String folderName = invocation.getArgument(1);
                mockFolderExisting(folderName, folderJobParent);
                return null;
            }
        }).when(client).createFolder(folderJobs.capture(), foldersNames.capture());
    }

    public void mockCredentialCreation(Integer httpResponseStatus) throws Exception {
        HttpResponse<String> httpResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(httpResponse.getStatus()).thenReturn(httpResponseStatus);
        Mockito.when(httpResponse.getStatusText()).thenReturn(String.valueOf(httpResponseStatus));
        Mockito.when(jenkinsRestService.postBasicAuth(Mockito.anyString(),Mockito.any(), Mockito.anyMap())).thenReturn(httpResponse);
    }

    @Test
    public void given_template_repository_and_group_when_getTemplateXML_then_works() {
        String expected  = underTest.getTemplate()      .get(JenkinsTemplate.JavaLibrary)
                                                        .replace("${repoUrl}", "XXXX")
                                                        .replace("${credentialId}", "yyyy");

        String result = underTest.getTemplateXML(JenkinsTemplate.JavaLibrary,"XXXX", "yyyy");
        Assertions.assertEquals(expected.trim(), result.trim());

    }



    /**
     * Check delete
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void given_nominal_args_folder_when_deletePipeline_then_works() throws Exception {
        JobWithDetails job = Mockito.mock(JobWithDetails.class);
        FolderJob folderJob = Mockito.mock(FolderJob.class);
        Mockito.when(client.getJob(GROUP)).thenReturn(job);
        Mockito.when(client.getFolderJob(job)).thenReturn(Optional.of(folderJob));
        underTest.deletePipeline(new Pipeline().path(ARTIFACT_ID_PATH));
        verify(client).deleteJob(folderJob, ARTIFACT_ID, true);
    }

    /**
     * Check delete with error
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void given_pipeline_with_deleting_error_when_deletePipeline_then_throw_exception() throws Exception {
        JobWithDetails job = Mockito.mock(JobWithDetails.class);
        FolderJob folderJob = Mockito.mock(FolderJob.class);
        Mockito.when(client.getJob(GROUP)).thenReturn(job);
        Mockito.when(client.getFolderJob(job)).thenReturn(Optional.of(folderJob));
        Mockito.doThrow(new IOException()).when(client).deleteJob(folderJob, ARTIFACT_ID, true);
        assertThrows(Exception.class,()-> {
            underTest.deletePipeline(new Pipeline().path(ARTIFACT_ID_PATH));;
        });
    }

    /**
     * Check delete with error
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void given_pipeline_no_deletable_when_deletePipeline_then_throw_exception() throws Exception {
        JobWithDetails job = Mockito.mock(JobWithDetails.class);
        FolderJob folderJob = Mockito.mock(FolderJob.class);
        Mockito.when(client.getJob(GROUP)).thenReturn(job);
        Mockito.when(client.getFolderJob(job)).thenReturn(Optional.of(folderJob));
        Mockito.doAnswer(invocationOnMock -> {
            Mockito.when(client.getJob(ARTIFACT_ID_PATH)).thenReturn(Mockito.mock(JobWithDetails.class));
            return null;
        }).when(client).deleteJob(folderJob, ARTIFACT_ID, true);
        assertThrows(IllegalStateException.class,()-> {
            underTest.deletePipeline(new Pipeline().path(ARTIFACT_ID_PATH));;
        });
    }
}