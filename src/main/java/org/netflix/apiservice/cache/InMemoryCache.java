package org.netflix.apiservice.cache;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryCache {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryCache.class);

  private final Cache<String, ByteString> cache;
  private final ObjectMapper objectMapper;

  private Map<String, Long> sortedByForks;
  private Map<String, Long> sortedByStars;
  private Map<String, Long> sortedByOpenIssues;
  private Map<String, String> sortedByLastUpdated;

  public InMemoryCache(final long cacheSize,
                       final long cacheExpiration,
                       final ObjectMapper objectMapper) {
    this.cache = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterWrite(cacheExpiration, TimeUnit.MINUTES)
        .build();
    this.objectMapper = objectMapper;
  }

  public void addElementToCache(final String key, final ByteString value) {
    cache.put(key, value);
  }

  public ByteString getValueFromCache(final String key) {
    return cache.getIfPresent(key);
  }

  public List<ImmutableList<? extends Serializable>> getTopNForks(int topForks) {
    return getTopNElements(sortedByForks, topForks);
  }

  public List<ImmutableList<? extends Serializable>> getTopNStars(int topStars) {
    return getTopNElements(sortedByStars, topStars);
  }

  public List<ImmutableList<? extends Serializable>> getTopNOpenIssues(int openIssues) {
    return getTopNElements(sortedByOpenIssues, openIssues);
  }

  public List<ImmutableList<String>> getTopNLastUpdated(int lastUpdated) {
    return getTopNLastUpdatedElements(sortedByLastUpdated, lastUpdated);
  }

  public void populateAllViews(final ByteString reposData)
      throws JsonProcessingException {
    final ArrayNode arrayNode =
        objectMapper.readValue(reposData.string(Charset.forName("UTF-8")), ArrayNode.class);

    sortedByForks = populateViews(arrayNode, "forks");
    sortedByOpenIssues = populateViews(arrayNode, "open_issues");
    sortedByStars = populateViews(arrayNode, "stargazers_count");
    sortedByLastUpdated = populateViewsForLastUpdated(arrayNode);

  }

  private Map<String, String> populateViewsForLastUpdated(final ArrayNode arrayNode) {
    final ConcurrentMap<String, String> concurrentMap =
        StreamSupport.stream(arrayNode.spliterator(), true)
            .collect(Collectors.toConcurrentMap(jsonNode -> jsonNode.get("full_name").asText(),
                jsonNode -> jsonNode.get("updated_at").asText()));

    return sortByValues(concurrentMap);
  }

  private Map<String, Long> populateViews(final ArrayNode arrayNode, final String sortByField) {
    final ConcurrentMap<String, Long> concurrentMap =
        StreamSupport.stream(arrayNode.spliterator(), true)
            .collect(Collectors.toConcurrentMap(jsonNode -> jsonNode.get("full_name").asText(),
                jsonNode -> jsonNode.get(sortByField).asLong()));

    return sortByValues(concurrentMap);
  }

  public static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
    final Comparator<K> valueComparator = (k1, k2) -> {
      int compare =
          map.get(k1).compareTo(map.get(k2));
      if (compare == 0) {
        return 1;
      } else {
        return -compare;
      }
    };

    final Map<K, V> sortedByValues =
        new TreeMap<>(valueComparator);
    sortedByValues.putAll(map);
    return sortedByValues;
  }

  private List<ImmutableList<? extends Serializable>> getTopNElements(Map<String, Long> sortedMap, int topN) {
    if (sortedMap.size() < topN) {
      LOG.error("requesting the topN repos, where N is greater than the total number of repos");
      topN = sortedMap.size();
    }
     return sortedMap.entrySet().stream()
        .limit(topN)
        .map(entry -> ImmutableList.of(entry.getKey(), entry.getValue()))
        .collect(toList());
  }

  private List<ImmutableList<String>> getTopNLastUpdatedElements(Map<String, String> sortedMap, int topN) {
    if (sortedMap.size() < topN) {
      LOG.error(
          "requesting the top N last updated repos, where N is greater than the number of repos");
      topN = sortedMap.size();
    }
    return sortedMap.entrySet().stream()
        .limit(topN)
        .map(entry -> ImmutableList.of(entry.getKey(), entry.getValue()))
        .collect(toList());
  }

}
