package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.responses.FalseResponse;
import searchengine.responses.TrueResponse;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final AtomicBoolean indexingProcessing = new AtomicBoolean(false);
    private final SitesList sites;
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() {

        if (indexingProcessing.get()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new FalseResponse("Индексация уже запущена"));
        }
        try {
            indexingService.startIndexing(indexingProcessing);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.status(HttpStatus.OK).body(new TrueResponse());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {

        if (!indexingProcessing.get()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new FalseResponse("Индексация не запущена"));
        }
        indexingProcessing.set(false);
        return ResponseEntity.status(HttpStatus.OK).body(new TrueResponse());
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url) {

        for (Site site : sites.getSites()) {
            if (url.startsWith(site.getUrl())) {
                indexingService.indexPage(site, url, indexingProcessing);
                return ResponseEntity.status(HttpStatus.OK).body(new TrueResponse());
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new FalseResponse("Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
    }

    @GetMapping("/search")
    public ResponseEntity<Object> search(@RequestParam String query,
                                         @RequestParam(required = false) String site,
                                         @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
                                         @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) throws IOException {
        if (query.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new FalseResponse("Задан пустой поисковый запрос"));
        }
        return searchService.search(query, site, offset, limit);
    }
}
