package org.e4s.client;

public class CacheStats {
    private long totalEntries;
    private long ownedEntries;
    private long memoryBytes;
    private double memoryMB;
    private double memoryGB;
    private long putCount;
    private long getCount;

    public long getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(long totalEntries) {
        this.totalEntries = totalEntries;
    }

    public long getOwnedEntries() {
        return ownedEntries;
    }

    public void setOwnedEntries(long ownedEntries) {
        this.ownedEntries = ownedEntries;
    }

    public long getMemoryBytes() {
        return memoryBytes;
    }

    public void setMemoryBytes(long memoryBytes) {
        this.memoryBytes = memoryBytes;
    }

    public double getMemoryMB() {
        return memoryMB;
    }

    public void setMemoryMB(double memoryMB) {
        this.memoryMB = memoryMB;
    }

    public double getMemoryGB() {
        return memoryGB;
    }

    public void setMemoryGB(double memoryGB) {
        this.memoryGB = memoryGB;
    }

    public long getPutCount() {
        return putCount;
    }

    public void setPutCount(long putCount) {
        this.putCount = putCount;
    }

    public long getGetCount() {
        return getCount;
    }

    public void setGetCount(long getCount) {
        this.getCount = getCount;
    }
}
