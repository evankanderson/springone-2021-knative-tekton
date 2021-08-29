package com.example.apitizer;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.spring.http.CloudEventHttpUtils;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class SimpleController {
	static final String brokerUrl = "http://broker-ingress.knative-eventing.svc.cluster.local/default/default";
	static final Logger logger = LoggerFactory.getLogger(SimpleController.class);

	@Autowired
	RestTemplateBuilder builder;

	private static Random random = new Random();

	@GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
	public @ResponseBody String get(HttpServletRequest request) throws IOException {
		StopWatch watch = new StopWatch();
		watch.start();
		ApiResponse.Joke attrs = getJoke();
		watch.stop();
		double elapsed = watch.getTotalTimeSeconds();

		// Record the visit for other functions to react to.
		CloudEvent ce = CloudEventBuilder.fromSpecVersion(SpecVersion.V1).withType("com.example.bite")
				.withId(UUID.randomUUID().toString())
				.withSource(ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri())
				.withExtension("apielapsedsec", String.valueOf(elapsed))
				.withData("text/plain", request.getRemoteHost().getBytes(StandardCharsets.UTF_8)).build();

		// Send as a binary HTTP request
		RestTemplate client = builder.build();
		try {
			ResponseEntity<byte[]> sentEvent = client.exchange(
					RequestEntity.post(brokerUrl).headers(CloudEventHttpUtils.toHttp(ce)).body(ce.getData().toBytes()),
					byte[].class);
			if (sentEvent.getStatusCode().isError()) {
				logger.info("Unable to post event to {}, got {}", brokerUrl, sentEvent.getStatusCode().value());
			}
		} catch (RestClientException e) {
			Throwable wrapped = e.getRootCause();
			logger.warn("Failed to send event: {}", wrapped.toString());
		}

		return String.format(">> %s\n\n%s\n\n", attrs.opener, attrs.punchline);
	}

	private ApiResponse.Joke getJoke() throws IOException {
		int joke = random.nextInt(76); // By experimentation
		String query = "https://www.fatherhood.gov/jsonapi/node/dad_jokes?page[offset]={start}&page[limit]=1";
		RestTemplate client = builder.build();
		logger.info("Chose joke {}", joke);
		ApiResponse response = client.getForObject(query, ApiResponse.class, joke);

		return response.data[0].attributes;
	}

	public static class ApiResponse {

		public ApiData[] data;

		public static class ApiData {
			public Joke attributes;
		}

		public static class Joke {
			@JsonProperty("field_joke_opener")
			public String opener;
			@JsonProperty("field_joke_response")
			public String punchline;

			public String toString() {
				return String.format("%s/%s", opener, punchline);
			}
		}
	}

}
