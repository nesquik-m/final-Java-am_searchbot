package searchengine.services.Impl;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.responses.FalseResponse;
import searchengine.responses.SearchData;
import searchengine.responses.SearchResponse;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.SearchService;
import searchengine.utils.LemmasAndIndexes;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private static final int COUNT_CHAR_IN_THE_SNIPPET = 270;
    private static final String ELLIPSIS = "...";
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private Set<String> lemmasSet;

    @Override
    public ResponseEntity<Object> search(String query, String site, int offset, int limit) throws IOException {

        List<String> searchSitesList = createSearchList(site);

        if (!sitesExistWithStatusIndexed(searchSitesList)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new FalseResponse("Не все сайты проиндексированы"));
        }

        lemmasSet = lemmasInQuery(query);

        Map<PageEntity, Float> pagesAndAbsRelevanceMap = getPagesAndAbsRelevanceMap(searchSitesList, lemmasSet);
        if (pagesAndAbsRelevanceMap.isEmpty()) {
            return ResponseEntity.ok(new SearchResponse());
        }

        float maxAbsRel = Collections.max(pagesAndAbsRelevanceMap.values());

        Map<PageEntity, Float> pagesAndRelRelevanceMap = new HashMap<>();
        for (Map.Entry<PageEntity, Float> pageEntity : pagesAndAbsRelevanceMap.entrySet()) {
            pagesAndRelRelevanceMap.put(pageEntity.getKey(), pageEntity.getValue() / maxAbsRel);
        }

        Map<PageEntity, Float> sortedPagesAndRelRelevanceMap = pagesAndRelRelevanceMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        SearchResponse searchResponse = getSearchResponse(sortedPagesAndRelRelevanceMap);

        return ResponseEntity.ok(searchResponse);
    }

    private SearchResponse getSearchResponse(Map<PageEntity, Float> sortedPagesAndRelRelevanceMap) throws IOException {
        List<SearchData> searchDataList = new ArrayList<>();
        for (Map.Entry<PageEntity, Float> pageEntity : sortedPagesAndRelRelevanceMap.entrySet()) {

            SearchData searchData = new SearchData();
            searchData.setSite(pageEntity.getKey().getSiteEntity().getUrl());
            searchData.setSiteName(pageEntity.getKey().getSiteEntity().getName());
            searchData.setUri(pageEntity.getKey().getPath());
            searchData.setTitle(getTitlePageEntity(pageEntity.getKey()));
            searchData.setSnippet(getSnippet(pageEntity.getKey().getContent()));
            searchData.setRelevance(pageEntity.getValue());
            searchDataList.add(searchData);
        }

        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setResult(true);
        searchResponse.setCount(sortedPagesAndRelRelevanceMap.size());
        searchResponse.setData(searchDataList);
        return searchResponse;
    }

    private String getSnippet(String text) throws IOException {

        String cleanText = insertingTags(text);
        StringBuilder snippet = new StringBuilder();

        int lengthText = cleanText.length();
        int firstTag = cleanText.indexOf("<b>");
        int startPosition = 0;

        String textBeforeTheFirstTag = cleanText.substring(0, firstTag);
        for (int i = textBeforeTheFirstTag.length() - 1; i >= 0; i--) {
            char ch1 = textBeforeTheFirstTag.charAt(i);
            char ch2 = textBeforeTheFirstTag.charAt(i - 1);
            if (Character.isUpperCase(ch1) && ch2 == ' ') {
                startPosition = textBeforeTheFirstTag.lastIndexOf(ch1);
                break;
            }
        }
        int maxEndPosition = startPosition + COUNT_CHAR_IN_THE_SNIPPET;
        int endPosition = Math.min(maxEndPosition, lengthText);
        String snippetText = cleanText.substring(startPosition, endPosition);
        snippet.append(snippetText).append(ELLIPSIS);

        return snippet.toString();
    }

    private String insertingTags(String text) throws IOException {

        String textWithoutTags = Jsoup.parse(text).body().text();
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();

        String[] words = textWithoutTags.replaceAll("[^А-я\\s]", "").trim().split("\\s+");

        for (String word : words) {
            List<String> wordNormalForms = luceneMorph.getNormalForms(word.toLowerCase());
            for (String normalForm : wordNormalForms) {
                textWithoutTags = getReplaceWord(word, normalForm, textWithoutTags);
            }
        }
        return textWithoutTags;
    }

    private String getReplaceWord(String word, String normalForm, String textWithoutTags) {

        for (String queryWord : lemmasSet) {
            if (!normalForm.equals(queryWord)) {
                continue;
            }
            String regex = " " + word + "\\b";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(textWithoutTags);
            while (matcher.find()) {
                String newWord = " <b>" + word + "</b>";
                textWithoutTags = textWithoutTags.replace(matcher.group(), newWord);
            }
        }
        return textWithoutTags;
    }

    private Map<PageEntity, Float> getPagesAndAbsRelevanceMap(List<String> searchSitesList, Set<String> lemmasSet) {
        Map<PageEntity, Float> pagesAndAbsRelevanceMap = new HashMap<>();

        for (String url : searchSitesList) {
            SiteEntity siteEntity = siteRepository.findByUrl(url).orElseThrow();
            List<LemmaEntity> lemmaEntitySet = searchLemmasOnSites(siteEntity, lemmasSet);

            if (lemmaEntitySet.isEmpty() || lemmaEntitySet.size() != lemmasSet.size()) {
                continue;
            }

            List<IndexEntity> indexEntityList = indexRepository.findAllByLemmaEntity(lemmaEntitySet.get(0));
            Map<PageEntity, Float> pageEntityMap = new ConcurrentHashMap<>();
            for (IndexEntity indexEntity : indexEntityList) {
                pageEntityMap.put(indexEntity.getPageEntity(), indexEntity.getRank());
            }

            pagesAndAbsRelevanceMap.putAll(getRelevantPagesForTheQuery(pageEntityMap, lemmaEntitySet));
        }
        return pagesAndAbsRelevanceMap;
    }

    private Map<PageEntity, Float> getRelevantPagesForTheQuery(Map<PageEntity, Float> pageEntityMap, List<LemmaEntity> lemmaEntitySet) {

        for (Map.Entry<PageEntity, Float> pageEntity : pageEntityMap.entrySet()) {
            for (int i = 1; i < lemmaEntitySet.size(); i++) {
                Optional<IndexEntity> optionalIndexEntity = indexRepository.findByPageEntityAndLemmaEntity(pageEntity.getKey(), lemmaEntitySet.get(i));
                if (optionalIndexEntity.isEmpty()) {
                    pageEntityMap.remove(pageEntity.getKey());
                    continue;
                }
                float newRank = pageEntity.getValue() + optionalIndexEntity.get().getRank();
                pageEntityMap.put(pageEntity.getKey(), newRank);
            }
        }
        return pageEntityMap;
    }

    private String getTitlePageEntity(PageEntity pageEntity) {
        Document doc;
        String url = pageEntity.getPath().startsWith("/") ?
                pageEntity.getSiteEntity().getUrl() + pageEntity.getPath() :
                pageEntity.getPath();
        try {
            Thread.sleep(150);
            doc = Jsoup.connect(url).timeout(6 * 10000).get();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return doc.title();
    }

    private List<LemmaEntity> searchLemmasOnSites(SiteEntity siteEntity, Set<String> lemmasSet) {

        return lemmasSet.stream()
                .map(lemma -> lemmaRepository.findBySiteIdAndLemma(siteEntity, lemma))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(lemmaEntity -> lemmaEntity.getFrequency() < 50)
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .toList();
    }

    private List<String> createSearchList(String site) {
        return site == null ?
                sites.getSites().stream().map(Site::getUrl).toList() :
                List.of(site);
    }

    private Set<String> lemmasInQuery(String query) throws IOException {
        LemmasAndIndexes lemmasAndIndexes = new LemmasAndIndexes();
        return lemmasAndIndexes.collectLemmas(query).keySet();
    }

    private boolean sitesExistWithStatusIndexed(List<String> searchSitesList) {
        return searchSitesList.stream()
                .allMatch(url -> siteRepository.findByUrl(url).isPresent() &&
                        siteRepository.findByUrl(url).get().getStatus().equals(StatusType.INDEXED));
    }
}
