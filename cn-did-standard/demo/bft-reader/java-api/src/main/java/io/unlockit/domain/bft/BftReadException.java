package io.unlockit.domain.bft;

import java.util.List;

public class BftReadException extends RuntimeException {
    private final int status;
    private final String error;
    private final List<SourceObservation> sources;

    public BftReadException(int status, String error, String message) {
        this(status, error, message, List.of());
    }

    public BftReadException(int status, String error, String message, List<SourceObservation> sources) {
        super(message);
        this.status = status;
        this.error = error;
        this.sources = List.copyOf(sources);
    }

    public int status() { return status; }
    public String error() { return error; }
    public List<SourceObservation> sources() { return sources; }
}
