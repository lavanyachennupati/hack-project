package org.netflix.apiservice.utils;

import com.google.common.collect.ImmutableMap;
import com.spotify.apollo.Client;
import com.spotify.apollo.RequestContext;
import com.spotify.apollo.request.RequestContexts;

public class RequestHelpers {

  private static final String GET = "GET";
  private final Client client;

  public RequestHelpers(final Client client) {
    this.client = client;
  }

  public RequestContext createRequest(final String uri) {
    final com.spotify.apollo.Request request = com.spotify.apollo.Request.forUri(uri, GET);
    return RequestContexts.create(request, client, ImmutableMap.of("everything", uri));

  }
}
