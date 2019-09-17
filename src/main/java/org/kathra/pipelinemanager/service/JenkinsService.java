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
import com.mashape.unirest.http.HttpResponse;
import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.*;
import org.kathra.core.model.Build;
import org.kathra.core.model.*;
import org.kathra.pipelinemanager.Config;
import org.kathra.pipelinemanager.model.Credential;
import org.kathra.utils.sanitizing.SanitizeUtils;
import javassist.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;

/**
 * Jenkins service
 *
 * @author Julien Boubechtoula <Julien.Boubechtoula@kathra.org>
 */
public class JenkinsService {

    private static Logger logger = Logger.getLogger(JenkinsService.class);

    private static final String PROVIDER_NAME = "jenkins";
    private static final Integer DEFAULT_BUILD_MAX = 5;
    private static final Set<String> jenkinsGuestPermission = new HashSet(Arrays.asList("hudson.model.Item.Read","hudson.model.View.Read"));
    private static final Set jenkinsContributorPermission = new HashSet(Arrays.asList("hudson.model.Item.Read","hudson.model.Item.Build","hudson.model.Item.Cancel","hudson.model.View.Read"));
    private static final Set jenkinsMaintainerPermission = new HashSet(Arrays.asList("hudson.model.Item.Build","hudson.model.Item.Cancel","hudson.model.Item.Configure"
                                                                                    ,"hudson.model.Item.Create","hudson.model.Item.Delete","hudson.model.Item.Discover"
                                                                                    ,"hudson.model.Item.Move","hudson.model.Item.Read","hudson.model.Item.Workspace"
                                                                                    ,"hudson.model.Run.Delete", "hudson.model.Run.Replay","hudson.model.Run.Update"
                                                                                    ,"hudson.model.View.Configure", "hudson.model.View.Create","hudson.model.View.Delete"
                                                                                    ,"hudson.model.View.Read"));

    private static final String authMatrixProperty = "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty";

    private HashMap<JenkinsTemplate, String> templates = new HashMap();

    private JenkinsServer client;
    private JenkinsRestService jenkinsRestService;

    private Config config;



    // private static List<String> subRepositories = ImmutableList.of("interface", "model", "client");
    // private static List<String> templateList = Arrays.asList("JavaLibrary", "JavaService", "PythonLibrary", "PythonService");

    public JenkinsService(Config config) throws Exception {
        client = new JenkinsServer(new URI(config.getJenkinsUrl()), config.getJenkinsAccountName(), config.getJenkinsAccountApiToken());
        jenkinsRestService = new JenkinsRestService(config.getJenkinsAccountName(), config.getJenkinsAccountApiToken());
        this.config = config;
        for (JenkinsTemplate name : JenkinsTemplate.values()) {
            templates.put(name, IOUtils.toString(getClass().getClassLoader().getResourceAsStream("templates/" + name + ".template"), "UTF-8"));
        }
    }

    public JenkinsService(JenkinsServer client, JenkinsRestService jenkinsRestService, Config config) throws Exception {
        this.client = client;
        this.config = config;
        this.jenkinsRestService = jenkinsRestService;
        for (JenkinsTemplate name : JenkinsTemplate.values()) {
            templates.put(name, IOUtils.toString(getClass().getClassLoader().getResourceAsStream("templates/" + name + ".template"), "UTF-8"));
        }
    }


    public JenkinsServer getClient() {
        return client;
    }

    public HashMap<JenkinsTemplate, String> getTemplate() {
        return templates;
    }

    public Credential addCredential(Credential credential) throws Exception {
        if (credential == null
                || StringUtils.isEmpty(credential.getPath())
                || StringUtils.isEmpty(credential.getCredentialId())
                || StringUtils.isEmpty(credential.getPrivateKey())
                || StringUtils.isEmpty(credential.getUsername()))  throw new IllegalArgumentException("Credential arguement is null or incomplete.");

        if(credential.getPath()!=null && credential.getPath().startsWith("/")) {
            credential.path(credential.getPath().substring(1));
        }

        JSONObject privateKeySource = new JSONObject();
        privateKeySource.put("stapler-class", "com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey$DirectEntryPrivateKeySource");
        privateKeySource.put("privateKey", credential.getPrivateKey());

        JSONObject jsonCredential = new JSONObject();
        jsonCredential.put("id", credential.getCredentialId());
        jsonCredential.put("username", credential.getUsername());
        jsonCredential.put("passphrase", "");
        jsonCredential.put("privateKeySource", privateKeySource);
        jsonCredential.put("description", credential.getDescription());
        jsonCredential.put("stapler-class", "com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey");
        JSONObject json = new JSONObject();
        json.put("", "0");
        json.put("credentials", jsonCredential);

        String jenkinsRelativeUrl = getJenkinsRelativeURL(credential.getPath());

        HttpResponse<String> response = jenkinsRestService.postBasicAuth(config.getJenkinsUrl() + "/job/" + jenkinsRelativeUrl + "/credentials/store/folder/domain/_/createCredentials", json,Map.of("Content-Type","application/x-www-form-urlencoded"));

        if (response.getStatus()>=400) throw new Exception("Error when calling Jenkins - "+ String.valueOf(response.getStatus())+" "+response.getStatusText());

        return credential;
    }


    public Membership addMembership(Membership membership) throws Exception {
        if (membership == null || StringUtils.isEmpty(membership.getPath()) || membership.getMemberType() == null
                || membership.getMemberName() == null || membership.getRole() == null)
            throw new IllegalArgumentException("Membership with all fields must be specified.");
        String path = SanitizeUtils.sanitizePathParameter(membership.getPath());

        while (path != null) {
            addMembership(path, membership);
            if (path.contains("/"))
                path = StringUtils.substringBeforeLast(path,"/");
            else path = null;
        }

        return membership;
    }

    private void addMembership(String path, Membership membership) throws Exception{
        JobWithDetails jenkinsPipeline = client.getJob(path);
        if (jenkinsPipeline != null) {
            String relativeURL = getPipelineRelativeURL(jenkinsPipeline);
            final Document document = getDocumentNode(relativeURL);

            Element root = document.getDocumentElement();
            Node properties = getFirstChildNode(root, "properties");

            Node authz = getFirstChildNode(properties, authMatrixProperty);

            if (authz==null) {
                authz = document.createElement(authMatrixProperty);
                properties.appendChild(authz);
            }
            authz = removePermissions(authz,membership.getMemberName());

            for (String permission : memberShipToPermission(membership)) {
                Element permissionNode = document.createElement("permission");
                permissionNode.setTextContent(permission);
                authz.appendChild(permissionNode);
            }

            authz = removeChildNode(authz, "inheritanceStrategy");
            Element strategyNode = document.createElement("inheritanceStrategy");
            strategyNode.setAttribute("class", "org.jenkinsci.plugins.matrixauth.inheritance.InheritGlobalStrategy");
            authz.insertBefore(strategyNode, authz.getFirstChild());

            String newXml = getStringFromDocument(document);
            client.updateJob(relativeURL, newXml);
        }
    }

    private Node removeChildNode(Node node, String tagName) {
        NodeList children = node.getChildNodes();
        for (int k = 0; k < children.getLength(); k++) {
            Node child = children.item(k);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && child.getNodeName() == tagName) {
                node.removeChild(child);
            }
        }
        return node;
    }

    private Node removePermissions(Node authz, String group) {
        NodeList permissions = authz.getChildNodes();
        Set<Node> nodesToDelete = new HashSet<>();

        for (int k = 0; k < permissions.getLength(); k++) {
            Node permission = permissions.item(k);
            if (permission.getNodeType() == Node.ELEMENT_NODE
                    && permission.getNodeName() == "permission") {
                
                String[] permissionText = StringUtils.split(permission.getTextContent(), ":");
                String permissionGroup = permissionText[1];
                if(group.equals(permissionGroup))
                    nodesToDelete.add(permission);
            }
        }
        for (Node nodeToDelete : nodesToDelete) {
            authz.removeChild(nodeToDelete);
        }
        return authz;
    }

    private String getPipelineRelativeURL(JobWithDetails jenkinsPipeline) {
        String url = jenkinsPipeline.getUrl();
        return url.substring(url.indexOf("job") + 4);
    }

    private String getJenkinsRelativeURL(String relativeUrl) {
        return relativeUrl.replace("/", "/job/");
    }

    private Document getDocumentNode(String relativeURL) throws IOException, ParserConfigurationException, SAXException {
        String xml = client.getJobXml(relativeURL);

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();

        InputSource is = new InputSource(new StringReader(xml));

        return builder.parse(is);
    }

    public Build createBuild(Build build) throws Exception {
        if (build==null || StringUtils.isEmpty(build.getPath()))
            throw new IllegalArgumentException("Build path must be specified.");
        Job jenkinsPipeline = client.getJob(build.getPath());
        if (jenkinsPipeline == null) {
            throw new NotFoundException("Pipeline does not exist.");
        }
        try {

            Map<String,String> params = null;
            List<BuildArgument> buildArguments = build.getBuildArguments();
            if (buildArguments!=null) {
                params = new HashMap<>();
                for (BuildArgument buildArgument : buildArguments) {
                    params.put(buildArgument.getKey(), buildArgument.getValue());
                }
            }
            com.offbytwo.jenkins.model.Build lastKnownBuild = getLastBuild(jenkinsPipeline, null);
            QueueReference jenkinsBuild = params == null  ? jenkinsPipeline.build() : jenkinsPipeline.build(params);
            return map(build, getLastBuild(jenkinsPipeline, lastKnownBuild));
        } catch(Exception e) {
            e.printStackTrace();
            throw new Exception("Unable to exec build for pipeline "+build.getPath());
        }
    }

    private com.offbytwo.jenkins.model.Build getLastBuild(Job jenkinsPipeline, com.offbytwo.jenkins.model.Build lastKnownBuild) throws Exception {
        int attempt = 0;
        int maxAttempts = 20;
        com.offbytwo.jenkins.model.Build lastBuild = null;
        do {
            if (attempt >= maxAttempts) {
                throw new Exception("Unable to get current build for job '" + jenkinsPipeline.getName()+"'");
            } else if (lastBuild != null) {
                Thread.sleep(500);
            }
            lastBuild = jenkinsPipeline.details().getLastBuild();
            attempt++;
        } while (lastKnownBuild != null && lastBuild.getNumber() == lastKnownBuild.getNumber());
        return lastBuild;
    }

    public Boolean createFolder(String path) throws Exception {
        if (StringUtils.isEmpty(path)) throw new IllegalArgumentException("Path argument must be specified");
        createFolderHierarchyIfNotExists(getFolders(path));
        return true;
    }

    public Pipeline createPipeline(Pipeline pipeline) throws Exception {

        if (pipeline == null)
            throw new IllegalArgumentException("Pipeline argument is null");

        String pipelinePath = pipeline.getPath();
        String repositoryUrl = pipeline.getSourceRepository()==null?StringUtils.EMPTY:pipeline.getSourceRepository().getSshUrl();
        Pipeline.TemplateEnum pipelineTemplate = pipeline.getTemplate();

        logger.info(
                "create pipeline : " +
                        "path:" + pipelinePath
                        +", repository:"+ repositoryUrl
                        +", pipelineTemplate:" + pipelineTemplate);

        if (StringUtils.isEmpty(pipelinePath) || StringUtils.isEmpty(repositoryUrl)    || pipelineTemplate == null ) {
            throw new IllegalArgumentException("1 or more arguments is null or empty");
        }

        String folderPath = pipelinePath.substring(0, pipeline.getPath().lastIndexOf("/"));
        String pipelineName = pipelinePath.substring(pipeline.getPath().lastIndexOf("/")+1);

        // Groups
        LinkedList<String> folders = getFolders(folderPath);
        FolderJob groupFolder = createFolderHierarchyIfNotExists(folders);

        // Job
        JobWithDetails existingJob = client.getJob(groupFolder, pipelineName);
        if (existingJob != null) {
            throw new IllegalStateException("Pipeline with name '" + pipelineName + "' is already existing in folder : '" + folderPath + "'");
        }
        client.createJob(groupFolder, pipelineName, getTemplateXML(map(pipelineTemplate), repositoryUrl, pipeline.getCredentialId()));

        // Test job creation is OK
        JobWithDetails jobCreated = client.getJob(groupFolder, pipelineName);
        if (jobCreated == null) {
            throw new Exception("Pipeline with name " + pipelineName + " should be created, but not exists.");
        }

        pipeline.providerId(String.join("/", folders)+"/" + pipelineName)
                .status(Resource.StatusEnum.READY)
                .provider(PROVIDER_NAME);

        logger.info("create pipelineName:" + pipelineName +", path:"+ pipelinePath+", repository:"+ repositoryUrl+", pipelineTemplate:" + pipelineTemplate+" => pipeline created");

        return pipeline;
    }

    public static String getStringFromDocument(Document doc) throws TransformerException {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.transform(domSource, result);
        return writer.toString();
    }

    public Build getBuild(String path, String buildNumber) throws Exception {
        try {
            Job pipeline = getPipelineExisting(path);
            com.offbytwo.jenkins.model.Build build = pipeline.details().getBuildByNumber(Integer.valueOf(buildNumber));
            if (build == null) {
                throw new NotFoundException("Not build existing");
            }
            return map(null, build);
        } catch(IOException e) {
            e.printStackTrace();
            throw new Exception("Unable to get last build for pipeline "+path,e);
        }

    }

    public List<Build> getBuilds(String path, String branch, Integer maxResult) throws Exception {

        if (maxResult==null)
            maxResult = DEFAULT_BUILD_MAX;

        List<Build> builds = new LinkedList<>();

        try {
            Job pipeline = getPipelineExisting(path);
            List<com.offbytwo.jenkins.model.Build> jenkinsBuilds = pipeline.details().getBuilds();

            // Jenkins build sort by build number
           // jenkinsBuilds.sort(Comparator.comparingInt(com.offbytwo.jenkins.model.Build::getNumber));

            int i = 0;

            while(i<jenkinsBuilds.size() && builds.size()<maxResult) {
                com.offbytwo.jenkins.model.Build jenkinsBuild = jenkinsBuilds.get(i);
                i++;
                Map<String, String> params = jenkinsBuild.details().getParameters();
                if (StringUtils.isEmpty(branch)
                        || (params!=null && branch.equals(params.get("GIT_BRANCH"))))
                    builds.add(map(null, jenkinsBuild));
            }

            return builds;

        } catch(IOException e) {
            e.printStackTrace();
            throw new Exception("Unable to get last build for pipeline "+path);
        }
    }


    public List<Membership> getMemberships(String path) throws Exception {
        List<Membership> memberships = new LinkedList<>();
        if (path == null || StringUtils.isEmpty(path))
            throw new IllegalArgumentException("Folder path must be specified.");
        JobWithDetails jenkinsPipeline = client.getJob(path);
        if (jenkinsPipeline!=null) {
            String relativeURL = getPipelineRelativeURL(jenkinsPipeline);
            final Document document = getDocumentNode(relativeURL);

            Element root = document.getDocumentElement();
            Node properties = getFirstChildNode(root, "properties");
            Node authz = getFirstChildNode(properties, "com.cloudbees.hudson.plugins.folder.properties.AuthorizationMatrixProperty");
            List<String> jenkinsPermissions = new LinkedList<>();
            NodeList permissions = authz.getChildNodes();

            for (int k = 0; k < permissions.getLength(); k++) {
                if (permissions.item(k).getNodeType() == Node.ELEMENT_NODE && permissions.item(k).getNodeName() == "permission") {
                    jenkinsPermissions.add(permissions.item(k).getTextContent());
                }
            }

            memberships = permisionToMembership(jenkinsPermissions,path);

        }
        return memberships;
    }

    /**
     *
     * @param parentNode
     * @param name
     * @return
     * @throws IllegalArgumentException
     */
    private Node getFirstChildNode(Node parentNode, String name) throws IllegalArgumentException {
        if (parentNode == null || name == null)
            throw new IllegalArgumentException("name and parent node cannot be null");
        NodeList childNodes = parentNode.getChildNodes();
        if (childNodes == null) return null;

        for (int i = 0; i < childNodes.getLength(); i++) {
            if(childNodes.item(i).getNodeType() == Node.ELEMENT_NODE && childNodes.item(i).getNodeName()==name) {
                return childNodes.item(i);
            }
        }
        return null;
    }



    /**
     * Get custom jenkins templateXml from repository and group name
     * @param template
     * @param repositoryURL
     * @param credentialId
     * @return
     */
    public String getTemplateXML(JenkinsTemplate template, String repositoryURL, String credentialId) {
        return templates.get(template).replace("${repoUrl}", repositoryURL).replace("${credentialId}", credentialId);
    }


    /**
     * Retreive existing Job, throws NotFoundException if doesn't exist
     * @param pipelineId
     * @return
     * @throws IOException
     * @throws NotFoundException
     */
    private Job getPipelineExisting(String pipelineId) throws IOException, NotFoundException {
        if (StringUtils.isEmpty(pipelineId)) {
            throw new IllegalArgumentException("Pipeline id should be defined.");
        }
        Job pipeline = client.getJob(pipelineId);
        if (pipeline == null) {
            throw new NotFoundException("Pipeline does not exist.");
        }
        return pipeline;
    }

    /**
     *
     * @param permissions
     * @return
     */
    private List<Membership> permisionToMembership(List<String> permissions, String path) {

        List<Membership> ret = new LinkedList<Membership>();
        Map<String, Set<String>> permissionsByGroup = permissionsByGroup(permissions);
        for ( Map.Entry<String,Set<String>> groupPermission : permissionsByGroup.entrySet()){
            Set<String> permissionSet = groupPermission.getValue();
            Membership m = new  Membership().memberName(groupPermission.getKey())
                    .memberType(Membership.MemberTypeEnum.GROUP)
                    .path(path);

            if (permissionSet.equals(jenkinsGuestPermission))
                m.setRole(Membership.RoleEnum.GUEST);
            else if(permissionSet.equals(jenkinsContributorPermission))
                m.setRole(Membership.RoleEnum.CONTRIBUTOR);
                        else if(permissionSet.equals(jenkinsMaintainerPermission))
            m.setRole(Membership.RoleEnum.MANAGER);
            else logger.warn("Inconsistant role mapping for "+groupPermission.getKey());

            ret.add(m);
        }
        return ret;
    }

    private List<String> memberShipToPermission(Membership membership){
        List<String> ret = new LinkedList<>();
        Set<String> permissionSet = null;
        switch(membership.getRole()){
            case GUEST:
                permissionSet = jenkinsGuestPermission;
                break;
            case CONTRIBUTOR:
                permissionSet = jenkinsContributorPermission;
                break;
            case MANAGER:
                permissionSet = jenkinsMaintainerPermission;
                break;
        }
        for(String permission:permissionSet)
            ret.add(permission+":"+membership.getMemberName());

        return ret;

    }

    /**
     *
     * @param permissions
     * @return
     */
    private Map<String, Set<String>> permissionsByGroup(List<String> permissions) {
        Map<String, Set<String>> permissionsByGroup = new HashMap<>();
        for(String permission : permissions) {
            String[] p = StringUtils.split(permission, ":");
            Set<String> permissionSet = permissionsByGroup.get(p[1]);
            if (permissionSet==null) {
                permissionSet = new HashSet<>();
            }
                permissionSet.add(p[0]);
                permissionsByGroup.put(p[1],permissionSet);
        }


        return permissionsByGroup;
    }
    /**
     * Map {@link Build} to {@link Build}
     * @param lastBuild
     * @return
     * @throws IOException
     */
    private Build map(Build build, com.offbytwo.jenkins.model.Build lastBuild) throws IOException {
        if (build==null) build = new Build();
        BuildWithDetails details = lastBuild.details();
        Build.StatusEnum status = Build.StatusEnum.PROCESSING;
        if (details!=null) {

            if(details.getClient() != null) {
                build.setLogs(details.getConsoleOutputText());
            } else {
                build.setLogs(StringUtils.EMPTY);
            }

            if (details.getResult() != null) {
                switch (details.getResult()) {
                    case SUCCESS:
                        status = Build.StatusEnum.SUCCESS;
                        break;
                    case FAILURE:
                    case UNSTABLE:
                    case ABORTED:
                    case CANCELLED:
                    case NOT_BUILT:
                        status = Build.StatusEnum.FAILED;
                        break;
                    case BUILDING:
                        status = Build.StatusEnum.PROCESSING;
                        break;

                    default:
                        throw new IllegalStateException("Status : " + details.getResult() + " is not implemented");
                }
            }
            build.setDuration((int) details.getDuration());
            build.setCreationDate(details.getTimestamp());
        }
        build.setStatus(status);
        build.setBuildNumber(lastBuild.getNumber()+"");
        return build;
    }

    private JenkinsTemplate map(Pipeline.TemplateEnum pipelineTemplate) {
        JenkinsTemplate ret = null;
        switch (pipelineTemplate) {
            case JAVA_LIBRARY:
                ret = JenkinsTemplate.JavaLibrary;
                break;
            case JAVA_SERVICE:
                ret = JenkinsTemplate.JavaService;
                break;
            case PYTHON_LIBRARY:
                ret = JenkinsTemplate.PythonLibrary;
                break;
            case PYTHON_SERVICE:
                ret = JenkinsTemplate.PythonService;
                break;
        }
        return ret;
    }

    private LinkedList<String> getFolders(String group) {
        LinkedList<String> ret = new LinkedList<>();
        if (StringUtils.isNotEmpty(group)) {
            String[] array = StringUtils.split(group, '/');
            array[0] = StringUtils.upperCase(array[0]);
            ret.addAll(Arrays.asList(array));
        }
        return ret;
    }


    private FolderJob createFolderHierarchyIfNotExists(LinkedList<String> folders) throws IOException, InterruptedException {
        FolderJob currentFolder = null;
        Iterator<String> iterator = folders.iterator();
        while (iterator.hasNext()) {
            // Create every folder recursively under the previous one
            currentFolder = createFolderIfNotExists(iterator.next(), currentFolder);
        }
        return currentFolder;
    }

    private FolderJob getFolderJob(FolderJob parentFolder, String group, JenkinsServer client) throws IOException {
        Job job = (parentFolder==null) ? client.getJob(group):client.getJob(parentFolder, group);
        return (job != null) ? client.getFolderJob(job).get() : null;
    }

    private synchronized FolderJob createFolderIfNotExists(String group, FolderJob parentFolder) throws IOException, InterruptedException {

        int nbRetry = 0;
        FolderJob groupFolder = null;

        while (groupFolder == null && nbRetry<=3) {
            groupFolder = getFolderJob(parentFolder, group, client);
            if (groupFolder == null) {
                try {
                    if (parentFolder == null)
                        client.createFolder(group);
                    else
                        client.createFolder(parentFolder,group);
                } catch (HttpResponseException e) {
                    Thread.sleep(300);
                    nbRetry++;
                }
            }
        }
        return groupFolder;
    }

}
