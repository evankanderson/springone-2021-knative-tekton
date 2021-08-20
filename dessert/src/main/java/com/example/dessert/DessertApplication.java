package com.example.dessert;

import io.cloudevents.spring.mvc.CloudEventHttpMessageConverter;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class DessertApplication {

	public static void main(String[] args) {
		SpringApplication.run(DessertApplication.class, args);
	}

	@Configuration
	public static class CloudEventHandlerConfiguration implements WebMvcConfigurer {

	    @Override
    	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        	converters.add(0, new CloudEventHttpMessageConverter());
    	}
	}

}
