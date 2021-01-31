package sv.RM;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.Callable;

public class RequestThread implements Callable<ResponseEntity> {

    RestTemplate restTemplate;
    String url;
    HttpMethod httpMethod;
    HttpEntity requestEntity;
    String clientId;
    int replicaId;

    public RequestThread (RestTemplate restTemplate, String url, HttpMethod httpMethod, HttpEntity requestEntity, String clientId, int replicaId) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.httpMethod = httpMethod;
        this.requestEntity = requestEntity;
        this.clientId = clientId;
        this.replicaId = replicaId;
    }

    @Override
    public ResponseEntity call() throws Exception {
        ResponseEntity response = this.restTemplate.exchange(this.url,
                this.httpMethod,
                this.requestEntity,
                String.class);
        if (response.getBody() == null || response.getBody().equals("")) throw new Exception("");
        return response;
    }
}