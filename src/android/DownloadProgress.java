package com.vaenow.appupdate.android;

/**
 * Tracks APK download progress and decides when the UI needs an update.
 * <p/>
 * Values are either a percentage in the range 0..100 for a known positive
 * response length, or {@link #INDETERMINATE} when the total size is unknown.
 * Only changed values are published, so at most 102 progress messages are
 * sent per download regardless of the number of reads.
 */
public final class DownloadProgress {
    /* The total size is unknown; show an animated indeterminate bar */
    public static final int INDETERMINATE = -1;
    /* Nothing new to publish */
    public static final int NO_CHANGE = Integer.MIN_VALUE;

    private final long totalBytes;
    private int lastPublished = NO_CHANGE;
    private int publishedCount = 0;

    public DownloadProgress(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public boolean isDeterminate() {
        return totalBytes > 0;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    /* Number of values returned for publication so far */
    public int getPublishedCount() {
        return publishedCount;
    }

    /**
     * Initial value to show when the response size becomes known.
     */
    public int start() {
        return publish(isDeterminate() ? 0 : INDETERMINATE);
    }

    /**
     * @return the value to publish, or {@link #NO_CHANGE} if the UI is already up to date
     */
    public int onBytesRead(long bytesRead) {
        int value = percentage(bytesRead, totalBytes);
        // Never repeat a value or move a determinate bar backwards
        if (value == lastPublished || (value >= 0 && lastPublished > value)) {
            return NO_CHANGE;
        }
        return publish(value);
    }

    /**
     * The download finished successfully, so the transfer is 100% complete
     * regardless of whether its size was known in advance.
     */
    public int complete() {
        if (lastPublished == 100) {
            return NO_CHANGE;
        }
        return publish(100);
    }

    /**
     * @return a percentage in 0..100, or {@link #INDETERMINATE} if totalBytes is not positive
     */
    public static int percentage(long bytesRead, long totalBytes) {
        if (totalBytes <= 0) {
            return INDETERMINATE;
        }
        if (bytesRead <= 0) {
            return 0;
        }
        if (bytesRead >= totalBytes) {
            return 100;
        }
        return (int) Math.min(99L, (long) (bytesRead * 100.0d / totalBytes));
    }

    private int publish(int value) {
        lastPublished = value;
        publishedCount++;
        return value;
    }
}
