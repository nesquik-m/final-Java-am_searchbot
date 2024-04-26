package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;

@Component
public class LemmasAndIndexes {

    private final LuceneMorphology luceneMorph = new RussianLuceneMorphology();
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public LemmasAndIndexes(SiteRepository siteRepository, LemmaRepository lemmaRepository,
                            IndexRepository indexRepository) throws IOException {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public void creatingLemmasAndIndexes(SiteEntity siteEntity, PageEntity pageEntity) {

        try {
            HashMap<String, Integer> lemmas = collectLemmas(pageEntity.getContent());
            for (Entry<String, Integer> lemma : lemmas.entrySet()) {
                Optional<LemmaEntity> optionalLemmaEntity = lemmaRepository.findBySiteIdAndLemma(siteEntity, lemma.getKey());
                LemmaEntity lemmaEntity;
                if (optionalLemmaEntity.isEmpty()) {
                    lemmaEntity = mapToNewLemmaEntity(siteEntity, lemma.getKey());
                    lemmaRepository.save(lemmaEntity);
                } else {
                    lemmaEntity = optionalLemmaEntity.get();
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                    lemmaRepository.save(lemmaEntity);
                }
                mapAndSaveToNewIndexEntity(pageEntity, lemmaEntity, lemma.getValue());
                siteEntity.setStatusTime(Instant.now());
                siteRepository.save(siteEntity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, Integer> collectLemmas(String text) {
        HashMap<String, Integer> lemmaMap = new HashMap<>();
        String[] words = text.replaceAll("[^А-я\\s]", "").trim().split("\\s+");
        for (String word : words) {

            List<String> wordMorphInfo = luceneMorph.getMorphInfo(word.toLowerCase());
            if (anyWordBaseBelongToParticle(wordMorphInfo)) {
                continue;
            }
            String normalWord = luceneMorph.getNormalForms(word.toLowerCase()).get(0);
            int count = lemmaMap.getOrDefault(normalWord, 0);
            lemmaMap.put(normalWord, count + 1);
        }
        return lemmaMap;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
        return wordBaseForms.stream().anyMatch(word -> Arrays.stream(particlesNames).anyMatch(word::contains));
    }

    private LemmaEntity mapToNewLemmaEntity(SiteEntity siteEntity, String lemma) {
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setSiteEntity(siteEntity);
        lemmaEntity.setLemma(lemma);
        lemmaEntity.setFrequency(1);
        return lemmaEntity;
    }

    private void mapAndSaveToNewIndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, int rank) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPageEntity(pageEntity);
        indexEntity.setLemmaEntity(lemmaEntity);
        indexEntity.setRank(rank);
        indexRepository.save(indexEntity);
    }
}