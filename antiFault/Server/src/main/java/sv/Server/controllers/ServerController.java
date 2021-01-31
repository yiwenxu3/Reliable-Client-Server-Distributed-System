package sv.Server.controllers;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import sv.Server.RestTemplateResponseErrorHandler;
import sv.Server.ServerConfiguration;

@Controller
public class ServerController {
    public static int state = 30;

//    @Value("${primary}")
//    public static boolean primary;


    @Autowired
    ServerConfiguration serverConfiguration;


    private final Log log = LogFactory.getLog(ServerController.class.getName());

    @PostMapping(path = "/state")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    String change(@RequestParam("state") final int newState, @RequestParam("user") final String user, @RequestParam("id") final int requestId, @RequestParam("replicaid") int replicaId) {


        boolean primary = serverConfiguration.isPrimary();
        serverConfiguration.setRequestId(requestId);

        if (!primary) {
            return "";
        }
        int prev = state;
        state= newState;
        log.info(String.format("RECEIVED request_id: %d server_id: %d client_id: %s state: %d -> %d", requestId, replicaId, user, prev, state));
        return String.format("RESPONSE client_id: %s, replica_id: Replica_%d, request_num: %d, result: %d -> %d", user, replicaId, requestId, prev, state);
    }

    @GetMapping(path = "/state")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    String getState(@RequestParam("user") final String user, @RequestParam("id") final int requestId, @RequestParam("replicaid") int replicaId){
        serverConfiguration.setRequestId(requestId);

        boolean primary = serverConfiguration.isPrimary();
        if (!primary) {
            return "";
        }
        log.info(String.format("RECEIVED request_id: %d server_id: %d client_id: %s state: %d", requestId, replicaId, user, this.state));
        return String.format("RESPONSE client_id: %s, replica_id: Replica_%d, request_num: %s, state: %d", user, replicaId, requestId , this.state);
    }

    @GetMapping(path = "/isAlive")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    String isAlive() {
        boolean primary = serverConfiguration.isPrimary();
        boolean isReady = serverConfiguration.isReady();
        boolean isActive = serverConfiguration.isActive();

        log.info(String.format("The server %s is ready:%s prmary:%s active:%s",
                serverConfiguration.getReplicaId(),
                isReady,
                primary,
                isActive));

        return String.format("%b,%b", isReady, primary);
    }

    @PostMapping(path = "/receive")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    String receive(@RequestParam("primary") int primaryId, @RequestParam("backup") int backupId, @RequestParam("state") int newState, @RequestParam("checkpointId") int checkpointId) {

        boolean isReady = serverConfiguration.isReady();
        boolean active = serverConfiguration.isActive();
        // would be for either active or passive, but not both
        int prev = state;
        state = newState;

        if (!isReady) serverConfiguration.setReady(true);

        if (!active) {
            //passive
            log.info(String.format("RECEIVED: checkpointId %d from primary_%d to backup_%d  state: %d -> %d", checkpointId, primaryId, backupId, prev, state));
            return String.format("RESPONSE: backup_%d RECEIVED checkpointId %d from primary_%d state: %d -> %d", backupId, checkpointId, primaryId, prev, state);
        }

        //active

        log.info(String.format("RECEIVED: checkpointId %d from primary_%d to newborn_%d  state: %d -> %d", checkpointId, primaryId, backupId, prev, state));
        return String.format("RESPONSE: newborn_%d RECEIVED checkpointId %d from primary_%d state: %d -> %d", backupId, checkpointId, primaryId, prev, state);
    }

    @PostMapping(path = "/setPrimary")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    String setPrimary() {
        boolean primary = serverConfiguration.isPrimary();
        int replicaId = serverConfiguration.getReplicaId();
        if (!primary) serverConfiguration.setPrimary(true);
        log.info(String.format("S%d is now appointed to be the Primary", replicaId));
        return "SUCCESS";
    }

    static private RestTemplate getTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setOutputStreaming(false);
        restTemplate.setRequestFactory(requestFactory);
        restTemplate.setErrorHandler(new RestTemplateResponseErrorHandler());
        return restTemplate;
    }

    static private HttpEntity getRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity request = new HttpEntity(headers);
        return request;
    }

    RestTemplate restTemplate = getTemplate();
    HttpEntity request = getRequest();


    @PostMapping(path = "/sendCheckpoint")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    String send(@RequestParam("newbornPort") int port, @RequestParam("newbornId") int id, @RequestParam("replicaId") int from, @RequestParam("checkpointId") int checkpointId) {
        // need to know the target id
        int replicaId = serverConfiguration.getReplicaId();
        String sendCheckpointUrl = String.format("http://localhost:%d/receive?primary=%d&backup=%d&state=%d&checkpointId=%d", port, replicaId, id, state, checkpointId);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    sendCheckpointUrl,
                    HttpMethod.POST,
                    request,
                    String.class);
            log.info(response.getBody());
            return "SUCCESS";
        } catch (Exception e) {
            return "FAILURE";
        }
    }


//    @PostMapping(path = "/recover")
//    @ResponseStatus(HttpStatus.CREATED)
//    @ResponseBody
//    String recover(@RequestParam("senderid") int oldReplicaId, @RequestParam("state") int newState) {
//        int prev = state;
//        state = newState;
//        if (!isReady) isReady = true;
//        log.info(String.format("RECEIVED: checkpoint from S%d  state: %d -> %d", oldReplicaId, prev, state));
//        return "SUCCESS";
//    }

//    @PostMapping(path = "/sendCheckpoint")
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody
//    String sendCheckpoint(@RequestParam("port") int port, @RequestParam("newreplicaid") int newReplicaId,
//                          @RequestParam("oldreplicaid") int oldReplicaId) {
//        RestTemplate restTemplate = WarmPassiveServerApplication.getTemplate();
//        HttpEntity request = WarmPassiveServerApplication.getRequest();
//        String sendCheckpointUrl = String.format("http://localhost:%d/recover?senderid=%d&state=%d", port, oldReplicaId, state);
//        try {
//            ResponseEntity<String> response = restTemplate.exchange(
//                    sendCheckpointUrl,
//                    HttpMethod.POST,
//                    request,
//                    String.class);
//            log.info(String.format("Sent checkpoint to S%d: %s", newReplicaId, response.getBody()));
//            return "SUCCESS";
//        } catch (Exception e) {
//            log.info(String.format("Error connecting to the new Replica: S%d", newReplicaId));
//            return "FAILURE";
//        }
//    }
}