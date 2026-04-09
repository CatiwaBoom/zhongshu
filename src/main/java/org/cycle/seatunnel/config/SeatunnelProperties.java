package org.cycle.seatunnel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "seatunnel")
public class SeatunnelProperties {
    private String home;
    private String workDir;
    private String execMode;
    private String clusterName;
    private String clientAddress;
    private String restBaseUrl;
}
