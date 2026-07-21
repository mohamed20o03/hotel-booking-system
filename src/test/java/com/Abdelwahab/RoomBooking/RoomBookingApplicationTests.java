package com.Abdelwahab.RoomBooking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Application smoke test. @SpringBootTest boots the full application context — every
 * bean, auto-configuration, the security chain and the JPA layer wired against the
 * H2 test datasource — with no slice narrowing and nothing mocked. It is the
 * broadest integration guard in the suite: a wiring error (a missing bean, a bad
 * property, a schema mismatch) fails here before any narrower test runs.
 */
@SpringBootTest
class RoomBookingApplicationTests extends AbstractIntegrationTest {

	/**
	 * Given the full application configuration;
	 * when the Spring context is started; then it loads without error — the assertion is
	 * implicit in the context coming up.
	 */
	@Test
	void contextLoads() {
	}

}
