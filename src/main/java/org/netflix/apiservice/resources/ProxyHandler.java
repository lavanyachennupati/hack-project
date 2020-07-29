package org.netflix.apiservice.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.spotify.apollo.Client;
import com.spotify.apollo.Request;
import com.spotify.apollo.RequestContext;
import com.spotify.apollo.Response;
import com.spotify.apollo.route.AsyncHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import okio.ByteString;
import org.json.simple.JSONArray;
import org.netflix.apiservice.utils.PageLinks;

/**
 * Proxies incoming requests to targetUri and returns the result.
 */
public class ProxyHandler implements AsyncHandler<Response<ByteString>> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String targetUri;
  private final String githubApiToken;

  // first page query params.
  private static Map<String, List<String>> queryParams;
  private static final Escaper URL_ESCAPER = UrlEscapers.urlFormParameterEscaper();
  private static final Set<String> PAGINATED_URIS = ImmutableSet.of("orgs/Netflix/repos");


  public ProxyHandler(final String targetUri, final String githubApiToken,
                      final String resultsPerPage) {
    this.targetUri = targetUri;
    this.githubApiToken = githubApiToken;
    this.queryParams = ImmutableMap.of("simple", Arrays.asList("yes"),
        "per_page", Arrays.asList(resultsPerPage),
        "page", Arrays.asList("1"));
  }

  @Override
  public CompletionStage<Response<ByteString>> invoke(final RequestContext requestContext) {
    final Client client = requestContext.requestScopedClient();
    final JSONArray consolidatedArray = new JSONArray();

    try {
      final Response<ByteString> response = client.send(
          buildRequest(requestContext.pathArgs().get("everything"), requestContext.request())
      ).toCompletableFuture().get();

      if(!PAGINATED_URIS.contains(requestContext.request().uri())) {
        return CompletableFuture.completedFuture(response);
      }

      final JSONArray jsonArray = MAPPER
          .readValue(response.payload().orElse(ByteString.EMPTY).string(Charset.forName("UTF-8")),
              JSONArray.class);
      consolidatedArray.addAll(jsonArray);

      PageLinks pageLinks = new PageLinks(response);

      // iterate through all pages and append the response.
      while (pageLinks.getNext() != null) {
        final Request request = Request.forUri(pageLinks.getNext(), "GET")
            .withHeader("Authorization", "token " + githubApiToken);
        final Response<ByteString> paginatedResponse =
            client.send(request).toCompletableFuture().get();
        final JSONArray paginatedJsonArray = MAPPER
            .readValue(paginatedResponse.payload().orElse(ByteString.EMPTY).utf8(),
                JSONArray.class);
        consolidatedArray.addAll(paginatedJsonArray);
        pageLinks = new PageLinks(client.send(request).toCompletableFuture().get());

      }

      final String consolidatedResponse = MAPPER.writeValueAsString(consolidatedArray);
      return CompletableFuture
          .completedFuture(Response.forPayload(ByteString.encodeUtf8(consolidatedResponse)));

    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @VisibleForTesting
  public Request buildRequest(final String path, final Request request) {
    final StringBuilder targetUrl = new StringBuilder(targetUri);

    targetUrl.append(path);
    final Map<String, List<String>> parameters = request.parameters();

    final List<String> args = new ArrayList<>();

    queryParams.entrySet().stream()
        .map(e -> String.format("%s=%s", e.getKey(), URL_ESCAPER.escape(e.getValue().get(0))))
        .forEach(args::add);

    if (!parameters.isEmpty()) {
      parameters.entrySet().stream()
          .map(e -> String.format("%s=%s", e.getKey(), URL_ESCAPER.escape(e.getValue().get(0))))
          .forEach(args::add);
    }

    targetUrl.append("?").append(Joiner.on("&").join(args));

    return Request.forUri(targetUrl.toString(), request.method())
        .withHeader("Authorization", "token " + githubApiToken);
  }

}