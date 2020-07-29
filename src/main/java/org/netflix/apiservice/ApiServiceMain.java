package org.netflix.apiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.spotify.apollo.Environment;
import com.spotify.apollo.RequestContext;
import com.spotify.apollo.Response;
import com.spotify.apollo.core.Service;
import com.spotify.apollo.httpservice.HttpService;
import com.spotify.apollo.httpservice.LoadingException;
import com.spotify.apollo.route.Route;
import com.typesafe.config.Config;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okio.ByteString;
import org.netflix.apiservice.cache.InMemoryCache;
import org.netflix.apiservice.resources.ApiReadCacheResource;
import org.netflix.apiservice.resources.ProxyHandler;
import org.netflix.apiservice.resources.ViewResource;
import org.netflix.apiservice.scheduler.PollNetflixData;
import org.netflix.apiservice.utils.RequestHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ApiServiceMain {

  private static final Logger LOG = LoggerFactory.getLogger(ApiServiceMain.class);
  static final String SERVICE_NAME = "api-read-cache";

  private ApiServiceMain() {
  }

  /**
   * Runs the application.
   *
   * @param args command-line arguments
   */
  public static void main(final String... args) throws LoadingException {
    final Service service = HttpService.usingAppInit(ApiServiceMain::configure, SERVICE_NAME)
        .build();
    HttpService.boot(service, args);
  }

  @VisibleForTesting
  static void configure(final Environment environment) {
    final Config config = environment.config();
    final ObjectMapper objectMapper = new ObjectMapper();

    final String forwardingUri = config.getString("forwarding_uri");
    final String githubApiToken = config.getString("github_api_token");
    final Long cacheSize = config.getLong("cache.size");
    final Long cacheExpiration = config.getLong("cache.expiration");
    final long pollDelayNs = config.getDuration("poll_delay", TimeUnit.NANOSECONDS);
    final String resultsPerPage = config.getString("results_per_page");

    final ProxyHandler proxyHandler = new ProxyHandler(forwardingUri, githubApiToken, resultsPerPage);

    final InMemoryCache inMemoryCache =
        new InMemoryCache(cacheSize, cacheExpiration, objectMapper);

    final RequestHelpers requestHelpers = new RequestHelpers(environment.client());

    final ApiReadCacheResource readCacheResource =
        new ApiReadCacheResource(proxyHandler, requestHelpers, inMemoryCache);
    final ViewResource viewResource = new ViewResource(objectMapper, inMemoryCache);

    // set of endpoints to poll periodically and cache.
    final List<String> periodicallyCachedEndpoints =
        config.getStringList("periodically_cached_endpoints");

    final PollNetflixData pollNetflixData =
        new PollNetflixData(proxyHandler, requestHelpers, pollDelayNs, inMemoryCache);

    periodicallyCachedEndpoints.parallelStream()
        .forEach(
            uri -> {
              final String executorName = SERVICE_NAME + uri;
              // dedicated scheduled executor to periodically refresh the cache
              final ScheduledExecutorService mainExecutor =
                  Executors.newSingleThreadScheduledExecutor(
                      new ThreadFactoryBuilder().setNameFormat(executorName).build());
              pollNetflixData.pollAndUpdateCachePeriodically(mainExecutor, uri);
            }
        );

    environment.routingEngine()
        .registerRoutes(readCacheResource.routes())
        .registerRoutes(viewResource.routes())
        .registerRoute(Route.sync("GET", "/healthcheck", ApiServiceMain::healthCheck));
  }

  public static Response<ByteString> healthCheck(RequestContext requestContext) {
    return Response.ok().withPayload(ByteString.encodeUtf8("health check succeeded!"));
  }

}
