package com.linplayer.tvlegacy.servers;

import android.content.Context;
import android.provider.Settings;
import androidx.annotation.Nullable;
import com.linplayer.tvlegacy.BuildConfig;
import com.linplayer.tvlegacy.NetworkClients;
import com.linplayer.tvlegacy.R;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class EmbyApi {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private EmbyApi() {}

    public static final class LoginResult {
        public final String baseUrl;
        public final String apiPrefix;
        public final String accessToken;
        public final String userId;
        public final boolean jellyfin;

        LoginResult(String baseUrl, String apiPrefix, String accessToken, String userId, boolean jellyfin) {
            this.baseUrl = safeTrim(baseUrl);
            this.apiPrefix = normalizeApiPrefix(apiPrefix);
            this.accessToken = safeTrim(accessToken);
            this.userId = safeTrim(userId);
            this.jellyfin = jellyfin;
        }
    }

    public static LoginResult authenticateByName(
            Context context, String baseUrl, String username, String password)
            throws IOException, JSONException {
        if (context == null) throw new IllegalArgumentException("context == null");

        String rawBase = normalizeBaseUrl(baseUrl);
        String authRoot = normalizeAuthRoot(rawBase);
        String user = safeTrim(username);
        String pass = password != null ? password : "";
        if (authRoot.isEmpty()) throw new IllegalArgumentException("baseUrl is empty");
        if (user.isEmpty()) throw new IllegalArgumentException("username is empty");

        OkHttpClient client = NetworkClients.okHttp(context.getApplicationContext());

        IOException lastIo = null;

        JSONObject body = new JSONObject();
        body.put("Username", user);
        body.put("Pw", pass);
        // Some servers accept "Password" instead of "Pw". Send both for compatibility (matches Flutter impl).
        body.put("Password", pass);

        List<String> baseCandidates = authBaseCandidates(authRoot);
        List<String> prefixCandidates = defaultApiPrefixCandidates();
        for (String baseCandidate : baseCandidates) {
            for (String prefixCandidate : prefixCandidates) {
                HttpUrl url = buildApiUrl(baseCandidate, prefixCandidate, "Users/AuthenticateByName");
                if (url == null) continue;

                // Emby-style Authorization
                try {
                    LoginResult ok =
                            tryAuthenticate(client, context, url, body, baseCandidate, prefixCandidate, false);
                    if (ok != null) return ok;
                } catch (IOException e) {
                    lastIo = e;
                    // Most common failure when baseUrl/prefix is wrong: 404. Try next.
                    if (isHttpCode(e, 404)) {
                        continue;
                    }
                }

                // Jellyfin-style Authorization (fallback)
                try {
                    LoginResult ok =
                            tryAuthenticate(client, context, url, body, baseCandidate, prefixCandidate, true);
                    if (ok != null) return ok;
                } catch (IOException e) {
                    lastIo = e;
                }
            }
        }
        if (lastIo != null) throw lastIo;
        throw new IOException("authenticate failed");
    }

    public static String fetchServerName(Context context, String baseUrl, @Nullable String token)
            throws IOException, JSONException {
        return fetchServerName(context, baseUrl, token, "emby", false);
    }

    public static String fetchServerName(
            Context context,
            String baseUrl,
            @Nullable String token,
            String apiPrefix,
            boolean jellyfin)
            throws IOException, JSONException {
        if (context == null) throw new IllegalArgumentException("context == null");
        String b = normalizeBaseUrl(baseUrl);
        if (b.isEmpty()) return "";

        OkHttpClient client = NetworkClients.okHttp(context.getApplicationContext());
        String t = safeTrim(token);

        String[] paths = new String[] {"System/Info/Public", "System/Info"};
        IOException last = null;
        for (String p : paths) {
            HttpUrl url = buildApiUrl(b, apiPrefix, p);
            if (url == null) continue;
            Request.Builder rb =
                    new Request.Builder()
                            .url(url)
                            .get()
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json");
            applyAuthorizationHeaders(rb, context, jellyfin, t, null);
            if (!t.isEmpty()) {
                rb.header("X-Emby-Token", t);
            }
            try (Response resp = client.newCall(rb.build()).execute()) {
                if (!resp.isSuccessful()) {
                    last = new IOException("HTTP " + resp.code() + " " + resp.message());
                    continue;
                }
                ResponseBody body = resp.body();
                String s = body != null ? body.string() : "";
                JSONObject root = new JSONObject(s);
                String name = safeTrim(root.optString("ServerName", ""));
                if (name.isEmpty()) name = safeTrim(root.optString("Name", ""));
                if (name.isEmpty()) name = safeTrim(root.optString("ApplicationName", ""));
                if (!name.isEmpty()) return name;
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return "";
    }

    public static List<ServerLine> fetchExtDomains(
            Context context, String baseUrl, String token, boolean allowFailure) throws IOException {
        return fetchExtDomains(context, baseUrl, token, "emby", false, allowFailure);
    }

    public static List<ServerLine> fetchExtDomains(
            Context context,
            String baseUrl,
            String token,
            String apiPrefix,
            boolean jellyfin,
            boolean allowFailure)
            throws IOException {
        if (context == null) throw new IllegalArgumentException("context == null");

        String b = normalizeBaseUrl(baseUrl);
        String t = safeTrim(token);
        if (b.isEmpty() || t.isEmpty()) return Collections.emptyList();

        OkHttpClient client = NetworkClients.okHttp(context.getApplicationContext());

        List<HttpUrl> urls = extDomainUrls(b, t, apiPrefix);
        IOException last = null;
        for (HttpUrl url : urls) {
            if (url == null) continue;
            Request.Builder rb =
                    new Request.Builder()
                            .url(url)
                            .get()
                            .header("Accept", "application/json")
                            .header("X-Emby-Token", t)
                            .header("Content-Type", "application/json");
            applyAuthorizationHeaders(rb, context, jellyfin, t, null);
            Request req = rb.build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    last = new IOException("HTTP " + resp.code() + " " + resp.message());
                    continue;
                }
                ResponseBody body = resp.body();
                String s = body != null ? body.string() : "";
                List<ServerLine> list = parseExtDomains(s);
                return list;
            } catch (Exception e) {
                last = e instanceof IOException ? (IOException) e : new IOException(e);
            }
        }

        if (allowFailure) return Collections.emptyList();
        if (last != null) throw last;
        throw new IOException("fetch domains failed");
    }

    private static List<ServerLine> parseExtDomains(String json) throws JSONException {
        String raw = json != null ? json.trim() : "";
        if (raw.isEmpty()) return Collections.emptyList();
        JSONObject root = new JSONObject(raw);
        boolean ok = root.optBoolean("ok", false);
        if (!ok) return Collections.emptyList();
        JSONArray data = root.optJSONArray("data");
        if (data == null || data.length() == 0) return Collections.emptyList();

        List<ServerLine> out = new ArrayList<>(data.length());
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) continue;
            String name = safeTrim(o.optString("name", ""));
            String url = safeTrim(o.optString("url", ""));
            if (url.isEmpty()) continue;
            out.add(new ServerLine(name, normalizeBaseUrl(url)));
        }
        return Collections.unmodifiableList(out);
    }

    private static HttpUrl buildApiUrl(String baseUrl, String path) {
        return buildApiUrl(baseUrl, "emby", path);
    }

    private static HttpUrl buildApiUrl(String baseUrl, String apiPrefix, String path) {
        String b = safeTrim(baseUrl);
        String prefix = normalizeApiPrefix(apiPrefix);
        String p = safeTrim(path);
        if (b.isEmpty() || p.isEmpty()) return null;
        HttpUrl base = HttpUrl.parse(b);
        if (base == null) return null;
        HttpUrl.Builder ub = base.newBuilder();
        if (!prefix.isEmpty()) {
            ub.addPathSegments(prefix);
        }
        if (p.startsWith("/")) p = p.substring(1);
        ub.addPathSegments(p);
        return ub.build();
    }

    private static List<HttpUrl> extDomainUrls(String baseUrl, String token) {
        return extDomainUrls(baseUrl, token, "emby");
    }

    private static List<HttpUrl> extDomainUrls(String baseUrl, String token, String apiPrefix) {
        String b = safeTrim(baseUrl);
        String t = safeTrim(token);
        if (b.isEmpty() || t.isEmpty()) return Collections.emptyList();

        List<String> bases = authBaseCandidates(normalizeAuthRoot(b));
        List<String> prefixes = apiPrefixCandidates(apiPrefix);

        List<HttpUrl> out = new ArrayList<>();
        for (String base : bases) {
            for (String prefix : prefixes) {
                HttpUrl url = buildApiUrl(base, prefix, "System/Ext/ServerDomains");
                if (url == null) continue;
                out.add(url.newBuilder().addQueryParameter("X-Emby-Token", t).build());
            }
        }
        return out.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(out);
    }

    public static String normalizeBaseUrl(String baseUrl) {
        String v = baseUrl != null ? baseUrl.trim() : "";
        if (v.isEmpty()) return "";
        if (!v.contains("://")) v = "http://" + v;

        HttpUrl url = HttpUrl.parse(v);
        if (url == null) {
            while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
            return v;
        }

        List<String> segs = url.pathSegments();
        ArrayList<String> outSegs = new ArrayList<>(segs != null ? segs : Collections.emptyList());

        while (!outSegs.isEmpty()) {
            int n = outSegs.size();
            String last = outSegs.get(n - 1) != null ? outSegs.get(n - 1).trim().toLowerCase() : "";
            String secondLast =
                    n >= 2 && outSegs.get(n - 2) != null ? outSegs.get(n - 2).trim().toLowerCase() : "";
            if ("index.html".equals(last) && "web".equals(secondLast)) {
                outSegs.remove(n - 1);
                outSegs.remove(n - 2);
                continue;
            }
            if ("web".equals(last)) {
                outSegs.remove(n - 1);
                continue;
            }
            break;
        }

        HttpUrl.Builder b = url.newBuilder().query(null).fragment(null);
        b.encodedPath("/");
        for (int i = 0; i < outSegs.size(); i++) {
            String s = outSegs.get(i);
            String t = s != null ? s.trim() : "";
            if (t.isEmpty()) continue;
            b.addPathSegment(t);
        }
        String out = b.build().toString();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static void applyAuthorizationHeaders(
            Request.Builder rb,
            Context context,
            boolean jellyfin,
            @Nullable String token,
            @Nullable String userId) {
        if (rb == null) return;
        String value = authorizationValue(context, jellyfin, token, userId);
        if (value.isEmpty()) return;
        if (jellyfin) {
            rb.header("X-Emby-Authorization", value);
        } else {
            rb.header("Authorization", value);
            rb.header("X-Emby-Authorization", value);
        }
    }

    private static String authorizationValue(
            Context context, boolean jellyfin, @Nullable String token, @Nullable String userId) {
        String deviceId = deviceId(context);
        String client = context.getString(R.string.app_name);
        String device = "Android TV";
        String version = BuildConfig.VERSION_NAME;

        StringBuilder sb = new StringBuilder();
        sb.append(jellyfin ? "MediaBrowser " : "Emby ");
        if (userId != null && !userId.trim().isEmpty()) {
            sb.append("UserId=\"").append(userId.trim()).append("\", ");
        }
        sb.append("Client=\"").append(client).append("\", ");
        sb.append("Device=\"").append(device).append("\", ");
        sb.append("DeviceId=\"").append(deviceId).append("\", ");
        sb.append("Version=\"").append(version).append("\"");
        if (token != null && !token.trim().isEmpty()) {
            sb.append(", Token=\"").append(token.trim()).append("\"");
        }
        return sb.toString();
    }

    private static LoginResult tryAuthenticate(
            OkHttpClient client,
            Context context,
            HttpUrl url,
            JSONObject body,
            String baseCandidate,
            String apiPrefixCandidate,
            boolean jellyfin)
            throws IOException, JSONException {
        if (client == null) throw new IllegalArgumentException("client == null");
        if (context == null) throw new IllegalArgumentException("context == null");
        if (url == null) throw new IllegalArgumentException("url == null");
        if (body == null) throw new IllegalArgumentException("body == null");

        Request.Builder rb =
                new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(JSON, body.toString()))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json");
        applyAuthorizationHeaders(rb, context, jellyfin, null, null);

        try (Response resp = client.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " " + resp.message());
            }
            ResponseBody respBody = resp.body();
            String s = respBody != null ? respBody.string() : "";
            JSONObject root = new JSONObject(s);
            String token = readToken(root);
            String userId = readUserId(root);
            if (token.isEmpty()) {
                throw new IOException("missing AccessToken");
            }
            return new LoginResult(baseCandidate, apiPrefixCandidate, token, userId, jellyfin);
        }
    }

    private static String readToken(JSONObject root) {
        if (root == null) return "";
        String token = safeTrim(root.optString("AccessToken", ""));
        if (!token.isEmpty()) return token;
        token = safeTrim(root.optString("accessToken", ""));
        if (!token.isEmpty()) return token;
        token = safeTrim(root.optString("Token", ""));
        if (!token.isEmpty()) return token;
        token = safeTrim(root.optString("token", ""));
        return token;
    }

    private static String readUserId(JSONObject root) {
        if (root == null) return "";
        JSONObject userObj = root.optJSONObject("User");
        if (userObj != null) {
            String id = safeTrim(userObj.optString("Id", ""));
            if (!id.isEmpty()) return id;
            id = safeTrim(userObj.optString("id", ""));
            if (!id.isEmpty()) return id;
            id = safeTrim(userObj.optString("UserId", ""));
            if (!id.isEmpty()) return id;
            id = safeTrim(userObj.optString("userId", ""));
            if (!id.isEmpty()) return id;
        }
        String id = safeTrim(root.optString("UserId", ""));
        if (!id.isEmpty()) return id;
        id = safeTrim(root.optString("userId", ""));
        return id;
    }

    private static boolean isHttpCode(IOException e, int code) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.startsWith("HTTP " + code + " ");
    }

    private static List<String> defaultApiPrefixCandidates() {
        // Prefer no prefix first, to match the Flutter client's behavior for Jellyfin/reverse-proxy deployments.
        List<String> out = new ArrayList<>(3);
        out.add("");
        out.add("jellyfin");
        out.add("emby");
        return Collections.unmodifiableList(out);
    }

    private static List<String> apiPrefixCandidates(String preferred) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(normalizeApiPrefix(preferred));
        set.add("");
        set.add("jellyfin");
        set.add("emby");
        ArrayList<String> out = new ArrayList<>(set.size());
        for (String s : set) {
            out.add(normalizeApiPrefix(s));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<String> authBaseCandidates(String authRoot) {
        String root = safeTrim(authRoot);
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        if (root.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(root);
        set.add(root + "/emby");

        ArrayList<String> out = new ArrayList<>(set.size());
        for (String s : set) {
            String v = safeTrim(s);
            while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
            if (!v.isEmpty()) out.add(v);
        }
        return Collections.unmodifiableList(out);
    }

    private static String normalizeAuthRoot(String baseUrl) {
        String v = normalizeBaseUrl(baseUrl);
        if (v.isEmpty()) return "";
        String out = v;
        while (true) {
            String next = stripTrailingPathSegment(out, "emby");
            if (next.equals(out)) break;
            out = next;
        }
        return out;
    }

    private static String normalizeApiPrefix(String prefix) {
        String p = safeTrim(prefix);
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String stripTrailingPathSegment(String url, String segmentLower) {
        String v = safeTrim(url);
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        if (v.isEmpty()) return v;

        String suffix = "/" + (segmentLower != null ? segmentLower.trim().toLowerCase(Locale.US) : "");
        if (suffix.length() <= 1) return v;
        if (!v.toLowerCase(Locale.US).endsWith(suffix)) return v;

        String out = v.substring(0, v.length() - suffix.length());
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String deviceId(Context context) {
        if (context == null) return "";
        try {
            String id =
                    Settings.Secure.getString(
                            context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return safeTrim(id);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safeTrim(String s) {
        return s != null ? s.trim() : "";
    }
}
