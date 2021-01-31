package sv.Client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.text.html.parser.Entity;
import java.util.*;

import com.jayway.jsonpath.JsonPath;

public class MyRequest {
    RestTemplate restTemplate;
    HttpEntity requestEntity;
    Random rand = new Random();
    int requestId;
    String clientId;

    Log log = LogFactory.getLog(MyRequest.class.getName());

    public MyRequest(String clientId, int id) {
        restTemplate = getTemplate();
        requestEntity = getRequest();
        requestId = id;
        this.clientId = clientId;
    }

    public void callRequest() {
        if (rand.nextBoolean()) {
            postState();
        } else {
            getState();
        }
    }

    public void getState() {
        String baseUrl = String.format("http://localhost:8060/state?user=%s&id=%d", clientId, requestId);

        log.info(String.format("REQUEST GET/ client_id: %s request_num: %d", clientId, requestId));
        try {

            MultiValueMap body = new LinkedMultiValueMap<>();
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<MultiValueMap> requestEntity
                    = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(baseUrl,HttpMethod.GET, requestEntity,
                    String.class);
            List<String> res = JsonPath.read(response.getBody(), "$");
            dedupe(res, "GET");

        } catch (Exception e) {
            log.info("FAILED to GET state");
        }
        requestId++;
    }

    public void postState() {
        int randomState = rand.nextInt(1000);
        String baseUrl = String.format("http://localhost:8060/state?user=%s&id=%d&state=%d", clientId, requestId, randomState);
        log.info(String.format("REQUEST POST/ client_id: %s request_num: %d send new state: %s", clientId, requestId, randomState));
        try {

            MultiValueMap body = new LinkedMultiValueMap<>();
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<MultiValueMap> requestEntity
                    = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(baseUrl,HttpMethod.POST, requestEntity,
                    String.class);
            List<String> res = JsonPath.read(response.getBody(), "$");
            dedupe(res, "POST");

        } catch (Exception e) {
            log.info("FAILED to POST state");
        }
        requestId++;
    }

    public void dedupe(List<String> responses, String httpMethod) {
        HashMap<String, String> replicaToState = new HashMap<>();
        for (int i = 0; i < responses.size(); ++i) {
            String responseBody = responses.get(i);
            String replicaId = getTargetFromResponse(responseBody, "replicaId", "");
            replicaToState.put(replicaId, responseBody);
        }

        int i = 0;
        String state = "";
        for (String replicaId : replicaToState.keySet()) {
            String responseBody = replicaToState.get(replicaId);
            if (i == 0) {
                state = getTargetFromResponse(responseBody, "state", httpMethod);
                log.info(String.format("RESPONSE %s/ %s", httpMethod, responseBody));
                if(replicaToState.keySet().size() == 1) {
                    //no duplicates
                    log.info("");
                    return;
                }
            } else {
                String dupState = getTargetFromResponse(responseBody, "state", httpMethod);
                if (state.equals(dupState)) {
                    log.info(String.format("Duplicated response received from %s, the state is %s", replicaId, dupState));
                } else {
                    log.info(String.format("Get different response from %s, the state is %s", replicaId, dupState));
                }
            }
            i++;
        }
    }

    public String getTargetFromResponse(String responseBody, String target, String httpMethod) {
        if (target.equals("replicaId")) {
            int replicaIndex = responseBody.indexOf("a_id: ");
            String replicaId = responseBody.substring(replicaIndex + 6, responseBody.indexOf(',', replicaIndex));
            return replicaId;
        } else if (target.equals("state") && httpMethod.equals("GET")) {
            int stateIndex = responseBody.indexOf("state: ");
            String state = responseBody.substring((stateIndex + 7));
            return state;
        } else if (target.equals("state") && httpMethod.equals("POST")) {
            int stateIndex = responseBody.indexOf("-> ");
            String state = responseBody.substring((stateIndex + 3));
            return state;
        }
        return "";
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
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(2000);
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
}