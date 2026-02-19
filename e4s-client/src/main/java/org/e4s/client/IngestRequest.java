package org.e4s.client;

import org.e4s.model.Timestamped;

import java.util.List;

public class IngestRequest {
    private String meterId;
    private List<? extends Timestamped> readings;

    public IngestRequest() {
    }

    public IngestRequest(String meterId, List<? extends Timestamped> readings) {
        this.meterId = meterId;
        this.readings = readings;
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    public List<? extends Timestamped> getReadings() {
        return readings;
    }

    public void setReadings(List<? extends Timestamped> readings) {
        this.readings = readings;
    }
}
