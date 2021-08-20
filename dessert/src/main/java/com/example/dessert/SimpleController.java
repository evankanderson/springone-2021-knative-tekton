package com.example.dessert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SimpleController {

	final static Logger logger = LoggerFactory.getLogger(SimpleController.class);
	final static ObjectMapper mapper = new ObjectMapper().registerModule(JsonFormat.getCloudEventJacksonModule())
			.enable(SerializationFeature.INDENT_OUTPUT);

	@PostMapping(value = "/")
	public @ResponseBody String post(@RequestBody CloudEvent event) {
		try {
			logger.info(mapper.writeValueAsString(event));
		} catch (JsonProcessingException e) {
			logger.info("Failed to format JSON: %s", e.getMessage());
		}

		return "Accepted"
	}

};
