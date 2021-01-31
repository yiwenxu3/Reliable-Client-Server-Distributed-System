package sv.RM.controllers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import sv.RM.MyRequest;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Controller
public class RmController {
    public static HashMap<Integer, Integer> idToPort = new HashMap<Integer, Integer>(){{
        put(1, 8081);
        put(2, 8082);
        put(3, 8083);
    }};

    class singleRequest {
        public int requestId;
        public int newState;
        public String clientId;

        public singleRequest(int requestId, int newState, String clientId) {
            this.requestId = requestId;
            this.newState = newState;
            this.clientId = clientId;
        }

        @Override
        public String toString() {
            return String.format("requestId = %d, newState = %d, clientId = %s", requestId, newState, clientId);
        }
    }

    static public HashMap<Integer, Boolean> members = new HashMap<>();
    private Log log = LogFactory.getLog(RmController.class.getName());
    int membershipChange = 0;
    Queue<singleRequest> requestLog = new LinkedList<>();
    boolean active = false;
    int checkpointId = 10000;

    private final ReadWriteLock logLock = new ReentrantReadWriteLock(true);
    private final Lock logReadLock = logLock.readLock();
    private final Lock logWriteLock = logLock.writeLock();

    private final ReadWriteLock membershipLock = new ReentrantReadWriteLock(true);
    private final Lock mbshipReadLock = membershipLock.readLock();
    private final Lock mbshipWriteLock = membershipLock.writeLock();

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
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
    static private HttpEntity getRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity request = new HttpEntity(headers);
        return request;
    }

    /*
    RM is notified by GFD of the membership
     */
    @PostMapping(path="/register")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    String registerWithGfd (@RequestParam("membercount") int memberCount) {
        log.info("RM: " + memberCount + " members");
        return "SUCCESS";
    }


    /*
    GFD notifies the RM that replica is ready / pending(alive)
     */
    @PostMapping(path = "/update")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    String changeMembership(@RequestParam("replica") int replica, @RequestParam("isactive") boolean isActive,
                            @RequestParam("isprimary") boolean isPrimary, @RequestParam("isready") boolean isReady,
                            @RequestParam("status") String status){

        String replicaState = isPrimary? "primary" : "backup";
        active = isActive;
        if (status.equals("dead")) {
            logWriteLock.lock();
            mbshipWriteLock.lock();
            int replicaId = 0;
            try{
                membershipChange++;
                members.remove(replica);
                log.info(String.format("RM view: %d  change: %d(%s) removed  %d members: %s",membershipChange, replica, replicaState, members.size(), reFormat(members)));

                // For Passive Replication, re-elect a Primary Replica
                if (!active && isPrimary && members.size() > 0) {

                    RestTemplate restTemplate = getTemplate();
                    HttpEntity request = getRequest();

                    for (int member : members.keySet()) {
                        replicaId = member;
                        break;
                    }

                    // For recovery send the requests to the primary candidate

                    HashMap<Integer, Boolean> candidate = new HashMap<>();
                    candidate.put(replicaId, true);

                    // Appoint the backup to be primary
                    String baseUrl = String.format("http://localhost:%s/setPrimary?", idToPort.get(replicaId));
                    try {
                        ResponseEntity<String> responseEntity = restTemplate.exchange(
                                baseUrl,
                                HttpMethod.POST,
                                request,
                                String.class);
                        log.info(String.format("RM appointed S%d(port: %d) to be the Primary Replica: %s ", replicaId, idToPort.get(replicaId), responseEntity.getBody()));

                        log.info("START PROCESSING LEFTOVER REQUESTS.....");
                        while (!requestLog.isEmpty()) {
                            singleRequest recoverRequest = requestLog.poll();
                            int id = recoverRequest.requestId;
                            int state = recoverRequest.newState;
                            String client = recoverRequest.clientId;

                            MyRequest newRequest = new MyRequest();
                            newRequest.request(state, client, id, candidate);
                            log.info(String.format("DONE processing from LOG  request_id: %d new_state: %d from_client: %s",
                                    id, state, client));
                        }
                        log.info("DONE.");

                    } catch (Exception e) {
                        log.info(String.format("RM lost connection with S%d(port: %d)", replicaId, idToPort.get(replicaId)));
                    }

                }
            }catch (Exception e){
                    log.info(String.format("Fail to send a log/setPrimary request to the backup replica S%d", replicaId));
            } finally {
                mbshipWriteLock.unlock();
                logWriteLock.unlock();
            }

            // Archived: recover the same server process using jar
            // startReplica(replica, isActive, isPrimary, checkpointFreq);

            //initial startinng server
        } else if (!members.containsKey(replica) && isReady){
            mbshipWriteLock.lock();
            membershipChange++;
            members.put(replica, isPrimary);
            mbshipWriteLock.unlock();
            log.info(String.format("RM view: %d  change: %d(%s) added  %d members: %s", membershipChange, replica, replicaState, members.size(),reFormat(members)));

            //alive and not ready, then we need to send checkpoint
        } else if (!members.containsKey(replica) && !isReady) {
            log.info(String.format("requesting checkpoint for S%d...", replica));
            //TODO: Differentiate between active and passive
            RestTemplate restTemplate = getTemplate();
            HttpEntity request = getRequest();

            mbshipWriteLock.lock();
            checkpointId++;
            try {
                Set<Integer> memberIds = members.keySet();
                boolean ready = false;
                for (int member : memberIds) {
                    if (members.get(member)) {
                        String baseUrl = String.format("http://localhost:%d/sendCheckpoint?newbornPort=%d&newbornId=%d&replicaId=%d&checkpointId=%d",
                                idToPort.get(member), idToPort.get(replica), replica, member, checkpointId);
                        try {
                            ResponseEntity<String> responseEntity = restTemplate.exchange(
                                    baseUrl,
                                    HttpMethod.POST,
                                    request,
                                    String.class);
                            log.info(String.format("RM requested S%d to send recovery checkpoint to S%d: %s", member, replica, responseEntity.getBody()));
                            ready = true;
                        } catch (Exception e) {
                            log.info(String.format("RM lost connection with S%d(port: %d)", member, idToPort.get(member)));
                        }
                    }
                }

                // checkpoints sent successfully, add the new replica to membership
                if (ready) membershipChange++;

                members.put(replica, isPrimary);
                log.info(String.format("RM view: %d  change: %d(%s) added  %d members: %s", membershipChange, replica, replicaState, members.size(),reFormat(members)));
            } finally {
                mbshipWriteLock.unlock();
            }

            //server in mebership list and is ready
        }else if(members.containsKey(replica)){
            // replica already added to the membership list after checkpointing
            if (isReady && members.get(replica) == isPrimary) return "RM's membership list is up-to-date";
            // changed from backup to primary
            mbshipWriteLock.lock();
            membershipChange++;
            members.put(replica, isPrimary);
            mbshipWriteLock.unlock();
            log.info(String.format("RM view: %d  change: %d(%s) becomes the Primary Replica  %d members: %s", membershipChange, replica, replicaState, members.size(),reFormat(members)));
        }

        return "SUCCESS";
    }

    @PostMapping(path = "/state")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    List<String> postRequest(@RequestParam("state") int newState, @RequestParam("user") String clientId,
                                   @RequestParam("id") int requestId) {
        logReadLock.lock();


        log.info(String.format("RECEIVED POST request_id: %d from client_id: %s", requestId, clientId));

        List<ResponseEntity> responses = new ArrayList<>();
        try {
            MyRequest request = new MyRequest();
            responses = request.request(newState, clientId, requestId, members);
        } catch (Exception e){
            log.info(String.format("FAILURE RM received no response for requestId %d from %s", requestId, clientId));
        } finally {
            if (!active && responses.size() != 0) {
                singleRequest newRequest = new singleRequest(requestId, newState, clientId);
                newRequest.requestId = requestId;
                newRequest.clientId = clientId;
                newRequest.newState = newState;
                requestLog.add(newRequest);
            }
            List<String> ls = new ArrayList<>();
            for (ResponseEntity response : responses) {
                ls.add((String)response.getBody());
            }
            logReadLock.unlock();
            return ls;
        }

    }


    @GetMapping(path = "/state")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    List<String> getState(@RequestParam("user") final String clientId, @RequestParam("id") final int requestId){


        log.info(String.format("RECEIVED GET request_id: %d from client_id: %s", requestId, clientId));
        //requestLog.offer(new singleRequest(requestId, -1, clientId));

        List<ResponseEntity> responses = new ArrayList<>();
        try {
            MyRequest request = new MyRequest();
            responses = request.request(-1, clientId, requestId, members);
        } finally {
            List<String> ls = new ArrayList<>();
            if (!active && responses.size() != 0) requestLog.offer(new singleRequest(requestId, -1, clientId));
            for (ResponseEntity response : responses) {
                ls.add((String)response.getBody());
            }
            return ls;
        }
    }

    @PostMapping(path = "/prune")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ResponseBody
    String getCheckpoint(@RequestParam("requestId") int requestId) {


//        int size = requestLog.size();
//        int i = 0;
//        while (i < size) {
//
//            System.out.println(requestLog.poll());
//            i++;
//        }

        logWriteLock.lock();
//        if (requestLog.isEmpty()) {
//            logWriteLock.unlock();
//            return String.format("SUCCESS: request log is empty");
//        }

        try {
            while (!requestLog.isEmpty() && requestLog.peek().requestId != requestId){
                log.info(requestLog.poll());
            }

            if (!requestLog.isEmpty()) {
                log.info(requestLog.poll());
            } else {
                return String.format("request %d is not pushed to the queue", requestId);
            }
            return String.format("SUCCESS: RM pruned the request log");
        } finally {
            logWriteLock.unlock();
        }

    }


    // for automatic replica recovery:
//   private void startReplica(int replica, boolean isActive, boolean isPrimary, int checkpointFreq) {
//       log.info(String.format("Relaunching S%d...", replica));
//       String lfdPort = String.format("807%d", replica);
//       RestTemplate restTemplate = getTemplate();
//       HttpEntity request = getRequest();
//
//       String baseUrl = String.format("http://localhost:%s/create?replica=%d&isactive=%b&isprimary=%b&checkpointfreq=%d", lfdPort, replica, isActive, isPrimary, checkpointFreq);
//       try {
//           ResponseEntity<String> responseEntity = restTemplate.exchange(
//                   baseUrl,
//                   HttpMethod.GET,
//                   request,
//                   String.class);
//           log.info(String.format("RM sent a request to LFD(port: %s) to re-launch S%d: %s ", lfdPort, replica, responseEntity.getBody()));
//       } catch (Exception e) {
//           log.info(String.format("RM lost connection with LFD(port: %s)", lfdPort));
//       }
//    }
}


