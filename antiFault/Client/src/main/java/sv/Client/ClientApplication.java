package sv.Client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(ClientApplication.class, args);
	}

	@Value("${clientId}")
	String clientId;

	@Override
	public void run(String... args) throws Exception {
		int requestId = Integer.parseInt(clientId.substring(clientId.length() - 1)) * 100;
		Log log = LogFactory.getLog(ClientApplication.class.getName());
		MyRequest request = new MyRequest(clientId, requestId);
		int timeInterval = 4000;

		while (true) {
			try {
				request.callRequest();
			} catch (Exception exception) {
				log.info("Server Error");
			}
			Thread.sleep(timeInterval);
		}
	}
}