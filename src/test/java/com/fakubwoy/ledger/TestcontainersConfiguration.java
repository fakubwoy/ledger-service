package com.fakubwoy.ledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

// One Postgres and one Kafka container per test JVM, wired into Spring via @ServiceConnection.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgres() {
		return new PostgreSQLContainer<>("postgres:17");
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafka() {
		return new KafkaContainer("apache/kafka-native:3.9.1");
	}
}
