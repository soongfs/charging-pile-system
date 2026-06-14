package com.bupt.charging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:sqlite:file:charging-flow-test?mode=memory&cache=shared",
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.sql.init.mode=always"
        }
)
class ChargingFlowIntegrationTest {
    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void userChargingFlowCreatesBillAndUpdatesPileState() {
        String carId = "test-" + System.nanoTime();

        assertSuccess(post("/api/user/register", Map.of(
                "carId", carId,
                "userName", "Integration Test",
                "carCapacity", 80
        )));
        assertSuccess(post("/api/user/set-password", Map.of(
                "carId", carId,
                "password", "secret123"
        )));
        assertSuccess(post("/api/user/login", Map.of(
                "carId", carId,
                "password", "secret123"
        )));

        Map<String, Object> request = assertSuccess(post("/api/charging/request", Map.of(
                "carId", carId,
                "requestAmount", 1,
                "requestMode", 1
        )));
        assertThat(request.get("carState")).isEqualTo("dispatched");
        assertThat(request.get("pileId")).isNotNull();

        assertSuccess(post("/api/charging/start", Map.of("carId", carId)));

        Map<String, Object> progress = assertSuccess(get("/api/charging/progress/" + carId));
        assertThat(progress.get("pileId")).isEqualTo(request.get("pileId"));
        assertThat(progress.get("powerKw")).isEqualTo(30.0);

        assertSuccess(post("/api/charging/end", Map.of("carId", carId)));

        List<?> bills = (List<?>) assertSuccess(get("/api/bill/" + carId + "?date=" + LocalDate.now()));
        assertThat(bills).hasSize(1);

        List<?> piles = (List<?>) assertSuccess(get("/api/admin/pile/state"));
        assertThat(piles).hasSize(5);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        return rest.postForObject(url(path), body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) {
        return rest.getForObject(url(path), Map.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T assertSuccess(Map<String, Object> body) {
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo(0);
        return (T) body.get("data");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
