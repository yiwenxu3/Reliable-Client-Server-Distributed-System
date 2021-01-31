package sv.GFD;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import sv.GFD.controllers.GfdController;

import java.util.HashMap;

@SpringBootApplication
@EnableScheduling
public class GfdApplication {

	public static HashMap<Integer, Boolean> lfds = new HashMap<>();


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

	public static void main(String[] args) {
		Log log = LogFactory.getLog(GfdApplication.class.getName());
		int member_count = GfdController.members.size();
		log.info(String.format("GFD: %d members", member_count));

		// GFD registers with RM (Port: 8060)
		RestTemplate restTemplate = getTemplate();
		HttpEntity request = getRequest();
		String baseUrl = String.format("http://localhost:%d/register?membercount=%d", 8060, member_count);
		try {
			ResponseEntity<String> responseEntity = restTemplate.exchange(
					baseUrl,
					HttpMethod.POST,
					request,
					String.class);
			log.info(String.format("GFD is now registered with RM (port: %d): %s", 8060, responseEntity.getBody()));
		} catch (Exception e) {
			log.info(String.format("GFD failed to register with RM (port: %d)", 8060));
		}

		lfds.put(8071, true);
		lfds.put(8072, true);
		lfds.put(8073, true);
		SpringApplication.run(GfdApplication.class, args);
	}

	Log log = LogFactory.getLog(GfdApplication.class.getName());
	private RestTemplate restTemplate = getTemplate();
	private HttpEntity request = getRequest();

	@Scheduled(fixedRateString ="${heartbeatRate}")
	public void heartbeat() {
		for (int lfdPort : lfds.keySet()) {
			String baseUrl = String.format("http://localhost:%d/isAlive?", lfdPort);
			try {
				ResponseEntity<String> responseEntity = restTemplate.exchange(
						baseUrl,
						HttpMethod.GET,
						request,
						String.class);
				log.info(String.format("heartbeat message: LFD(port: %d) %s", lfdPort, responseEntity.getBody()));
			} catch (Exception e) {
				log.info(String.format("heartbeat message: LFD(port: %d) DIES", lfdPort));
//				if (lfds.get(lfdPort)) {
//					log.info("LFD is removed");
//					lfds.put(lfdPort, false);
//				}
			}
		}
	}
}
