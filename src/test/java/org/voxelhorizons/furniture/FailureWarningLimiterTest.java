package org.voxelhorizons.furniture;

import org.junit.Test;

import static org.junit.Assert.*;

public class FailureWarningLimiterTest {
    @Test public void firstFailureIsReportedAndIdenticalRetriesAreLimited() {
        FailureWarningLimiter limiter = new FailureWarningLimiter(60_000L);
        assertTrue(limiter.shouldWarn("revision 3: bad worth", 1_000L));
        assertFalse(limiter.shouldWarn("revision 3: bad worth", 1_001L));
        assertFalse(limiter.shouldWarn("revision 3: bad worth", 60_999L));
        assertTrue(limiter.shouldWarn("revision 3: bad worth", 61_000L));
        assertFalse(limiter.shouldWarn("revision 3: bad worth", 61_001L));
    }

    @Test public void changedFailureOrRevisionReportsImmediately() {
        FailureWarningLimiter limiter = new FailureWarningLimiter(60_000L);
        assertTrue(limiter.shouldWarn("revision 3: bad worth", 1_000L));
        assertTrue(limiter.shouldWarn("revision 4: bad worth", 1_001L));
        assertTrue(limiter.shouldWarn("revision 4: invalid renderer", 1_002L));
    }

    @Test public void successResetsLimiterAndClockRollbackIsHandled() {
        FailureWarningLimiter limiter = new FailureWarningLimiter(60_000L);
        assertTrue(limiter.shouldWarn("bad worth", 100_000L));
        limiter.reset();
        assertTrue(limiter.shouldWarn("bad worth", 100_001L));
        assertTrue(limiter.shouldWarn("bad worth", 90_000L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveInterval() {
        new FailureWarningLimiter(0);
    }
}
