package br.org.fju.rally.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClienteHttpConfig {

    @Bean
    public RestClient.Builder construtorRestClient() {
        return RestClient.builder();
    }
}
