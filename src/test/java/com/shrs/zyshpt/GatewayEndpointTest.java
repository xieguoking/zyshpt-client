package com.shrs.zyshpt;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GatewayEndpointTest {

    @Test
    public void splitFullHttpUrl() {
        String[] parts = GatewayEndpoint.split("http://172.22.22.13:8081/api-demo");
        assertArrayEquals(new String[]{"http://172.22.22.13:8081/", "api-demo"}, parts);
    }

    @Test
    public void splitTrailingSlash() {
        String[] parts = GatewayEndpoint.split("https://gateway.example.com/api/");
        assertEquals("https://gateway.example.com/", parts[0]);
        assertEquals("api", parts[1]);
    }

    @Test
    public void splitNestedPath() {
        String[] parts = GatewayEndpoint.split("https://gateway.example.com/open/gateway/abc123");
        assertEquals("https://gateway.example.com/open/gateway/", parts[0]);
        assertEquals("abc123", parts[1]);
    }

    @Test
    public void rejectUrlWithoutApiId() {
        assertInvalid("http://gateway.example.com");
        assertInvalid("http://gateway.example.com/");
        assertInvalid("");
        assertInvalid(null);
    }

    @Test
    public void rejectUrlWithQuery() {
        assertInvalid("http://gateway.example.com/api?name=1");
    }

    private void assertInvalid(String value) {
        try {
            GatewayEndpoint.split(value);
            fail("应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
