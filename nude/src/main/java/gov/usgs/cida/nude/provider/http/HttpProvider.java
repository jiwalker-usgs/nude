package gov.usgs.cida.nude.provider.http;

import gov.usgs.cida.nude.provider.IProvider;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.cache.BasicHttpCacheStorage;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProvider implements IProvider {

	private static final Logger log = LoggerFactory.getLogger(HttpProvider.class);
	// Connection pool setup
	private final static int CONNECTION_TTL = 30 * 60 * 1000;       // 30 minutes, default is infinte
	private final static int CONNECTIONS_MAX_TOTAL = 256;
	private final static int CONNECTIONS_MAX_ROUTE = 32;
	// Connection timeouts
	private final static int CLIENT_SOCKET_TIMEOUT = 10 * 60 * 1000; // 10 minutes, default is infinite
	private final static int CLIENT_CONNECTION_TIMEOUT = 3 * 60 * 1000; // 3 minute, default is infinte
	// Cache setup
	private final static boolean CACHING_ENABLED = true;
	private final static int CACHING_MAX_ENTRIES = 2048;
	private final static int CACHING_MAX_RESPONSE_SIZE = 32767;
	private final static boolean CACHING_HEURISTIC_ENABLED = true; // behaves per RFC 2616
	private final static long CACHIN_HEURITIC_DEFAULT_LIFETIME_SECONDS = 300;  // 5 minutes
	protected HttpCacheStorage cacheStorage;
	protected CacheConfig cacheConfig;
	protected ThreadSafeClientConnManager clientConnectionManager = null;

	@Override
	public void init() {
		if (null != clientConnectionManager) {
			throw new IllegalStateException("Init ran on previously set up instance!");
		}

		// Initialize connection manager, this is thread-safe.  if we use this
		// with any HttpClient instance it becomes thread-safe.
		clientConnectionManager = new ThreadSafeClientConnManager(SchemeRegistryFactory.createDefault(), CONNECTION_TTL, TimeUnit.MILLISECONDS);
		clientConnectionManager.setMaxTotal(CONNECTIONS_MAX_TOTAL);
		clientConnectionManager.setDefaultMaxPerRoute(CONNECTIONS_MAX_ROUTE);
		log.info("Created HTTP client connection manager " + clientConnectionManager.hashCode() + ": maximum connections total = " + clientConnectionManager.getMaxTotal() + ", maximum connections per route = " + clientConnectionManager.getDefaultMaxPerRoute());

		if (CACHING_ENABLED) {
			cacheConfig = new CacheConfig();
			cacheConfig.setMaxCacheEntries(CACHING_MAX_ENTRIES);
			cacheConfig.setMaxObjectSizeBytes(CACHING_MAX_RESPONSE_SIZE);
			cacheConfig.setHeuristicCachingEnabled(CACHING_HEURISTIC_ENABLED);
			cacheConfig.setHeuristicDefaultLifetime(CACHIN_HEURITIC_DEFAULT_LIFETIME_SECONDS);
			cacheConfig.setSharedCache(true);  // won't cache authorized responses
			cacheStorage = new HttpProviderCacheStorage(cacheConfig);
		}
	}

	/**
	 * Gets an HttpClient through the connection manager. Returns null when
	 * Provider has not been set up
	 *
	 * @return
	 */
	public HttpClient getClient() {
		return this.getClient(CLIENT_SOCKET_TIMEOUT);
	}
	
	public HttpClient getClient(int timeoutMillis) {
		HttpClient result = null;

		if (null != clientConnectionManager) {
			HttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setSoTimeout(httpParams, timeoutMillis);
			HttpConnectionParams.setConnectionTimeout(httpParams, CLIENT_CONNECTION_TIMEOUT);

			result = new DefaultHttpClient(clientConnectionManager, httpParams);
			if (CACHING_ENABLED) {
				result = new CachingHttpClient(result, cacheStorage, cacheConfig);
			}
		}

		return result;
	}

	@Override
	public void destroy() {
		int code = clientConnectionManager.hashCode();
		clientConnectionManager.shutdown();
		clientConnectionManager = null;
		log.info("Destroyed HTTP client connection manager " + code);
	}

	public static void generateFirefoxHeaders(HttpUriRequest req, String referer) {
		req.addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:5.0) Gecko/20100101 Firefox/5.0");
		req.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		req.addHeader("Accept-Language", "en-us,en;q=0.5");
		req.addHeader("Accept-Encoding", "gzip, deflate");
		req.addHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
		req.addHeader("Connection", "keep-alive");
		if (null != referer) {
			req.addHeader("Referer", referer);
		}
		req.addHeader("Pragma", "no-cache");
		req.addHeader("Cache-Control", "no-cache");
	}
	
	public static class HttpProviderCacheStorage extends BasicHttpCacheStorage {

		public HttpProviderCacheStorage(CacheConfig cacheConfig) {
			super(cacheConfig);
		}

		@Override
		public synchronized void putEntry(String url, HttpCacheEntry entry) throws IOException {
			super.putEntry(url, checkEntry(entry));
		}

		@Override
		public synchronized HttpCacheEntry getEntry(String url) throws IOException {
			return checkEntry(super.getEntry(url));
		}

		private HttpCacheEntry checkEntry(HttpCacheEntry entry) {
			if (entry != null && entry.getFirstHeader(HTTP.CONTENT_LEN) == null) {
				Header[] originalHeaders = entry.getAllHeaders();
				int originalHeaderCount = originalHeaders.length;
				Header[] fixedHeaders = Arrays.copyOf(originalHeaders, originalHeaderCount + 1);
				fixedHeaders[originalHeaderCount] = new BasicHeader(HTTP.CONTENT_LEN, Long.toString(entry.getResource().length()));
				return new HttpCacheEntry(
						entry.getRequestDate(),
						entry.getResponseDate(),
						entry.getStatusLine(),
						fixedHeaders,
						entry.getResource(),
						entry.getVariantMap());
			} else {
				return entry;
			}
		}
	}
}
