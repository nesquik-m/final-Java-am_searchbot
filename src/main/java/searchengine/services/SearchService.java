package searchengine.services;

import org.springframework.http.ResponseEntity;

import java.io.IOException;

public interface SearchService {
    ResponseEntity<Object> search(String query, String siteUrl, int offset, int limit) throws IOException;
}
