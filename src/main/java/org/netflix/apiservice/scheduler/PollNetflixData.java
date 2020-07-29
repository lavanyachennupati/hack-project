package org.netflix.apiservice.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Stopwatch;
import com.spotify.apollo.RequestContext;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okio.ByteString;
import org.netflix.apiservice.ApiServiceMain;
import org.netflix.apiservice.cache.InMemoryCache;
import org.netflix.apiservice.resources.ProxyHandler;
import org.netflix.apiservice.utils.RequestHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollNetflixData {
  private static final Logger LOG = LoggerFactory.getLogger(ApiServiceMain.class);
  private final ProxyHandler proxyHandler;
  private final RequestHelpers requestHelpers;
  private final long pollDelayNs;
  private final InMemoryCache inMemoryCache;
  private static final String NETFLIX_REPOS_DATA_URI = "orgs/Netflix/repos";

  public PollNetflixData(final ProxyHandler proxyHandler,
                         final RequestHelpers requestHelpers,
                         final long pollDelayNs,
                         final InMemoryCache inMemoryCache) {
    this.proxyHandler = proxyHandler;
    this.requestHelpers = requestHelpers;
    this.pollDelayNs = pollDelayNs;
    this.inMemoryCache = inMemoryCache;
  }

  public void pollAndUpdateCachePeriodically(final ScheduledExecutorService executor,
                                             final String uri) {
    executor.scheduleWithFixedDelay(
        () -> {
          try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            //set of all endpoints data to cache.
            getNetflixOrgDataAndCache(uri);
            stopwatch.stop();
            final long elapsedMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            LOG.info("Finished a round of polling for endpoint: {}, took {} ms",
                uri, elapsedMillis);
          } catch (Throwable throwable) {
            LOG.error("Failed polling endpoint: {}", uri, throwable);
          }
        },
        0, pollDelayNs, TimeUnit.NANOSECONDS);
  }

  private void getNetflixOrgDataAndCache(final String uri)
      throws ExecutionException, InterruptedException, JsonProcessingException {
    final RequestContext request = requestHelpers.createRequest(uri);

    // The next execution will start only after the previous one completes.
    // blocking here to get the response payload data as it's being executed
    // inside a single thread that depends on the payload data to continue.
    final Optional<ByteString> payload =
        proxyHandler.invoke(request).toCompletableFuture().get().payload();

    if(payload.isPresent()) {
      inMemoryCache.addElementToCache(uri, payload.get());
    }

    if(NETFLIX_REPOS_DATA_URI.equals(uri)) {
      inMemoryCache.populateAllViews(payload.orElse(ByteString.EMPTY));
    }

  }

}
