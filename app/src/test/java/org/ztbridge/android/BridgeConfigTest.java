package org.ztbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class BridgeConfigTest {
    @Test public void publicDefaultsContainNoPrivateNetworkAndRequireSetup() {
        BridgeConfig empty = BridgeConfig.defaults();
        assertEquals("", empty.networkId); assertEquals("", empty.remoteIp);
        assertEquals(256, empty.connectionLimit);
        try { BridgeConfig.parse(empty.json().toString()); fail("Unconfigured settings must not connect"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Network ID")); }
    }
    @Test public void migratesOldSettingsAndSavesEditableLimit() throws Exception {
        BridgeConfig old = BridgeConfig.parse("{\"networkId\":\"0123456789abcdef\",\"remoteIp\":\"10.0.0.1\",\"localPort\":15000,\"remotePort\":443}");
        assertEquals(256, old.connectionLimit);
        BridgeConfig custom = new BridgeConfig("0123456789abcdef", "10.0.0.1", "15000", "443", "512");
        assertEquals(512, BridgeConfig.parse(custom.json().toString()).connectionLimit);
        for (String invalid : new String[]{"0", "15", "513", "99999999", "-1", "abc"}) {
            try { new BridgeConfig("0123456789abcdef", "10.0.0.1", "15000", "443", invalid); fail(invalid); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("16 до 512")); }
        }
    }
    @Test public void preservesUnsignedNetworkIdAndEditedEndpoints() {
        BridgeConfig original = new BridgeConfig(" ABCDEF0123456789 ", "192.168.4.10", "23456", "443");
        BridgeConfig restored = BridgeConfig.parse(original.json().toString());
        assertEquals("abcdef0123456789", Long.toUnsignedString(restored.network(), 16));
        assertEquals("127.0.0.1:23456", restored.local());
        assertEquals("192.168.4.10:443", restored.remote());
    }
    @Test public void rejectsInvalidNetworkAndEndpointInputs() {
        String[][] invalid = {
            {"0000000000000000", "10.0.0.1", "15000", "443"},
            {"0123456789abcdeg", "10.0.0.1", "15000", "443"},
            {"0123456789abcde", "10.0.0.1", "15000", "443"},
            {"0123456789abcdef", "256.0.0.1", "15000", "443"},
            {"0123456789abcdef", "010.0.0.1", "15000", "443"},
            {"0123456789abcdef", "10.0.0.1.example", "15000", "443"},
            {"0123456789abcdef", "10.0.0.1", "0", "443"},
            {"0123456789abcdef", "10.0.0.1", "65536", "443"},
            {"0123456789abcdef", "10.0.0.1", "15000", "-1"}
        };
        for (String[] args : invalid) {
            try { new BridgeConfig(args[0], args[1], args[2], args[3]); fail(java.util.Arrays.toString(args)); }
            catch (IllegalArgumentException expected) { assertFalse(expected.getMessage().isEmpty()); }
        }
    }
    @Test public void supportsPortBoundaries() {
        BridgeConfig config = new BridgeConfig("0123456789abcdef", "10.0.0.1", "1", "65535");
        assertEquals(1, config.localPort); assertEquals(65535, config.remotePort);
    }
}
