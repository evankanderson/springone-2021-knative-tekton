package com.example.apitizer;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.spring.http.CloudEventHttpUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class SimpleController {
	static final String brokerUrl = "http://broker-ingress.knative-eventing.svc.cluster.local/default/default";
	static final Logger logger = LoggerFactory.getLogger(SimpleController.class);

	@Autowired
	RestTemplateBuilder builder;

	@GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
	public @ResponseBody String get(HttpServletRequest request) {
		// Record the visit for other functions to react to.
		CloudEvent ce = CloudEventBuilder.fromSpecVersion(SpecVersion.V1).withType("com.example.bite")
				.withId(UUID.randomUUID().toString())
				.withSource(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri())
				.withData("text/plain", request.getRemoteHost().getBytes(StandardCharsets.UTF_8)).build();

		// Send as a binary HTTP request
		RestTemplate client = builder.build();
		ResponseEntity<byte[]> sentEvent = client.exchange(
				RequestEntity.post(brokerUrl).headers(CloudEventHttpUtils.toHttp(ce)).body(ce.getData().toBytes()),
				byte[].class);
		if (sentEvent.getStatusCode().isError()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store event");
		}
		logger.info("Submitted to {}, got {}", brokerUrl, sentEvent.getStatusCode().value());

		return "done";
	}

}
