package br.org.fju.rally.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.PropertyNamingStrategies;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {
    @Bean
    JsonMapperBuilderCustomizer configuracaoJackson() {
        return construtor -> construtor
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .defaultTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}
