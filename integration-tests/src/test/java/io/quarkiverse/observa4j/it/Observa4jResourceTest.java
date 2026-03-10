package io.quarkiverse.observa4j.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class Observa4jResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/observa4j")
                .then()
                .statusCode(200)
                .body(is("Hello observa4j"));
    }
}
