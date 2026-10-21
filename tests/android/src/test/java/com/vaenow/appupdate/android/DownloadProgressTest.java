package com.vaenow.appupdate.android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the progress calculation and throttling only; the ProgressBar/dialog
 * rendering in DownloadHandler requires an Android device or emulator.
 */
public class DownloadProgressTest {
    private static final int READ_SIZE = 1024;

    @Test
    public void knownLengthReportsAdvancingPercentages() {
        long length = 3677576L;
        DownloadProgress tracker = new DownloadProgress(length);
        assertTrue(tracker.isDeterminate());
        assertEquals(0, tracker.start());

        int last = 0;
        int updates = 0;
        for (long count = READ_SIZE; count < length + READ_SIZE; count += READ_SIZE) {
            int value = tracker.onBytesRead(Math.min(count, length));
            if (value != DownloadProgress.NO_CHANGE) {
                assertTrue("progress must increase", value > last);
                assertTrue(value <= 100);
                last = value;
                updates++;
            }
        }
        assertEquals(100, last);
        assertEquals(100, updates);
        assertEquals(DownloadProgress.NO_CHANGE, tracker.complete());
        assertEquals(101, tracker.getPublishedCount());
    }

    @Test
    public void unknownLengthIsIndeterminateUntilCompletion() {
        DownloadProgress tracker = new DownloadProgress(-1L);
        assertFalse(tracker.isDeterminate());
        assertEquals(DownloadProgress.INDETERMINATE, tracker.start());
        for (long count = READ_SIZE; count <= 10L * 1024 * 1024; count += READ_SIZE) {
            assertEquals(DownloadProgress.NO_CHANGE, tracker.onBytesRead(count));
        }
        assertEquals(100, tracker.complete());
        assertEquals(DownloadProgress.NO_CHANGE, tracker.complete());
        assertEquals(2, tracker.getPublishedCount());
    }

    @Test
    public void zeroLengthIsTreatedAsUnknown() {
        DownloadProgress tracker = new DownloadProgress(0L);
        assertFalse(tracker.isDeterminate());
        assertEquals(DownloadProgress.INDETERMINATE, tracker.start());
        assertEquals(DownloadProgress.NO_CHANGE, tracker.onBytesRead(READ_SIZE));
    }

    @Test
    public void percentageIsClampedAndBounded() {
        assertEquals(DownloadProgress.INDETERMINATE, DownloadProgress.percentage(10, -1));
        assertEquals(DownloadProgress.INDETERMINATE, DownloadProgress.percentage(10, 0));
        assertEquals(0, DownloadProgress.percentage(-5, 100));
        assertEquals(0, DownloadProgress.percentage(0, 100));
        assertEquals(0, DownloadProgress.percentage(1, 3677576L));
        assertEquals(50, DownloadProgress.percentage(50, 100));
        assertEquals(99, DownloadProgress.percentage(Long.MAX_VALUE - 1, Long.MAX_VALUE));
        assertEquals(100, DownloadProgress.percentage(100, 100));
        assertEquals(100, DownloadProgress.percentage(250, 100));
        assertEquals(100, DownloadProgress.percentage(Long.MAX_VALUE, 100));
    }

    @Test
    public void progressNeverMovesBackwardsOrRepeats() {
        DownloadProgress tracker = new DownloadProgress(100L);
        assertEquals(0, tracker.start());
        assertEquals(DownloadProgress.NO_CHANGE, tracker.onBytesRead(0));
        assertEquals(40, tracker.onBytesRead(40));
        assertEquals(DownloadProgress.NO_CHANGE, tracker.onBytesRead(40));
        assertEquals(DownloadProgress.NO_CHANGE, tracker.onBytesRead(10));
        assertEquals(100, tracker.onBytesRead(500));
        assertEquals(DownloadProgress.NO_CHANGE, tracker.onBytesRead(600));
    }

    @Test
    public void retryStartsFromFreshState() {
        DownloadProgress first = new DownloadProgress(100L);
        first.start();
        assertEquals(100, first.onBytesRead(100));
        assertEquals(DownloadProgress.NO_CHANGE, first.complete());

        DownloadProgress knownRetry = new DownloadProgress(100L);
        assertEquals(0, knownRetry.start());
        assertEquals(1, knownRetry.onBytesRead(1));

        DownloadProgress unknownRetry = new DownloadProgress(-1L);
        assertEquals(DownloadProgress.INDETERMINATE, unknownRetry.start());
        assertEquals(100, unknownRetry.complete());
    }
}
