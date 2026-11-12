package org.ztbridge.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class BridgeHealthTest {
    @Test public void receivedDataOutweighsOfflineEvent() {
        BridgeHealth status = BridgeHealth.evaluate(false, false, 10000, 10001);
        assertEquals("traffic", status.phase); assertTrue(status.detail.contains("offline"));
    }
    @Test public void offlineWithoutTrafficIsUnconfirmedInsteadOfClaimingInternetLoss() {
        BridgeHealth status = BridgeHealth.evaluate(false, true, -1, 10001);
        assertEquals("degraded", status.phase); assertTrue(status.detail.contains("не подтверждена"));
    }
    @Test public void readinessRecoversWithoutAnotherOnlineCallback() {
        assertEquals("degraded", BridgeHealth.evaluate(false, true, -1, 10000).phase);
        assertEquals("ready", BridgeHealth.evaluate(true, true, -1, 11000).phase);
    }
    @Test public void staleTrafficDoesNotProveCurrentReachability() {
        assertEquals("degraded", BridgeHealth.evaluate(false, true, 10000, 20000).phase);
        assertEquals("degraded", BridgeHealth.evaluate(true, false, -1, 20000).phase);
        assertEquals("degraded", BridgeHealth.evaluate(false, true, 30000, 20000).phase);
    }
}
