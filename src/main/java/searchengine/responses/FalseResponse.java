package searchengine.responses;

import lombok.Getter;

@Getter
public class FalseResponse {

    private boolean result = false;
    private String error;

    public FalseResponse(String error) {
        this.error = error;
    }
}
