package sv.RM;

import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
import sv.RM.controllers.RmController;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MyRequest {
    Map<Integer, String> baseUrls = new HashMap<>();
    RestTemplate restTemplate;
    HttpEntity requestEntity;
    ExecutorService executor = (ExecutorService) Executors.newFixedThreadPool(3);
    List<RequestThread> requestThreadList = new ArrayList<>();
    List<Future<ResponseEntity>> responseList = new ArrayList();
//    Log log = LogFactory.getLog(MyRequest.class.getName());

    public MyRequest() {
        restTemplate = getTemplate();
        requestEntity = getRequest();
    }

    public List<ResponseEntity> request (int newState, String clientId, int requestId, HashMap<Integer, Boolean> members) {
        int replicaPort;
        if (newState != -1) {

            for(int replicaId: members.keySet()) {
                if (!members.get(replicaId)) continue;
                replicaPort = RmController.idToPort.get(replicaId);
                baseUrls.put(replicaId, String.format("http://localhost:%d/state?state=%d&user=%s&id=%d&replicaid=%d",
                        replicaPort, newState, clientId, requestId,replicaId));
            }
        } else {
            for (int replicaId: members.keySet()) {
                if (!members.get(replicaId)) continue;
                replicaPort = RmController.idToPort.get(replicaId);
                baseUrls.put(replicaId, String.format("http://localhost:%d/state?user=%s&id=%d&replicaid=%d",
                        replicaPort, clientId, requestId, replicaId));
            }
        }

        for (int replicaId: baseUrls.keySet()) {
            try {
                HttpMethod httpMethod = HttpMethod.POST;
                if (newState == -1) {
                    httpMethod = HttpMethod.GET;
                }
                RequestThread thread = new RequestThread(restTemplate, baseUrls.get(replicaId), httpMethod, requestEntity, clientId, replicaId);
                requestThreadList.add(thread);
            } catch (Exception exception) { }
        }

        try{
            responseList = executor.invokeAll(requestThreadList);
        } catch (InterruptedException exception) {}

        executor.shutdown();

        List<ResponseEntity> responses = new ArrayList<>();
        for (int i = 0; i < responseList.size(); i++) {
            Future<ResponseEntity> future = responseList.get(i);

            try{
                ResponseEntity response = future.get();
                responses.add(response);
            } catch (InterruptedException | ExecutionException e) {}
        }
        return responses;
    }

    public HttpEntity getRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity entity = new HttpEntity(headers);
        return entity;
    }

    public RestTemplate getTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setOutputStreaming(false);
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
}