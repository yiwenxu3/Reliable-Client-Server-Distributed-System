package sv.Server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import sv.Server.controllers.ServerController;

import java.util.HashMap;

@Component
public class Scheduler {

    HashMap<Integer, Integer> idToPort = new HashMap<Integer, Integer>(){{
        put(1, 8081);
        put(2, 8082);
        put(3, 8083);
    }};

    int checkpointId = 0;

    public RestTemplate getTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setOutputStreaming(false);
        restTemplate.setRequestFactory(requestFactory);
        restTemplate.setErrorHandler(new RestTemplateResponseErrorHandler());
        return restTemplate;
    }

    public HttpEntity getRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity request = new HttpEntity(headers);
        return request;
    }

    Log log = LogFactory.getLog(ServerApplication.class.getName());
    private RestTemplate restTemplate = getTemplate();
    private HttpEntity request = getRequest();

    @Autowired
    ServerConfiguration configuration;


    @Scheduled(fixedRateString = "${checkpointFrequency}")
    public void sendCheckpoint() {

        boolean primary = configuration.isPrimary();
        boolean active = configuration.isActive();
        int replicaId = configuration.getReplicaId();

        if (active || !primary) return;

        checkpointId++;
        int state = ServerController.state;
        boolean check = false;
        for (int id : idToPort.keySet()) {
            if (id != replicaId) {
                String sendCheckpointUrl = String.format("http://localhost:%d/receive?primary=%d&backup=%d&state=%d&checkpointId=%d", idToPort.get(id), replicaId, id, state, checkpointId);
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            sendCheckpointUrl,
                            HttpMethod.POST,
                            request,
                            String.class);
                    check = true;
                    log.info(response.getBody());
                } catch (Exception e) {
                }
            }
        }
        if (!check) return;

        int rid = configuration.getRequestId();
        String updateRMUrl = String.format("http://localhost:%d/prune?requestId=%d", 8060, rid);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    updateRMUrl,
                    HttpMethod.POST,
                    request,
                    String.class);
            log.info(response.getBody());
        } catch (Exception e) {
        }
    }
}
