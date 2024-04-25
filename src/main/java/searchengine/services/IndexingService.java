package searchengine.services;
import searchengine.config.Site;

import java.util.concurrent.atomic.AtomicBoolean;

public interface IndexingService {
    void startIndexing(AtomicBoolean indexingProcessing) throws InterruptedException;
    void indexPage(Site site, String link, AtomicBoolean indexingProcessing);

}
