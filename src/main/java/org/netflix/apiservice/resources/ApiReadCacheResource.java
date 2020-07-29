package org.netflix.apiservice.resources;

import static com.spotify.apollo.route.Route.async;

import com.spotify.apollo.RequestContext;
import com.spotify.apollo.Response;
import com.spotify.apollo.route.AsyncHandler;
import com.spotify.apollo.route.Route;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import okio.ByteString;
import org.netflix.apiservice.cache.InMemoryCache;
import org.netflix.apiservice.utils.RequestHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoints for reading Netflix organization data.
 */
public class ApiReadCacheResource {

  private static final Logger LOG = LoggerFactory.getLogger(ApiReadCacheResource.class);
  private static final String GET = "GET";

  private final ProxyHandler proxyHandler;
  private final RequestHelpers requestHelpers;
  private final InMemoryCache inMemoryCache;

  public ApiReadCacheResource(final ProxyHandler proxyHandler, final RequestHelpers requestHelpers,
                              final InMemoryCache inMemoryCache) {
    this.proxyHandler = proxyHandler;
    this.inMemoryCache = inMemoryCache;
    this.requestHelpers = requestHelpers;
  }

  /**
   * Routes for this resource.
   */
  public Stream<Route<AsyncHandler<Response<ByteString>>>> routes() {
    return Stream.of(
        async(GET, "/orgs/Netflix", this::getDataFromCache)
            .withDocString("Netflix org data", "get and cache Netflix org data"),
        async(GET, "/orgs/Netflix/members", this::getDataFromCache)
            .withDocString("Netflix members data", "get and cache Netflix members data"),
        async(GET, "/orgs/Netflix/repos", this::getDataFromCache)
            .withDocString("Netflix repos data", "get and cache Netflix repos data"),
        async(GET, "/", this::getDataFromCache)
            .withDocString("api.github.com data", "get and cache api.github.com data"),
        async(GET, "/<everything:path>", this::getData)
            .withDocString("forwards request to api.github.com",
                "forwards request to api.github.com"));
  }

  private CompletionStage<Response<ByteString>> getData(RequestContext requestContext) {
    return proxyHandler.invoke(requestContext);
  }

  private CompletionStage<Response<ByteString>> getDataFromCache(RequestContext requestContext) {
    final String uri = requestContext.request().uri().substring(1);
    final ByteString valueFromCache = inMemoryCache.getValueFromCache(uri);

    if(valueFromCache != null) {
      LOG.info("data for uri:{} read from cache", uri);
      return CompletableFuture.completedFuture(Response.forPayload(valueFromCache));
    }

    // if not found in cache.
    final RequestContext request = requestHelpers.createRequest(uri);
    final CompletionStage<Response<ByteString>> response = proxyHandler.invoke(request);
    response.thenAccept(r -> inMemoryCache.addElementToCache(uri, r.payload().orElse(ByteString.EMPTY)));

    return response;
  }


}
