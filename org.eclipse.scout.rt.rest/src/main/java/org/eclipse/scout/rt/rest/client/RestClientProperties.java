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
package org.eclipse.scout.rt.rest.client;

import java.util.logging.Level;

import org.eclipse.scout.rt.platform.util.concurrent.ICancellable;

public final class RestClientProperties {

  /**
   * A value of {@code true} enables cookies.
   * <p>
   * The value MUST be an instance convertible to {@link java.lang.Boolean}.
   * </p>
   * <p>
   * The default value is {@code false}.
   * </p>
   * <p>
   * The name of the configuration property is <tt>{@value}</tt>.
   */
  public static final String ENABLE_COOKIES = "scout.rest.client.enableCookies";

  /**
   * A value of {@code true} disables chunked transfer encoding.
   * <p>
   * The value MUST be an instance convertible to {@link java.lang.Boolean}.
   * <p>
   * The default value is {@code false}.
   * <p>
   * The name of the configuration property is <tt>{@value}</tt>.
   */
  public static final String DISABLE_CHUNKED_TRANSFER_ENCODING = "scout.rest.client.disableChunkedTransferEncoding";

  /**
   * A value of {@code true} ensures that the header 'Connection: close' is added to every REST HTTP request where the
   * 'Connection' header is not already set.
   * <p>
   * The value MUST be an instance convertible to {@link java.lang.Boolean}.
   * <p>
   * The default value is {@code true}.
   * <p>
   * The name of the configuration property is <tt>{@value}</tt>.
   */
  public static final String CONNECTION_CLOSE = "scout.rest.client.connectionClose";

  /**
   * Name used for REST client request/response logger.
   * <p>
   * Note: REST client request response logger is activated if at lease one of the LOGGING_LOGGER_* properties is set.
   */
  public static final String LOGGING_LOGGER_NAME = "scout.rest.client.logging.loggerName";

  /**
   * Level used for REST client request/response logger.
   * <p>
   * Note: REST client request response logger is activated if at lease one of the LOGGING_LOGGER_* properties is set.
   *
   * @see Level for allowed values
   */
  public static final String LOGGING_LOGGER_LEVEL = "scout.rest.client.logging.loggerLevel";

  /**
   * Verbosity used for REST client request/response logger.
   * <p>
   * Note: REST client request response logger is activated if at lease one of the LOGGING_LOGGER_* properties is set.
   *
   * @see LoggerVerbosity for set of allowed values
   */
  public static final String LOGGING_LOGGER_VERBOSITY = "scout.rest.client.logging.loggerVerbosity";

  /**
   * Maximum number of bytes of an entity to be logged by request/response logger.
   * <p>
   * Note: REST client request response logger is activated if at lease one of the LOGGING_LOGGER_* properties is set.
   */
  public static final String LOGGING_LOGGER_MAX_ENTITY_SIZE = "scout.rest.client.logging.loggerEntityMaxSize";

  /**
   * @see RestClientProperties#LOGGING_LOGGER_VERBOSITY
   */
  public enum LoggerVerbosity {
    /**
     * Only content of HTTP headers is logged. No message payload data are logged.
     */
    HEADERS_ONLY,
    /**
     * Content of HTTP headers as well as entity content of textual media types is logged.
     */
    PAYLOAD_TEXT,
    /**
     * Full verbose logging. Content of HTTP headers as well as any message payload content will be logged.
     */
    PAYLOAD_ANY
  }

  /**
   * Optional custom {@link ICancellable} used by HTTP connection providers that support cancellation.
   */
  public static final String CANCELLABLE = "scout.rest.client.cancellable";

  /**
   * Implementation to use for encoding the request URI, i.e. the URI used in the request line (GET [request-uri]
   * HTTP/1.1)
   * <p>
   * The value MUST implement {@link org.eclipse.scout.rt.rest.IRestHttpRequestUriEncoder}.
   * <p>
   * The default value is {@code null}, meaning the http client's default implementation is used.
   * <p>
   * The name of the configuration property is <tt>{@value}</tt>.
   */
  public static final String REQUEST_URI_ENCODER = "scout.rest.client.requestUriEncoder";

  /**
   * The name of the configuration property is <tt>{@value}</tt>. FIXME PBZ javadoc / inverse property?
   *
   * @deprecated will be removed with release 23.0.
   */
  @Deprecated
  public static final String USE_SCOUT_APACHE_CONNECTOR = "scout.rest.client.useScoutApacheConnector";
}
