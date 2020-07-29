package org.netflix.apiservice.resources;

import static com.spotify.apollo.route.Route.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableList;
import com.spotify.apollo.RequestContext;
import com.spotify.apollo.Response;
import com.spotify.apollo.route.AsyncHandler;
import com.spotify.apollo.route.JsonSerializerMiddlewares;
import com.spotify.apollo.route.Route;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import okio.ByteString;
import org.netflix.apiservice.cache.InMemoryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewResource {

  private static final Logger LOG = LoggerFactory.getLogger(ApiReadCacheResource.class);
  private static final String GET = "GET";
  private final ObjectMapper objectMapper;
  private final ObjectWriter objectWriter;
  private final InMemoryCache inMemoryCache;

  public ViewResource(ObjectMapper objectMapper, final InMemoryCache inMemoryCache) {
    this.objectMapper = objectMapper;
    this.objectWriter = objectMapper.writer();
    this.inMemoryCache = inMemoryCache;
  }

  /**
   * Routes for getting the top N repos by the given criteria .
   */
  public Stream<Route<AsyncHandler<Response<ByteString>>>> routes() {
    return Stream.of(
        async(GET, "/view/top/<N>/forks", this::getTopNreposByForks)
            .withMiddleware(JsonSerializerMiddlewares.jsonSerializeResponse(objectWriter))
            .withDocString("top N repos by forks", "top N repos by forks"),
        async(GET, "/view/top/<N>/stars", this::getTopNreposByStars)
            .withMiddleware(JsonSerializerMiddlewares.jsonSerializeResponse(objectWriter))
            .withDocString("top N repos by stars", "top N repos by stars"),
        async(GET, "/view/top/<N>/open_issues", this::getTopNreposByOpenIssues)
            .withMiddleware(JsonSerializerMiddlewares.jsonSerializeResponse(objectWriter))
            .withDocString("top N repos by open issues", "top N repos by open issues"),
        async(GET, "/view/top/<N>/last_updated", this::getTopNreposByLastUpdated)
            .withMiddleware(JsonSerializerMiddlewares.jsonSerializeResponse(objectWriter))
            .withDocString("top N repos by last updated timestamp", "top N repos by last updated timestamp"));
  }

  private CompletableFuture<Response<List<ImmutableList<? extends Serializable>>>> getTopNreposByForks(
      final RequestContext requestContext) {
    final int topForks = Integer.parseInt(requestContext.pathArgs().get("N"));
    return CompletableFuture
        .completedFuture(Response.forPayload(inMemoryCache.getTopNForks(topForks)));
  }

  private CompletableFuture<Response<List<ImmutableList<? extends Serializable>>>> getTopNreposByStars(
      final RequestContext requestContext) {
    final int topStars = Integer.parseInt(requestContext.pathArgs().get("N"));
    return CompletableFuture
        .completedFuture(Response.forPayload(inMemoryCache.getTopNStars(topStars)));
  }

  private CompletableFuture<Response<List<ImmutableList<? extends Serializable>>>> getTopNreposByOpenIssues(
      final RequestContext requestContext) {
    final int topOpenIssues = Integer.parseInt(requestContext.pathArgs().get("N"));
    return CompletableFuture
        .completedFuture(Response.forPayload(inMemoryCache.getTopNOpenIssues(topOpenIssues)));
  }

  private CompletableFuture<Response<List<ImmutableList<String>>>> getTopNreposByLastUpdated(
      final RequestContext requestContext) {
    final int topForks = Integer.parseInt(requestContext.pathArgs().get("N"));
    return CompletableFuture
        .completedFuture(Response.forPayload(inMemoryCache.getTopNLastUpdated(topForks)));
  }

}
