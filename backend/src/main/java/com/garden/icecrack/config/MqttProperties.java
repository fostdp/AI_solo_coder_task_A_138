package com.garden.icecrack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "icecrack-backend";
    private String topic = "sensors/+/data";
    private String username;
    private String password;
    private int qos = 1;
    private boolean enabled = false;
    private int connectionTimeout = 10;
    private int keepAlive = 60;
}
