package sv.LFD.controllers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.ZonedDateTime;
import java.io.PrintWriter;

@Controller
@EnableScheduling
@RefreshScope
public class LfdController {
    private Log log = LogFactory.getLog(LfdController.class.getName());

    @Value("${active}")
    boolean isActive;

    @Value("${checkpointFrequency}")
    int checkpointFreq;

    @GetMapping(path = "/isAlive")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    String isAlive() {
        return String.format("ALIVE");
    }

    //relaunch Replica
    @GetMapping(path = "/create")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    String create(@RequestParam("replica") int replica, @RequestParam("isactive") boolean isActive,
                  @RequestParam("isprimary") boolean isPrimary, @RequestParam("checkpointfreq") int checkpointFreq) throws Exception {

        ZonedDateTime zonedDateTime = ZonedDateTime.now();

        // if lfdPort == 8071, then the serverPort = 8081
        int replicaPort = 8080 + replica;

        //TODO: change jar file address to be the AWS EC2 DNS
        String command = String.format("java -Dserver.port=%d -DcheckpointFrequency=%d" +
                " -Dactive=%b -Dprimary=%b -DisReady=%b -DreplicaId=%d -jar" +
                " /Users/yvonnexu/CMU_Summer_2020/18749_ds/project/antiFault/server/target/warm_passive_server-0.0.1-SNAPSHOT.jar",
                replicaPort, checkpointFreq, isActive, isPrimary, false, replica);
        String scriptFilePath = "server/target/lfd.sh";

        PrintWriter writer = new PrintWriter(scriptFilePath, "UTF-8");
        writer.println(command);
        writer.close();

        try {
            Runtime.getRuntime().exec("chmod u+x " + scriptFilePath);
            Runtime.getRuntime().exec("/usr/bin/open -a Terminal " + scriptFilePath);
            log.info(String.format("time: %s created S%d (Port: %d)", zonedDateTime, replica, replicaPort));
            return "S"  + replica + " created and listens on " + replicaPort + " at " + zonedDateTime;
        } catch (Exception e) {
            log.info("port: "+replicaPort +" is not available");
        }
        return "fail to create S" + replica +" at port: " + replicaPort;
    }
}

