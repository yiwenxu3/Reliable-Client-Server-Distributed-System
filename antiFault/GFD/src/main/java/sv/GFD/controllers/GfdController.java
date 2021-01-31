package sv.GFD.controllers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import sv.GFD.RestTemplateResponseErrorHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Controller
public class GfdController {

    public static HashMap<Integer, Boolean> members = new HashMap<>();

    int membershipChange = 0;
    private Log log = LogFactory.getLog(GfdController.class.getName());

    public List<String> reFormat(HashMap<Integer, Boolean> members) {
        List<String> newList = new ArrayList<>();
        for (int replicaId : members.keySet()) {
            if (members.get(replicaId)) {
                newList.add(String.format("%d(primary)", replicaId));
            } else {
                newList.add(String.format("%d(backup)", replicaId));
            }
        }
        return newList;
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

    // for future reference when there is RM who sends request to LFD
    @PostMapping(path = "/update")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    String changeMembership(@RequestParam("replica") int replica, @RequestParam("isactive") boolean isActive,
                            @RequestParam("isprimary") boolean isPrimary, @RequestParam("isready") boolean isReady,
                            @RequestParam("checkpointfreq") int checkpointFreq, @RequestParam("status") String status){

        String replicaState = isPrimary? "primary" : "backup";

        if (status.equals("dead") && members.containsKey(replica)) {
            membershipChange++;
            members.remove(replica);
            log.info(String.format("GFD view: %d  change: %d(%s) removed  %d members: %s",membershipChange, replica, replicaState, members.size(), reFormat(members)));

        } else if (!members.containsKey(replica) && isReady){
            membershipChange++;
            members.put(replica, isPrimary);
            log.info(String.format("GFD view: %d  change: %d(%s) added  %d members: %s",membershipChange, replica, replicaState, members.size(),reFormat(members)));

        } else if (members.containsKey(replica)){
            membershipChange++;
            members.put(replica, isPrimary);
            log.info(String.format("GFD view: %d  change: %d(%s) becomes Primary Replica  %d members: %s",membershipChange, replica, replicaState, members.size(),reFormat(members)));
        }

        RestTemplate restTemplate = getTemplate();
        HttpEntity request = getRequest();

        int rmPort = 8060;
        String baseUrl = String.format("http://localhost:%d/update?replica=%d&isactive=%b&isprimary=%b&isready=%b&checkpointfreq=%d&status=%s",
                rmPort, replica, isActive, isPrimary, isReady, checkpointFreq, status);
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    request,
                    String.class);
            if (isReady) log.info(String.format("Membership update message sent to RM: %s", responseEntity.getBody()));
            else if (!status.equals("dead"))log.info(String.format("Tentative Membership update message sent to RM: %s", responseEntity.getBody()));
        } catch (Exception e) {
            log.info(String.format("GFD lost connection with RM(port: %s)", rmPort));
        }

        return "SUCCESS";
    }
}
