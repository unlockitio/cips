package io.unlockit.application.did.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DemoFaultFilterTest {
    @Test
    void recognizesOnlyTheSixFamilyRoots() {
        assertEquals("dids", DemoFaultFilter.family("v1/dids"));
        assertEquals("dids", DemoFaultFilter.family("v1/dids/did%3Acanton%3ADSO%3A%3Aabc"));
        assertEquals("assets", DemoFaultFilter.family("v1/assets"));
        assertEquals("assets", DemoFaultFilter.family("/v1/assets"));
        assertEquals("dids", DemoFaultFilter.family("/v1/dids/did%3Acanton%3ADSO%3A%3Aabc"));
        assertNull(DemoFaultFilter.family("/q/health/ready"));
        assertNull(DemoFaultFilter.family("q/health/ready"));
        assertNull(DemoFaultFilter.family("v1/registered-dids"));
        assertNull(DemoFaultFilter.family("v1/unknown"));
    }
}
