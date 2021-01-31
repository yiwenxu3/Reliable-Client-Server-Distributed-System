package sv.LFD;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@SpringBootApplication
@EnableScheduling
@EnableConfigServer
@RefreshScope
public class LfdApplication {

	@Value("${replicaId}")
	int replicaId;

	@Value("${active}")
	boolean isActive;

	@Value("${checkpointFrequency}")
	int checkpointFreq;

	boolean primary = false;
	boolean ready = false;

	HashMap<Integer, Integer> idToPort = new HashMap<Integer, Integer>(){{
		put(1, 8081);
		put(2, 8082);
		put(3, 8083);
	}};

	Log log = LogFactory.getLog(LfdApplication.class.getName());
	Set<Integer> serverStatus = new HashSet<>();
	private int heartbeatCount = 0;
	private int GfdPort = 8070;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(LfdApplication.class);
		app.run(args);
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

	private RestTemplate restTemplate = getTemplate();
	private HttpEntity request = getRequest();

	@Scheduled(fixedRateString ="${heartbeatRate}")
	public void heartbeat(){

		int replicaPort = idToPort.get(replicaId);
		heartbeatCount++;
		String baseUrl = String.format("http://localhost:%d/isAlive?", replicaPort);
		ResponseEntity<String> response;
		try {
			response = restTemplate.exchange(
					baseUrl,
					HttpMethod.GET,
					request,
					String.class);
			log.info(String.format("heartbeat: %d  replicaId: %d  status: ALIVE", heartbeatCount, replicaId));
			System.out.println(response.getBody());
			String[] readyPrimary= response.getBody().toString().split(",");
			boolean isReady = Boolean.parseBoolean(readyPrimary[0]);
			boolean isPrimary = Boolean.parseBoolean(readyPrimary[1]);

			System.out.println((isReady? "isReady" : "isNotReady") + " "  + (isPrimary? "isPrimary": "notPrimary"));
			//  dead -> alive
			if (!serverStatus.contains(replicaPort)) {
				serverStatus.add(replicaPort);
				primary = isPrimary;
				ready = isReady;
				log.info("LFD adds replica: S" + replicaId);
			}

			// backup -> primary
			else if (serverStatus.contains(replicaPort) && primary != isPrimary) {
				primary = isPrimary;
				log.info("S" + replicaId + " was changed from Backup to Primary");
			}

			//not ready => ready
			else if (serverStatus.contains(replicaPort) && ready != isReady) {
				ready = isReady;
				log.info("Replica: S" + replicaId + " is READY");
			} else return;

			String GfdUrl = String.format("http://localhost:%d/update?replica=%d&isactive=%b&isprimary=%b&isready=%b&checkpointfreq=%d&status=alive",
					GfdPort,
					replicaId,
					isActive,
					primary,
					isReady,
					checkpointFreq);

			restTemplate.exchange(
					GfdUrl,
					HttpMethod.POST,
					request,
					String.class);

		} catch (Exception e) {
			log.info(String.format("heartbeat: %d  replica: S%d  status: DEAD", heartbeatCount, replicaId));
			if (serverStatus.contains(replicaPort)) {
				//the server just died
				String GfdUrl = String.format("http://localhost:%d/update?replica=%d&isactive=%b&isprimary=%b&isready=%b&checkpointfreq=%d&status=dead",
						GfdPort, replicaId, isActive, primary, false, checkpointFreq);
				restTemplate.exchange(
						GfdUrl,
						HttpMethod.POST,
						request,
						String.class);
				log.info("LFD deletes replica: S" + replicaId);
				serverStatus.remove(replicaPort);
			}
		}
	}
}
