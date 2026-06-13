package com.xeno.crm_backend;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CrmBackendApplication {

	public static void main(String[] args) {
		// Windows JVM reports "Asia/Calcutta", which Postgres in Docker rejects.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(CrmBackendApplication.class, args);
	}

}
