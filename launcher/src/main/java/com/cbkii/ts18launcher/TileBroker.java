package com.cbkii.ts18launcher;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

/**
 * Small native HTTP/cache boundary for the HOME raster map.
 *
 * The WebView remains responsible only for rendering/interaction. Tile networking is
 * handled here so requests have an application-specific User-Agent, bounded timeouts,
 * HTTP-aware cache metadata and stale-cache fallback without adding a networking library.
 */
final class TileBroker {
    static final String TILE_HOST = "tile.openstreetmap.org";
    private static final Pattern TILE_PATH =
            Pattern.compile("^/(\\d{1,2})/(\\d{1,10})/(\\d{1,10})\\.png$");
    private static final long FALLBACK_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long MAX_CACHE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_TILE_BYTES = 512 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int READ_TIMEOUT_MS = 5500;

    private final File cacheRoot;
    private final String userAgent;
    private final Object[] requestLocks = new Object[16];
    private final AtomicInteger cacheHits = new AtomicInteger();
    private final AtomicInteger networkLoads = new AtomicInteger();
    private final AtomicInteger staleHits = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();
    private volatile String lastFailure = "";

    TileBroker(File applicationCacheDir, String userAgent) {
        this.cacheRoot = new File(applicationCacheDir, "ts18-map-tiles");
        this.userAgent = userAgent;
        for (int i = 0; i < requestLocks.length; i++) requestLocks[i] = new Object();
    }

    static boolean isTileUri(Uri uri) {
        return uri != null
                && "https".equals(uri.getScheme())
                && TILE_HOST.equals(uri.getHost())
                && parseKey(uri) != null;
    }

    WebResourceResponse intercept(Uri uri) {
        TileKey key = parseKey(uri);
        if (key == null) return error(400, "Bad tile request");
        int stripe = (key.z * 31 * 31 + key.x * 31 + key.y) & (requestLocks.length - 1);
        synchronized (requestLocks[stripe]) {
            return interceptLocked(uri, key);
        }
    }

    private WebResourceResponse interceptLocked(Uri uri, TileKey key) {
        long now = System.currentTimeMillis();
        File tile = tileFile(key);
        File metadata = metadataFile(tile);
        CacheMeta meta = readMeta(metadata);
        if (tile.isFile() && tile.length() > 0L && isFresh(tile, meta, now)) {
            cacheHits.incrementAndGet();
            touch(tile, now);
            return fileResponse(tile, "HIT");
        }

        boolean staleAvailable = tile.isFile() && tile.length() > 0L;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(true);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.1");

            if (staleAvailable && meta != null) {
                if (!meta.etag.isEmpty()) {
                    connection.setRequestProperty("If-None-Match", meta.etag);
                }
                if (!meta.lastModified.isEmpty()) {
                    connection.setRequestProperty("If-Modified-Since", meta.lastModified);
                }
            }

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && staleAvailable) {
                CacheMeta refreshed = CacheMeta.fromResponse(connection, meta, now);
                writeMeta(metadata, refreshed);
                cacheHits.incrementAndGet();
                touch(tile, now);
                lastFailure = "";
                return fileResponse(tile, "REVALIDATED");
            }

            if (code != HttpURLConnection.HTTP_OK) {
                String reason = "HTTP " + code;
                return staleOrError(tile, staleAvailable, reason);
            }

            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                return staleOrError(tile, staleAvailable, "invalid content type");
            }

            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readBounded(input);
            }
            if (bytes.length == 0) {
                return staleOrError(tile, staleAvailable, "empty tile");
            }

            writeTile(tile, bytes);
            writeMeta(metadata, CacheMeta.fromResponse(connection, null, now));
            networkLoads.incrementAndGet();
            lastFailure = "";
            if ((writes.incrementAndGet() & 31) == 0) trimCache();
            return bytesResponse(bytes, "MISS");
        } catch (SSLHandshakeException e) {
            return staleOrError(tile, staleAvailable, "TLS handshake");
        } catch (IOException | RuntimeException e) {
            return staleOrError(tile, staleAvailable, e.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    String lastFailure() {
        return lastFailure;
    }

    String diagnosticSummary() {
        return "cache=" + cacheHits.get()
                + " network=" + networkLoads.get()
                + " stale=" + staleHits.get()
                + " failures=" + failures.get()
                + (lastFailure.isEmpty() ? "" : " last=" + lastFailure);
    }

    private WebResourceResponse staleOrError(File tile, boolean staleAvailable, String reason) {
        lastFailure = reason;
        failures.incrementAndGet();
        if (staleAvailable) {
            staleHits.incrementAndGet();
            touch(tile, System.currentTimeMillis());
            return fileResponse(tile, "STALE");
        }
        return error(502, "Tile unavailable");
    }

    private static boolean isFresh(File tile, CacheMeta meta, long now) {
        long expiresAt = meta == null
                ? tile.lastModified() + FALLBACK_CACHE_TTL_MS
                : meta.expiresAt;
        return expiresAt > now;
    }

    private static TileKey parseKey(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || !TILE_HOST.equals(uri.getHost())) {
            return null;
        }
        Matcher match = TILE_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (!match.matches()) return null;
        try {
            int z = Integer.parseInt(match.group(1));
            int x = Integer.parseInt(match.group(2));
            int y = Integer.parseInt(match.group(3));
            if (z < 0 || z > 19) return null;
            int count = 1 << z;
            if (x < 0 || x >= count || y < 0 || y >= count) return null;
            return new TileKey(z, x, y);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private File tileFile(TileKey key) {
        return new File(new File(new File(cacheRoot, Integer.toString(key.z)),
                Integer.toString(key.x)), key.y + ".png");
    }

    private static File metadataFile(File tile) {
        return new File(tile.getParentFile(), tile.getName() + ".meta");
    }

    private WebResourceResponse fileResponse(File file, String cacheState) {
        try {
            return new WebResourceResponse(
                    "image/png",
                    null,
                    200,
                    "OK",
                    Collections.singletonMap("X-TS18-Map-Cache", cacheState),
                    new FileInputStream(file));
        } catch (IOException e) {
            lastFailure = e.getClass().getSimpleName();
            failures.incrementAndGet();
            return error(502, "Tile cache read failed");
        }
    }

    private static WebResourceResponse bytesResponse(byte[] bytes, String cacheState) {
        return new WebResourceResponse(
                "image/png",
                null,
                200,
                "OK",
                Collections.singletonMap("X-TS18-Map-Cache", cacheState),
                new ByteArrayInputStream(bytes));
    }

    private static WebResourceResponse error(int status, String reason) {
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                status,
                reason,
                Collections.emptyMap(),
                new ByteArrayInputStream(reason.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_TILE_BYTES) throw new IOException("tile too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void writeTile(File destination, byte[] bytes) throws IOException {
        File parent = destination.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cache directory unavailable");
        }
        File temporary = new File(parent,
                destination.getName() + ".tmp-" + Thread.currentThread().getId());
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
            output.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IOException("cannot replace cached tile");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("cannot commit cached tile");
        }
    }

    private static CacheMeta readMeta(File file) {
        if (!file.isFile()) return null;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            long expires = Long.parseLong(properties.getProperty("expiresAt", "0"));
            return new CacheMeta(
                    expires,
                    properties.getProperty("etag", ""),
                    properties.getProperty("lastModified", ""));
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    private static void writeMeta(File file, CacheMeta meta) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cache directory unavailable");
        }
        Properties properties = new Properties();
        properties.setProperty("expiresAt", Long.toString(meta.expiresAt));
        properties.setProperty("etag", meta.etag);
        properties.setProperty("lastModified", meta.lastModified);
        File temporary = new File(parent,
                file.getName() + ".tmp-" + Thread.currentThread().getId());
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            properties.store(output, "TS18 map tile cache metadata");
        }
        if (file.exists() && !file.delete()) {
            temporary.delete();
            throw new IOException("cannot replace tile metadata");
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("cannot commit tile metadata");
        }
    }

    private static void touch(File file, long now) {
        if (file.isFile()) file.setLastModified(now);
    }

    private void trimCache() {
        if (!cacheRoot.isDirectory()) return;
        java.util.ArrayList<File> tiles = new java.util.ArrayList<>();
        collectTiles(cacheRoot, tiles);
        long total = 0L;
        for (File tile : tiles) total += tile.length();
        if (total <= MAX_CACHE_BYTES) return;
        Collections.sort(tiles, (left, right) ->
                Long.compare(left.lastModified(), right.lastModified()));
        for (File tile : tiles) {
            if (total <= MAX_CACHE_BYTES) break;
            long size = tile.length();
            File meta = metadataFile(tile);
            if (tile.delete()) {
                total -= size;
                if (meta.isFile()) meta.delete();
            }
        }
    }

    private static void collectTiles(File directory, java.util.List<File> output) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectTiles(child, output);
            else if (child.getName().endsWith(".png")) output.add(child);
        }
    }

    private static final class TileKey {
        final int z;
        final int x;
        final int y;

        TileKey(int z, int x, int y) {
            this.z = z;
            this.x = x;
            this.y = y;
        }
    }

    private static final class CacheMeta {
        final long expiresAt;
        final String etag;
        final String lastModified;

        CacheMeta(long expiresAt, String etag, String lastModified) {
            this.expiresAt = expiresAt;
            this.etag = etag == null ? "" : etag;
            this.lastModified = lastModified == null ? "" : lastModified;
        }

        static CacheMeta fromResponse(
                HttpURLConnection connection, CacheMeta previous, long now) {
            long expiresAt = expiryFrom(connection, now);
            String etag = valueOrPrevious(connection.getHeaderField("ETag"),
                    previous == null ? "" : previous.etag);
            String modified = valueOrPrevious(connection.getHeaderField("Last-Modified"),
                    previous == null ? "" : previous.lastModified);
            return new CacheMeta(expiresAt, etag, modified);
        }

        private static long expiryFrom(HttpURLConnection connection, long now) {
            String cacheControl = connection.getHeaderField("Cache-Control");
            if (cacheControl != null) {
                Matcher matcher = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)",
                        Pattern.CASE_INSENSITIVE).matcher(cacheControl);
                if (matcher.find()) {
                    try {
                        long seconds = Long.parseLong(matcher.group(1));
                        if (seconds > 0L && seconds < Long.MAX_VALUE / 1000L) {
                            return now + seconds * 1000L;
                        }
                    } catch (NumberFormatException ignored) {
                        // Fall through to Expires/default TTL.
                    }
                }
            }
            long expires = connection.getExpiration();
            if (expires > now) return expires;
            return now + FALLBACK_CACHE_TTL_MS;
        }

        private static String valueOrPrevious(String value, String previous) {
            return value == null || value.isEmpty() ? previous : value;
        }
    }
}
