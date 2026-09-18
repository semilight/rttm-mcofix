package com.ringosham.translationmod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ringosham.translationmod.TranslationMod;
import com.ringosham.translationmod.client.types.Language;
import com.ringosham.translationmod.client.types.RequestResult;
import com.ringosham.translationmod.common.ConfigManager;
import com.ringosham.translationmod.common.Log;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

//translates through my own server instead of google as a fallback option
//libretranslate detects the language, NLLB does the actual translating.
public class SelfHostedClient extends RESTClient {

    private static final String TARGET_FALLBACK = "eng_Latn";
    //already in your language or undetected
    public static final int SKIP = 204;
    //error handler is shared with google which already uses 401/403/429.
    public static final int AUTH_FAIL = 4401;
    public static final int RATE_LIMITED = 4429;
    public static final int BLOCKED = 4403;

    //only true for normal chat
    private final boolean applyLanguageSkips;

    public SelfHostedClient() {
        this(false);
    }

    public SelfHostedClient(boolean applyLanguageSkips) {
        super("");
        this.applyLanguageSkips = applyLanguageSkips;
    }

    //get them emoji codes out of here
    private static final String SHORTCODE = ":[A-Za-z0-9_+-]+:";

    private static String normalise(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(Character.isLetterOrDigit(c) ? Character.toLowerCase(c) : ' ');
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    //compare input/output and return how alike they are, 1.0 = identical
    //if a translation comes back looking like the input then it probably wasn't translated
    private static double similarity(String a, String b) {
        String x = normalise(a);
        String y = normalise(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> left = trigrams(x);
        Map<String, Integer> right = trigrams(y);
        int leftTotal = 0;
        for (int count : left.values()) {
            leftTotal += count;
        }
        int rightTotal = 0;
        int shared = 0;
        for (Map.Entry<String, Integer> entry : right.entrySet()) {
            rightTotal += entry.getValue();
            Integer inLeft = left.get(entry.getKey());
            if (inLeft != null) {
                shared += Math.min(inLeft, entry.getValue());
            }
        }
        return 2.0 * shared / (leftTotal + rightTotal);
    }

    private static Map<String, Integer> trigrams(String s) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        if (s.length() < 3) {
            counts.put(s, 1);
            return counts;
        }
        for (int i = 0; i + 3 <= s.length(); i++) {
            String g = s.substring(i, i + 3);
            Integer c = counts.get(g);
            counts.put(g, c == null ? 1 : c + 1);
        }
        return counts;
    }

    private static int meaningfulLetters(String message) {
        String stripped = message.replaceAll(SHORTCODE, " ");
        int letters = 0;
        for (int i = 0; i < stripped.length(); i++) {
            if (Character.isLetter(stripped.charAt(i))) {
                letters++;
            }
        }
        return letters;
    }

    @Override
    public RequestResult translate(String message, Language from, Language to) {
        //check the length first so we dont waste a request
        if (meaningfulLetters(message) < ConfigManager.INSTANCE.getSelfHostedMinChars()) {
            return new RequestResult(SKIP, "", null, null);
        }
        String targetIso = to == null ? "en" : to.getGoogleCode();
        String targetNllb = NllbCodes.of(targetIso);
        if (targetNllb == null) {
            targetNllb = TARGET_FALLBACK;
        }

        Language detected = from;
        String sourceNllb;
        if (from != null && NllbCodes.isKnown(from.getGoogleCode())) {
            sourceNllb = NllbCodes.of(from.getGoogleCode());
        } else {
            Detection d = detect(message);
            if (d == null) {
                return new RequestResult(1, "Cannot reach the detection service", null, null);
            }
            if (d.error != 0) {
                return new RequestResult(d.error, "Detection service rejected the request", null, null);
            }
            //already in your language
            if (targetIso.equalsIgnoreCase(d.code)) {
                return new RequestResult(SKIP, "", null, null);
            }
            //a language on your "never translate" list
            if (applyLanguageSkips && ConfigManager.INSTANCE.isLanguageSkipped(d.code)) {
                return new RequestResult(SKIP, "", null, null);
            }
            //confidence goes up with message length more than correctness, so keep the bar low
            if (d.confidence < ConfigManager.INSTANCE.getSelfHostedMinConfidence()
                    || !NllbCodes.isKnown(d.code)) {
                String fallback = ConfigManager.INSTANCE.getSelfHostedFallbackLang();
                if (fallback == null || fallback.trim().isEmpty() || !NllbCodes.isKnown(fallback)) {
                    //no idea what it is.
                    return new RequestResult(SKIP, "", null, null);
                }
                sourceNllb = NllbCodes.of(fallback);
                detected = LangManager.getInstance().findLanguageFromGoogle(fallback);
            } else {
                sourceNllb = NllbCodes.of(d.code);
                detected = LangManager.getInstance().findLanguageFromGoogle(d.code);
            }
        }

        JsonObject body = new JsonObject();
        body.addProperty("q", message);
        body.addProperty("source", sourceNllb);
        body.addProperty("target", targetNllb);
        Response response = postJson(ConfigManager.INSTANCE.getNllbUrl() + "/translate", body.toString());
        if (response == null) {
            return new RequestResult(1, "Cannot reach the translation service", null, null);
        }
        if (response.getResponseCode() != 200) {
            //split these out so the chat message tells you what to actually do
            if (response.getResponseCode() == 401) {
                Log.logger.error("Translation service rejected the API key (check selfHostedApiKey)");
                return new RequestResult(AUTH_FAIL, "Translation API key rejected", null, null);
            }
            if (response.getResponseCode() == 403) {
                Log.logger.error("Translation request blocked before it reached the service (403): {}",
                    response.getEntity());
                return new RequestResult(BLOCKED, "Translation request blocked", null, null);
            }
            if (response.getResponseCode() == 429) {
                Log.logger.warn("Translation service rate limit reached; backing off");
                return new RequestResult(RATE_LIMITED, "Translation rate limit reached", null, null);
            }
            Log.logger.error("Self-hosted translate failed ({}): {}",
                response.getResponseCode(), response.getEntity());
            return new RequestResult(411, "Self-hosted translation service error", null, null);
        }
        try {
            Gson gson = new GsonBuilder().setLenient().create();
            JsonObject json = gson.fromJson(response.getEntity(), JsonObject.class);
            String text = json.get("translatedText").getAsString();
            //if the translation barely changed the input then the language was guessed wrong
            if (similarity(message, text) >= ConfigManager.INSTANCE.getSelfHostedMaxSimilarity()) {
                return new RequestResult(SKIP, "", null, null);
            }
            return new RequestResult(200, text, detected, to);
        } catch (Exception e) {
            Log.logger.error("Could not parse the translation response", e);
            return new RequestResult(2, "Bad response from the translation service", null, null);
        }
    }

    private static final class Detection {
        final String code;
        final double confidence;
        //set when detection fails in a way you need to know about, like a bad key
        //otherwise it quietly falls back and you never find out
        final int error;

        Detection(String code, double confidence) {
            this(code, confidence, 0);
        }

        Detection(String code, double confidence, int error) {
            this.code = code;
            this.confidence = confidence;
            this.error = error;
        }
    }

    //returns null only if the service is unreachable, a bad answer just means low confidence
    private Detection detect(String message) {
        JsonObject body = new JsonObject();
        body.addProperty("q", message);
        Response response = postJson(
            ConfigManager.INSTANCE.getLibreTranslateUrl() + "/detect", body.toString());
        if (response == null) {
            return null;
        }
        if (response.getResponseCode() != 200) {
            int code = response.getResponseCode();
            if (code == 401) {
                return new Detection("", 0, AUTH_FAIL);
            }
            if (code == 403) {
                return new Detection("", 0, BLOCKED);
            }
            if (code == 429) {
                return new Detection("", 0, RATE_LIMITED);
            }
            Log.logger.warn("Language detection failed ({}), using the fallback language", code);
            return new Detection("", 0);
        }
        try {
            Gson gson = new GsonBuilder().setLenient().create();
            JsonArray array = gson.fromJson(response.getEntity(), JsonArray.class);
            if (array == null || array.size() == 0) {
                return new Detection("", 0);
            }
            JsonObject best = array.get(0).getAsJsonObject();
            return new Detection(best.get("language").getAsString(),
                best.get("confidence").getAsDouble());
        } catch (Exception e) {
            Log.logger.warn("Could not parse the detection response, using the fallback language", e);
            return new Detection("", 0);
        }
    }

    //posts json. the parent class only does GET, so this rolls its own.
    private Response postJson(String url, String json) {
        HttpURLConnection connection = null;
        try {
            URL target = new URL(url);
            connection = target.getProtocol().equals("https")
                ? (HttpsURLConnection) target.openConnection()
                : (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            //java's default user agent gets blocked by cloudflare, so send our own
            connection.setRequestProperty("User-Agent",
                "RTTM-mcofix/" + TranslationMod.MOD_VERSION + " (Minecraft 1.12.2)");
            //only needed when going through the gateway
            String apiKey = ConfigManager.INSTANCE.getSelfHostedApiKey();
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                connection.setRequestProperty("X-API-Key", apiKey.trim());
            }
            connection.setConnectTimeout(ConfigManager.INSTANCE.getSelfHostedTimeout());
            connection.setReadTimeout(ConfigManager.INSTANCE.getSelfHostedTimeout());
            connection.setDoOutput(true);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            int code = connection.getResponseCode();
            InputStream stream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                return new Response(code, "");
            }
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder responseBody = new StringBuilder();
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            } finally {
                reader.close();
            }
            return new Response(code, responseBody.toString());
        } catch (IOException e) {
            Log.logger.warn("Self-hosted request to {} failed: {}", url, e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
