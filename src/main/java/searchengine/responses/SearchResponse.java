package searchengine.responses;

import lombok.Data;


import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResponse {

    private boolean result = true;
    private int count;
    private List<SearchData> data = new ArrayList<>();
}
