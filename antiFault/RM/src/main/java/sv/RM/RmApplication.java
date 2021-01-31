package sv.RM;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import sv.RM.controllers.RmController;

@SpringBootApplication
public class RmApplication {

	public static void main(String[] args) {
		Log log = LogFactory.getLog(RmApplication.class.getName());
		log.info(String.format("RM: %d members", RmController.members.size()));
		SpringApplication.run(RmApplication.class, args);
	}


}
