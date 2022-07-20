package com.example;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.automatiko.engine.api.runtime.process.WorkItem;
import io.automatiko.engine.api.runtime.process.WorkItemHandler;
import io.automatiko.engine.api.runtime.process.WorkItemManager;
import io.automatiko.engine.api.workflow.ProcessConfig;
import io.automatiko.engine.services.io.ClassPathResource;
import io.automatiko.engine.workflow.DefaultWorkItemHandlerConfig;
import io.automatiko.engine.workflow.base.core.context.variable.JsonVariableScope;
import io.automatiko.engine.workflow.serverless.ServerlessModel;
import io.automatiko.engine.workflow.serverless.ServerlessProcess;
import io.automatiko.engine.workflow.serverless.ServerlessProcessInstance;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

@Path("/hello")
public class ExampleResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {

        //return "Hello from RESTEasy Reactive";
        List<ServerlessProcess> list = ServerlessProcess.from(new ClassPathResource("inject-state/helloworld-multiple.json", this.getClass().getClassLoader()));

        ServerlessProcess process = list.get(0);

        ServerlessProcessInstance pi = (ServerlessProcessInstance) process.createInstance();
        pi.start();

        TextNode tn= (TextNode) pi.variables().toMap().get("result");

        return tn.asText();
    }

    @GET
    @Path("/flowtest")
    @Produces(MediaType.TEXT_PLAIN)
    public String customWorkflow() throws JsonProcessingException {
        ProcessConfig processConfig = ServerlessProcess.processConfig();
        ((DefaultWorkItemHandlerConfig) processConfig.workItemHandlers()).register("Service Task", new WorkItemHandler() {

            @Override
            public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {

                String url = (String) workItem.getParameter("interfaceImplementationRef");
                String baseUrl = url.substring(0, url.length()-5);

                ObjectNode objectNode = (ObjectNode) workItem.getParameter("requestbody");
                String str = objectNode.get("Result").textValue();
                JSONObject jsonObject = new JSONObject(str);
                String score = doPost(baseUrl, jsonObject);

                ObjectMapper mapper = new ObjectMapper();
                ObjectNode obdata = mapper.createObjectNode();
                obdata.put("score", score);

                manager.completeWorkItem(workItem.getId(),
                        Collections.singletonMap(JsonVariableScope.WORKFLOWDATA_KEY, obdata));
            }

            @Override
            public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {

            }
        });

        List<ServerlessProcess> list = ServerlessProcess.from(processConfig, new ClassPathResource("function-call-state/gdbt-function.json", this.getClass().getClassLoader()));

        ServerlessProcess process = list.get(0);

//        ServerlessProcess process = ServerlessProcess.from(processConfig, new ClassPathResource("function-call-state/gdbt-function.json"))
//                .get(0);

        JsonNode data = new ObjectMapper().readTree("{\n"
                + "  \"person\": {\"name\" : \"john\"}\n"
                + "}");
        ServerlessProcessInstance pi = (ServerlessProcessInstance) process.createInstance(ServerlessModel.from(data));
        pi.start();

        JsonNode jn= (JsonNode)pi.variables().toMap().get("res");
        return jn.toString();
    }

    public static String doPost(String url, JSONObject jsonObject){
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost(url);
        String score = "";
        try {
            StringEntity s = new StringEntity(jsonObject.toString(), "UTF-8");
            s.setContentEncoding("UTF-8");
            s.setContentType("application/json");

            post.setEntity(s);
            post.addHeader("Content-Type", "application/json;charset=UTF-8");
            CloseableHttpResponse res = client.execute(post);
            if(res.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
                HttpEntity entity = res.getEntity();
                String result = EntityUtils.toString(res.getEntity(), "UTF-8");// 返回json格式：
                JSONObject resObject = new JSONObject(result);
                JSONArray instance = resObject.getJSONArray("instances");
                score = ((JSONObject)instance.get(0)).getJSONArray("scores").toString();
                System.out.println("Response :"+result);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return score;
    }

}