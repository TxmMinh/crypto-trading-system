package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${crypto.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${crypto.http.read-timeout-ms:5000}")
    private int readTimeoutMs;

    /**
     * RestTemplate with explicit timeouts.
     * Prevents the scheduler from hanging indefinitely if an exchange API is slow.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
