package com.hhplus.ecommerce;

import com.hhplus.ecommerce.config.TestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class EcommerceApplicationTests {

	@Test
	void contextLoads() {
	}

}
