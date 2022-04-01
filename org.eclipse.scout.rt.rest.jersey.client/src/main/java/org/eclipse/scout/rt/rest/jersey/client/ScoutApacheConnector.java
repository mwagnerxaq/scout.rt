/*
 * Copyright (c) 2010-2021 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 */
package org.eclipse.scout.rt.rest.jersey.client;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultManagedHttpClientConnection;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.impl.io.ChunkedOutputStream;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.TextUtils;
import org.apache.http.util.VersionInfo;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.config.CONFIG;
import org.eclipse.scout.rt.platform.context.RunMonitor;
import org.eclipse.scout.rt.platform.util.BooleanUtility;
import org.eclipse.scout.rt.platform.util.IRegistrationHandle;
import org.eclipse.scout.rt.platform.util.concurrent.ICancellable;
import org.eclipse.scout.rt.rest.client.RestClientProperties;
import org.eclipse.scout.rt.shared.http.proxy.ConfigurableProxySelector;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectionClosingStrategy;
import org.glassfish.jersey.apache.connector.ApacheHttpClientBuilderConfigurator;
import org.glassfish.jersey.apache.connector.LocalizationMessages;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.message.internal.HeaderUtils;
import org.glassfish.jersey.message.internal.OutboundMessageContext;
import org.glassfish.jersey.message.internal.ReaderWriter;
import org.glassfish.jersey.message.internal.Statuses;

/**
 * A {@link Connector} that utilizes the Apache HTTP Client to send and receive HTTP request and responses.
 * <p/>
 * The following properties are only supported at construction of this class:
 * <ul>
 * FIXME PBZ
 * </ul>
 * <p>
 */
public class ScoutApacheConnector implements Connector {

  private static final Logger LOGGER = Logger.getLogger(ScoutApacheConnector.class.getName());
  private static final VersionInfo vi;
  private static final String release;

  static {
    vi = VersionInfo.loadVersionInfo("org.apache.http.client", HttpClientBuilder.class.getClassLoader());
    release = (vi != null) ? vi.getRelease() : VersionInfo.UNAVAILABLE;
  }

  private final CloseableHttpClient client;
  private final CookieStore cookieStore;
  private final boolean preemptiveBasicAuth;
  private final RequestConfig requestConfig;

  /**
   * Create the new Apache HTTP Client connector.
   *
   * @param client
   *          JAX-RS client instance for which the connector is being created.
   * @param config
   *          client configuration.
   */
  ScoutApacheConnector(final Client client, final Configuration config) {
    final Object connectionManager = config.getProperties().get(ApacheClientProperties.CONNECTION_MANAGER);
    if (connectionManager != null) {
      if (!(connectionManager instanceof HttpClientConnectionManager)) {
        LOGGER.log(
            Level.WARNING,
            LocalizationMessages.IGNORING_VALUE_OF_PROPERTY(
                ApacheClientProperties.CONNECTION_MANAGER,
                connectionManager.getClass().getName(),
                HttpClientConnectionManager.class.getName()));
      }
    }

    Object keepAliveStrategy = config.getProperties().get(ApacheClientProperties.KEEPALIVE_STRATEGY);
    if (keepAliveStrategy != null) {
      if (!(keepAliveStrategy instanceof ConnectionKeepAliveStrategy)) {
        LOGGER.log(
            Level.WARNING,
            LocalizationMessages.IGNORING_VALUE_OF_PROPERTY(
                ApacheClientProperties.KEEPALIVE_STRATEGY,
                keepAliveStrategy.getClass().getName(),
                ConnectionKeepAliveStrategy.class.getName()));
        keepAliveStrategy = null;
      }
    }

    Object reuseStrategy = config.getProperties().get(ApacheClientProperties.REUSE_STRATEGY);
    if (reuseStrategy != null) {
      if (!(reuseStrategy instanceof ConnectionReuseStrategy)) {
        LOGGER.log(
            Level.WARNING,
            LocalizationMessages.IGNORING_VALUE_OF_PROPERTY(
                ApacheClientProperties.REUSE_STRATEGY,
                reuseStrategy.getClass().getName(),
                ConnectionReuseStrategy.class.getName()));
        reuseStrategy = null;
      }
    }

    Object reqConfig = config.getProperties().get(ApacheClientProperties.REQUEST_CONFIG);
    if (reqConfig != null) {
      if (!(reqConfig instanceof RequestConfig)) {
        LOGGER.log(
            Level.WARNING,
            LocalizationMessages.IGNORING_VALUE_OF_PROPERTY(
                ApacheClientProperties.REQUEST_CONFIG,
                reqConfig.getClass().getName(),
                RequestConfig.class.getName()));
        reqConfig = null;
      }
    }

    final SSLContext sslContext = client.getSslContext();
    final HttpClientBuilder clientBuilder = HttpClientBuilder.create();

    clientBuilder.setConnectionManager(getConnectionManager(client, config, sslContext));
    clientBuilder.setConnectionManagerShared(
        PropertiesHelper.getValue(config.getProperties(), ApacheClientProperties.CONNECTION_MANAGER_SHARED, false, null));
    clientBuilder.setSSLContext(sslContext);
    if (keepAliveStrategy != null) {
      clientBuilder.setKeepAliveStrategy((ConnectionKeepAliveStrategy) keepAliveStrategy);
    }
    if (reuseStrategy != null) {
      clientBuilder.setConnectionReuseStrategy((ConnectionReuseStrategy) reuseStrategy);
    }

    final RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

    final Object credentialsProvider = config.getProperty(ApacheClientProperties.CREDENTIALS_PROVIDER);
    if (credentialsProvider != null && (credentialsProvider instanceof CredentialsProvider)) {
      clientBuilder.setDefaultCredentialsProvider((CredentialsProvider) credentialsProvider);
    }

    final Object retryHandler = config.getProperties().get(ApacheClientProperties.RETRY_HANDLER);
    if (retryHandler != null && (retryHandler instanceof HttpRequestRetryHandler)) {
      clientBuilder.setRetryHandler((HttpRequestRetryHandler) retryHandler);
    }

    final Object proxyUri;
    proxyUri = config.getProperty(ClientProperties.PROXY_URI);
    if (proxyUri != null) {
      final URI u = getProxyUri(proxyUri);
      final HttpHost proxy = new HttpHost(u.getHost(), u.getPort(), u.getScheme());
      final String userName;
      userName = ClientProperties.getValue(config.getProperties(), ClientProperties.PROXY_USERNAME, String.class);
      if (userName != null) {
        final String password;
        password = ClientProperties.getValue(config.getProperties(), ClientProperties.PROXY_PASSWORD, String.class);

        if (password != null) {
          final CredentialsProvider credsProvider = new BasicCredentialsProvider();
          credsProvider.setCredentials(
              new AuthScope(u.getHost(), u.getPort()),
              new UsernamePasswordCredentials(userName, password));
          clientBuilder.setDefaultCredentialsProvider(credsProvider);
        }
      }
      clientBuilder.setProxy(proxy);

      clientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(BEANS.get(ConfigurableProxySelector.class)));
    }

    final Boolean preemptiveBasicAuthProperty = (Boolean) config.getProperties()
        .get(ApacheClientProperties.PREEMPTIVE_BASIC_AUTHENTICATION);
    this.preemptiveBasicAuth = (preemptiveBasicAuthProperty != null) ? preemptiveBasicAuthProperty : false;

    final boolean ignoreCookies = PropertiesHelper.isProperty(config.getProperties(), ApacheClientProperties.DISABLE_COOKIES);

    if (reqConfig != null) {
      final RequestConfig.Builder reqConfigBuilder = RequestConfig.copy((RequestConfig) reqConfig);
      if (ignoreCookies) {
        reqConfigBuilder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
      }
      requestConfig = reqConfigBuilder.build();
    }
    else {
      if (ignoreCookies) {
        requestConfigBuilder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
      }
      requestConfig = requestConfigBuilder.build();
    }

    if (requestConfig.getCookieSpec() == null || !requestConfig.getCookieSpec().equals(CookieSpecs.IGNORE_COOKIES)) {
      this.cookieStore = new BasicCookieStore();
      clientBuilder.setDefaultCookieStore(cookieStore);
    }
    else {
      this.cookieStore = null;
    }
    clientBuilder.setDefaultRequestConfig(requestConfig);

    LinkedList<Object> contracts = config.getInstances().stream()
        .filter(ApacheHttpClientBuilderConfigurator.class::isInstance)
        .collect(Collectors.toCollection(LinkedList::new));

    HttpClientBuilder configuredBuilder = clientBuilder;
    for (Object configurator : contracts) {
      configuredBuilder = ((ApacheHttpClientBuilderConfigurator) configurator).configure(configuredBuilder);
    }

    this.client = configuredBuilder.build();
  }

  private HttpClientConnectionManager getConnectionManager(final Client client,
      final Configuration config,
      final SSLContext sslContext) {
    final Object cmObject = config.getProperties().get(ApacheClientProperties.CONNECTION_MANAGER);

    // Connection manager from configuration.
    if (cmObject != null) {
      if (cmObject instanceof HttpClientConnectionManager) {
        return (HttpClientConnectionManager) cmObject;
      }
      else {
        LOGGER.log(
            Level.WARNING,
            LocalizationMessages.IGNORING_VALUE_OF_PROPERTY(
                ApacheClientProperties.CONNECTION_MANAGER,
                cmObject.getClass().getName(),
                HttpClientConnectionManager.class.getName()));
      }
    }

    final boolean useSystemProperties =
        PropertiesHelper.isProperty(config.getProperties(), ApacheClientProperties.USE_SYSTEM_PROPERTIES);

    // Create custom connection manager.
    return createConnectionManager(
        client,
        config,
        sslContext,
        useSystemProperties);
  }

  private HttpClientConnectionManager createConnectionManager(
      final Client client,
      final Configuration config,
      final SSLContext sslContext,
      final boolean useSystemProperties) {

    final String[] supportedProtocols = useSystemProperties ? split(
        System.getProperty("https.protocols")) : null;
    final String[] supportedCipherSuites = useSystemProperties ? split(
        System.getProperty("https.cipherSuites")) : null;

    HostnameVerifier hostnameVerifier = client.getHostnameVerifier();

    final LayeredConnectionSocketFactory sslSocketFactory;
    if (sslContext != null) {
      sslSocketFactory = new SSLConnectionSocketFactory(
          sslContext, supportedProtocols, supportedCipherSuites, hostnameVerifier);
    }
    else {
      if (useSystemProperties) {
        sslSocketFactory = new SSLConnectionSocketFactory(
            (SSLSocketFactory) SSLSocketFactory.getDefault(),
            supportedProtocols, supportedCipherSuites, hostnameVerifier);
      }
      else {
        sslSocketFactory = new SSLConnectionSocketFactory(
            SSLContexts.createDefault(),
            hostnameVerifier);
      }
    }

    final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory> create()
        .register("http", PlainConnectionSocketFactory.getSocketFactory())
        .register("https", sslSocketFactory)
        .build();

    final Integer chunkSize = ClientProperties.getValue(config.getProperties(),
        ClientProperties.CHUNKED_ENCODING_SIZE, ClientProperties.DEFAULT_CHUNK_SIZE, Integer.class);

    final PoolingHttpClientConnectionManager connectionManager =
        new PoolingHttpClientConnectionManager(registry, new ScoutApacheConnector.ConnectionFactory(chunkSize));

    if (useSystemProperties) {
      String s = System.getProperty("http.keepAlive", "true");
      if ("true".equalsIgnoreCase(s)) {
        s = System.getProperty("http.maxConnections", "5");
        final int max = Integer.parseInt(s);
        connectionManager.setDefaultMaxPerRoute(max);
        connectionManager.setMaxTotal(2 * max);
      }
    }

    return connectionManager;
  }

  private static String[] split(final String s) {
    if (TextUtils.isBlank(s)) {
      return null;
    }
    return s.split(" *, *");
  }

  /**
   * Get the {@link HttpClient}.
   *
   * @return the {@link HttpClient}.
   */
  @SuppressWarnings("UnusedDeclaration")
  public HttpClient getHttpClient() {
    return client;
  }

  /**
   * Get the {@link CookieStore}.
   *
   * @return the {@link CookieStore} instance or {@code null} when {@value ApacheClientProperties#DISABLE_COOKIES} set
   *         to {@code true}.
   */
  public CookieStore getCookieStore() {
    return cookieStore;
  }

  private static URI getProxyUri(final Object proxy) {
    if (proxy instanceof URI) {
      return (URI) proxy;
    }
    else if (proxy instanceof String) {
      return URI.create((String) proxy);
    }
    else {
      throw new ProcessingException(LocalizationMessages.WRONG_PROXY_URI_TYPE(ClientProperties.PROXY_URI));
    }
  }

  @Override
  public ClientResponse apply(final ClientRequest clientRequest) throws ProcessingException {
    preprocessClientRequest(clientRequest);

    final HttpUriRequest request = getUriHttpRequest(clientRequest);

    // Work around for rare abnormal connection terminations (258238)
    ensureHttpHeaderCloseConnection(clientRequest, request);
    final Map<String, String> clientHeadersSnapshot = writeOutBoundHeaders(clientRequest, request);

    final IRegistrationHandle cancellableHandle = registerCancellable(clientRequest, request);

    try {
      final CloseableHttpResponse response;
      final HttpClientContext context = HttpClientContext.create();
      if (preemptiveBasicAuth) {
        final AuthCache authCache = new BasicAuthCache();
        final BasicScheme basicScheme = new BasicScheme();
        authCache.put(getHost(request), basicScheme);
        context.setAuthCache(authCache);
      }

      // If a request-specific CredentialsProvider exists, use it instead of the default one
      CredentialsProvider credentialsProvider =
          clientRequest.resolveProperty(ApacheClientProperties.CREDENTIALS_PROVIDER, CredentialsProvider.class);
      if (credentialsProvider != null) {
        context.setCredentialsProvider(credentialsProvider);
      }

      response = client.execute(getHost(request), request, context);
      HeaderUtils.checkHeaderChanges(clientHeadersSnapshot, clientRequest.getHeaders(), this.getClass().getName(), clientRequest.getConfiguration());

      final Response.StatusType status = response.getStatusLine().getReasonPhrase() == null
          ? Statuses.from(response.getStatusLine().getStatusCode())
          : Statuses.from(response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());

      final ClientResponse responseContext = new ClientResponse(status, clientRequest);
      final List<URI> redirectLocations = context.getRedirectLocations();
      if (redirectLocations != null && !redirectLocations.isEmpty()) {
        responseContext.setResolvedRequestUri(redirectLocations.get(redirectLocations.size() - 1));
      }

      final Header[] respHeaders = response.getAllHeaders();
      final MultivaluedMap<String, String> headers = responseContext.getHeaders();
      for (final Header header : respHeaders) {
        final String headerName = header.getName();
        List<String> list = headers.get(headerName);
        if (list == null) {
          list = new ArrayList<>();
        }
        list.add(header.getValue());
        headers.put(headerName, list);
      }

      final HttpEntity entity = response.getEntity();

      if (entity != null) {
        if (headers.get(HttpHeaders.CONTENT_LENGTH) == null && entity.getContentLength() >= 0) {
          headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(entity.getContentLength()));
        }

        final Header contentEncoding = entity.getContentEncoding();
        if (headers.get(HttpHeaders.CONTENT_ENCODING) == null && contentEncoding != null) {
          headers.add(HttpHeaders.CONTENT_ENCODING, contentEncoding.getValue());
        }
      }

      try {
        final ScoutApacheConnector.ConnectionClosingMechanism closingMechanism = new ScoutApacheConnector.ConnectionClosingMechanism(clientRequest, request);
        responseContext.setEntityStream(getInputStream(response, closingMechanism));
      }
      catch (final IOException e) {
        LOGGER.log(Level.SEVERE, null, e);
      }

      return responseContext;
    }
    catch (final Exception e) {
      throw new ProcessingException(e);
    }
    finally {
      cancellableHandle.dispose();
    }
  }

  /**
   * Enables cookies if enabled by {@link RestClientProperties#ENABLE_COOKIES} and disables chunked transfer encoding if
   * disabled by {@link RestClientProperties#DISABLE_CHUNKED_TRANSFER_ENCODING}.
   * <p>
   * If corresponding properties are already set, their values are not overridden.
   * <p>
   * Must be called before {@link #getUriHttpRequest(ClientRequest)}.
   */
  protected void preprocessClientRequest(ClientRequest clientRequest) {
    if (!isCookiePropertySet(clientRequest) && !BooleanUtility.nvl(clientRequest.resolveProperty(RestClientProperties.ENABLE_COOKIES, Boolean.class))) {
      clientRequest.setProperty(ApacheClientProperties.DISABLE_COOKIES, true);
    }

    if (clientRequest.getProperty(ClientProperties.REQUEST_ENTITY_PROCESSING) == null && BooleanUtility.nvl(clientRequest.resolveProperty(RestClientProperties.DISABLE_CHUNKED_TRANSFER_ENCODING, Boolean.class))) {
      clientRequest.setProperty(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);
    }
  }

  protected boolean isCookiePropertySet(ClientRequest clientRequest) {
    if (clientRequest.getProperty(ApacheClientProperties.DISABLE_COOKIES) != null) {
      return true;
    }

    RequestConfig requestConfig = (RequestConfig) clientRequest.getConfiguration().getProperty(ApacheClientProperties.REQUEST_CONFIG);
    if (requestConfig == null) {
      return false; // absent -> default request configuration
    }

    return requestConfig.getCookieSpec() != null; // e.g. standard
  }

  /**
   * Adds the HTTP header {@code Connection: close} if {@code RestClientProperties.CONNECTION_CLOSE} is {@code true} or
   * {@link RestEnsureHttpHeaderConnectionCloseProperty} is {@code true} and the given {@code headers} do not contain
   * the key {@code Connection}.
   */
  protected void ensureHttpHeaderCloseConnection(ClientRequest clientRequest, HttpUriRequest httpRequest) {
    boolean closeConnection = BooleanUtility.nvl(clientRequest.resolveProperty(RestClientProperties.CONNECTION_CLOSE, CONFIG.getPropertyValue(RestEnsureHttpHeaderConnectionCloseProperty.class)), true);
    MultivaluedMap<String, Object> headers = clientRequest.getHeaders();
    if (closeConnection && !headers.containsKey(HTTP.CONN_DIRECTIVE)) {
      LOGGER.finest("Adding HTTP header '" + HTTP.CONN_DIRECTIVE + ": " + HTTP.CONN_CLOSE + "'");
      httpRequest.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
    }
  }

  /**
   * Registers an {@link ICancellable} if this method is invoked in the context of a {@link RunMonitor} (i.e.
   * {@link RunMonitor#CURRENT} is not {@code null}).
   */
  protected IRegistrationHandle registerCancellable(ClientRequest clientRequest, final HttpUriRequest request) {
    final RunMonitor runMonitor = RunMonitor.CURRENT.get();
    if (runMonitor == null) {
      return IRegistrationHandle.NULL_HANDLE;
    }
    ICancellable cancellable;
    Object c = clientRequest.getProperty(RestClientProperties.CANCELLABLE);
    if (c instanceof ICancellable) {
      // use cancellable provided by the client request and ignore the default HTTP connection-aborting strategy
      cancellable = (ICancellable) c;
    }
    else {
      if (c != null) {
        LOGGER.fine("non-null cancellable has unexpected type: " + c.getClass());
      }
      cancellable = new ICancellable() {
        @Override
        public boolean isCancelled() {
          return request.isAborted();
        }

        @Override
        public boolean cancel(boolean interruptIfRunning) {
          LOGGER.fine("Aborting HTTP REST request");
          request.abort();
          return true;
        }
      };
    }
    runMonitor.registerCancellable(cancellable);
    return () -> runMonitor.unregisterCancellable(cancellable);
  }

  @Override
  public Future<?> apply(final ClientRequest request, final AsyncConnectorCallback callback) {
    try {
      ClientResponse response = apply(request);
      callback.response(response);
      return CompletableFuture.completedFuture(response);
    }
    catch (Throwable t) {
      callback.failure(t);
      CompletableFuture<Object> future = new CompletableFuture<>();
      future.completeExceptionally(t);
      return future;
    }
  }

  @Override
  public String getName() {
    return "Scout Apache HttpClient Connector";
  }

  @Override
  public void close() {
    try {
      client.close();
    }
    catch (final IOException e) {
      throw new ProcessingException(LocalizationMessages.FAILED_TO_STOP_CLIENT(), e);
    }
  }

  private HttpHost getHost(final HttpUriRequest request) {
    return new HttpHost(request.getURI().getHost(), request.getURI().getPort(), request.getURI().getScheme());
  }

  private HttpUriRequest getUriHttpRequest(final ClientRequest clientRequest) {
    final RequestConfig.Builder requestConfigBuilder = RequestConfig.copy(requestConfig);

    final int connectTimeout = clientRequest.resolveProperty(ClientProperties.CONNECT_TIMEOUT, -1);
    final int socketTimeout = clientRequest.resolveProperty(ClientProperties.READ_TIMEOUT, -1);

    if (connectTimeout >= 0) {
      requestConfigBuilder.setConnectTimeout(connectTimeout);
    }
    if (socketTimeout >= 0) {
      requestConfigBuilder.setSocketTimeout(socketTimeout);
    }

    final Boolean redirectsEnabled =
        clientRequest.resolveProperty(ClientProperties.FOLLOW_REDIRECTS, requestConfig.isRedirectsEnabled());
    requestConfigBuilder.setRedirectsEnabled(redirectsEnabled);

    final Boolean bufferingEnabled = clientRequest.resolveProperty(ClientProperties.REQUEST_ENTITY_PROCESSING,
        RequestEntityProcessing.class) == RequestEntityProcessing.BUFFERED;
    final HttpEntity entity = getHttpEntity(clientRequest, bufferingEnabled);

    return RequestBuilder
        .create(clientRequest.getMethod())
        .setUri(clientRequest.getUri())
        .setConfig(requestConfigBuilder.build())
        .setEntity(entity)
        .build();
  }

  private HttpEntity getHttpEntity(final ClientRequest clientRequest, final boolean bufferingEnabled) {
    final Object entity = clientRequest.getEntity();

    if (entity == null) {
      return null;
    }

    if (HttpEntity.class.isInstance(entity)) {
      return wrapHttpEntity(clientRequest, (HttpEntity) entity);
    }

    final AbstractHttpEntity httpEntity = new AbstractHttpEntity() {
      @Override
      public boolean isRepeatable() {
        return false;
      }

      @Override
      public long getContentLength() {
        return -1;
      }

      @Override
      public InputStream getContent() throws IOException, IllegalStateException {
        if (bufferingEnabled) {
          final ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
          writeTo(buffer);
          return new ByteArrayInputStream(buffer.toByteArray());
        }
        else {
          return null;
        }
      }

      @Override
      public void writeTo(final OutputStream outputStream) throws IOException {
        clientRequest.setStreamProvider(new OutboundMessageContext.StreamProvider() {
          @Override
          public OutputStream getOutputStream(final int contentLength) throws IOException {
            return outputStream;
          }
        });
        clientRequest.writeEntity();
      }

      @Override
      public boolean isStreaming() {
        return false;
      }
    };

    return bufferEntity(httpEntity, bufferingEnabled);
  }

  private HttpEntity wrapHttpEntity(final ClientRequest clientRequest, final HttpEntity originalEntity) {
    final boolean bufferingEnabled = BufferedHttpEntity.class.isInstance(originalEntity);

    try {
      clientRequest.setEntity(originalEntity.getContent());
    }
    catch (IOException e) {
      //throw new ProcessingException(LocalizationMessages.ERROR_READING_HTTPENTITY_STREAM(e.getMessage()), e);
      throw new ProcessingException("Error reading InputStream from HttpEntity: " + e.getMessage(), e);
    }

    final AbstractHttpEntity httpEntity = new AbstractHttpEntity() {
      @Override
      public boolean isRepeatable() {
        return originalEntity.isRepeatable();
      }

      @Override
      public long getContentLength() {
        return originalEntity.getContentLength();
      }

      @Override
      public Header getContentType() {
        return originalEntity.getContentType();
      }

      @Override
      public Header getContentEncoding() {
        return originalEntity.getContentEncoding();
      }

      @Override
      public InputStream getContent() throws IOException, IllegalStateException {
        return originalEntity.getContent();
      }

      @Override
      public void writeTo(final OutputStream outputStream) throws IOException {
        clientRequest.setStreamProvider(new OutboundMessageContext.StreamProvider() {
          @Override
          public OutputStream getOutputStream(final int contentLength) throws IOException {
            return outputStream;
          }
        });
        clientRequest.writeEntity();
      }

      @Override
      public boolean isStreaming() {
        return originalEntity.isStreaming();
      }

      @Override
      public boolean isChunked() {
        return originalEntity.isChunked();
      }
    };

    return bufferEntity(httpEntity, bufferingEnabled);
  }

  private static HttpEntity bufferEntity(HttpEntity httpEntity, boolean bufferingEnabled) {
    if (bufferingEnabled) {
      try {
        return new BufferedHttpEntity(httpEntity);
      }
      catch (final IOException e) {
        throw new ProcessingException(LocalizationMessages.ERROR_BUFFERING_ENTITY(), e);
      }
    }
    else {
      return httpEntity;
    }
  }

  private static Map<String, String> writeOutBoundHeaders(final ClientRequest clientRequest,
      final HttpUriRequest request) {
    final Map<String, String> stringHeaders =
        HeaderUtils.asStringHeadersSingleValue(clientRequest.getHeaders(), clientRequest.getConfiguration());

    for (final Map.Entry<String, String> e : stringHeaders.entrySet()) {
      request.addHeader(e.getKey(), e.getValue());
    }
    return stringHeaders;
  }

  private static InputStream getInputStream(final CloseableHttpResponse response,
      final ScoutApacheConnector.ConnectionClosingMechanism closingMechanism) throws IOException {
    final InputStream inputStream;

    if (response.getEntity() == null) {
      inputStream = new ByteArrayInputStream(new byte[0]);
    }
    else {
      final InputStream i = response.getEntity().getContent();
      if (i.markSupported()) {
        inputStream = i;
      }
      else {
        inputStream = new BufferedInputStream(i, ReaderWriter.BUFFER_SIZE);
      }
    }

    return closingMechanism.getEntityStream(inputStream, response);
  }

  /**
   * The way the Apache CloseableHttpResponse is to be closed. See https://github.com/eclipse-ee4j/jersey/issues/4321
   * {@link ApacheClientProperties#CONNECTION_CLOSING_STRATEGY}
   */
  private final class ConnectionClosingMechanism {
    private ApacheConnectionClosingStrategy connectionClosingStrategy = null;
    private final ClientRequest clientRequest;
    private final HttpUriRequest apacheRequest;

    private ConnectionClosingMechanism(ClientRequest clientRequest, HttpUriRequest apacheRequest) {
      this.clientRequest = clientRequest;
      this.apacheRequest = apacheRequest;
      Object closingStrategyProperty = clientRequest
          .resolveProperty(ApacheClientProperties.CONNECTION_CLOSING_STRATEGY, Object.class);
      if (closingStrategyProperty != null) {
        if (ApacheConnectionClosingStrategy.class.isInstance(closingStrategyProperty)) {
          connectionClosingStrategy = (ApacheConnectionClosingStrategy) closingStrategyProperty;
        }
        else {
          LOGGER.log(
              Level.WARNING,
              LocalizationMessages.IGNORING_VALUE_OF_PROPERTY(
                  ApacheClientProperties.CONNECTION_CLOSING_STRATEGY,
                  closingStrategyProperty,
                  ApacheConnectionClosingStrategy.class.getName()));
        }
      }

      if (connectionClosingStrategy == null) {
//        if (vi.getRelease().compareTo("4.5") > 0) {
        connectionClosingStrategy = GracefulClosingStrategy.INSTANCE;
//        } else {
//          connectionClosingStrategy = ApacheConnectionClosingStrategy.ImmediateClosingStrategy.INSTANCE;
//        }
      }
    }

    private InputStream getEntityStream(final InputStream inputStream,
        final CloseableHttpResponse response) {
      InputStream filterStream = new FilterInputStream(inputStream) {
        @Override
        public void close() throws IOException {
          connectionClosingStrategy.close(clientRequest, apacheRequest, response, in);
        }
      };
      return filterStream;
    }
  }

  /**
   * Strategy that aborts Apache HttpRequests for the case of Chunked Stream, closes the stream, and response next.
   */
  static class GracefulClosingStrategy implements ApacheConnectionClosingStrategy {
    static final GracefulClosingStrategy INSTANCE = new GracefulClosingStrategy();

    @Override
    public void close(ClientRequest clientRequest, HttpUriRequest request, CloseableHttpResponse response, InputStream stream)
        throws IOException {
      if (response.getEntity() != null && response.getEntity().isChunked()) {
        request.abort();
      }
      try {
        stream.close();
      }
      catch (IOException ex) {
        // Ignore
      }
      finally {
        response.close();
      }
    }
  }

  private static class ConnectionFactory extends ManagedHttpClientConnectionFactory {

    private static final AtomicLong COUNTER = new AtomicLong();

    private final int chunkSize;

    private ConnectionFactory(final int chunkSize) {
      this.chunkSize = chunkSize;
    }

    @Override
    public ManagedHttpClientConnection create(final HttpRoute route, final ConnectionConfig config) {
      final String id = "http-outgoing-" + Long.toString(COUNTER.getAndIncrement());

      return new ScoutApacheConnector.HttpClientConnection(id, config.getBufferSize(), chunkSize);
    }
  }

  private static class HttpClientConnection extends DefaultManagedHttpClientConnection {

    private final int chunkSize;

    private HttpClientConnection(final String id, final int buffersize, final int chunkSize) {
      super(id, buffersize);

      this.chunkSize = chunkSize;
    }

    @Override
    protected OutputStream createOutputStream(final long len, final SessionOutputBuffer outbuffer) {
      if (len == ContentLengthStrategy.CHUNKED) {
        return new ChunkedOutputStream(chunkSize, outbuffer);
      }
      return super.createOutputStream(len, outbuffer);
    }
  }
}
