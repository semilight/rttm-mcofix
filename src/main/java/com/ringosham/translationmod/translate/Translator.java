/*
 * Copyright (C) 2021 Ringosham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ringosham.translationmod.translate;

import com.ringosham.translationmod.client.BaiduClient;
import com.ringosham.translationmod.client.GoogleClient;
import com.ringosham.translationmod.client.SelfHostedClient;
import com.ringosham.translationmod.client.GooglePaidClient;
import com.ringosham.translationmod.client.types.Language;
import com.ringosham.translationmod.client.types.RequestResult;
import com.ringosham.translationmod.common.ChatUtil;
import com.ringosham.translationmod.common.ConfigManager;
import com.ringosham.translationmod.common.Log;
import com.ringosham.translationmod.translate.types.TranslateResult;
import net.minecraft.util.text.TextFormatting;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Translator extends Thread {
    private final String message;
    //the same message with color codes, used to rebuild the header
    private final String formattedMessage;
    private final Language from;
    private final Language to;
    private static final LinkedList<TranslationLog> translationLog = new LinkedList<>();
    //Cache about 100 messages
    private static final int CACHE_SIZE = 100;

    private static boolean warnLimit = false;

    //only true for normal chat, does not apply to signs
    private final boolean automatic;

    public Translator(String message, Language from, Language to) {
        this(message, message, from, to, false);
    }

    //skip languages in normal chat only
    public Translator(String message, String formattedMessage, Language from, Language to) {
        this(message, formattedMessage, from, to, true);
    }

    private Translator(String message, String formattedMessage, Language from, Language to,
                       boolean automatic) {
        this.message = message;
        this.formattedMessage = formattedMessage;
        this.from = from;
        this.to = to;
        this.automatic = automatic;
    }

    //Limits how many messages to get from the full log
    public static List<TranslationLog> getTranslationLog(int count) {
        int begin = translationLog.size() - count;
        if (begin < 0)
            begin = 0;
        return translationLog.subList(begin, translationLog.size());
    }

    //Parameter required for the raw content without any chat headers from the server
    public TranslateResult translate(String rawMessage) {
        //Save bandwidth and money $.
        if (from == to)
            return null;
        //In case the message content is empty. Thanks to sloppy chat plugins in servers
        //Also if you are using the paid option, Google charges per character, including whitespaces.
        //If the request is empty, Google STILL charges a character.
        rawMessage = rawMessage.trim();
        if (rawMessage.length() == 0)
            return null;
        //Check if message already exist in cache
        for (TranslationLog log : translationLog) {
            if (rawMessage.equals(log.message) && to == log.result.getSourceLanguage()) {
                return accepted(log.result.getMessage(), log.result.getSourceLanguage());
            }
        }
        //the active engine, not the saved one
        switch (ConfigManager.INSTANCE.getActiveEngine()) {
            case "google":
                if (!ConfigManager.INSTANCE.getGoogleKey().equals("") && !GooglePaidClient.getDisable()) {
                    //Paid options go first.
                    GooglePaidClient google = new GooglePaidClient();
                    RequestResult transRequest;
                    if (from == null)
                        transRequest = google.translateAuto(rawMessage, to);
                    else
                        transRequest = google.translate(rawMessage, from, to);
                    if (transRequest.getCode() != 200) {
                        return afterGoogleFailure(transRequest, rawMessage, from, to);
                    }
                    return accepted(transRequest.getMessage(), transRequest.getFrom());
                } else if (!GoogleClient.isAccessDenied()) {
                    //Use free ones later
                    GoogleClient google = new GoogleClient();
                    RequestResult transRequest;
                    if (from == null)
                        transRequest = google.translateAuto(rawMessage, to);
                    else
                        transRequest = google.translate(rawMessage, from, to);
                    if (transRequest.getCode() != 200) {
                        return afterGoogleFailure(transRequest, rawMessage, from, to);
                    }
                    return accepted(transRequest.getMessage(), transRequest.getFrom());
                }
                //beating a dead horse. fuck off.
                return null;
            case "selfhosted":
                return translateSelfHosted(rawMessage, from, to);
            case "baidu":
                BaiduClient baidu = new BaiduClient();
                RequestResult transRequest;
                if (from == null)
                    transRequest = baidu.translateAuto(rawMessage, to);
                else
                    transRequest = baidu.translate(rawMessage, from, to);
                if (transRequest.getCode() != 200) {
                    logException(transRequest);
                    return null;
                }
                return accepted(transRequest.getMessage(), transRequest.getFrom());
        }
        //Otherwise ignore
        return null;
    }

    //drops the result if it's normal chat and the language is set to never translate
    private TranslateResult accepted(String translated, Language source) {
        if (automatic && ConfigManager.INSTANCE.isLanguageSkipped(source)) {
            return null;
        }
        return new TranslateResult(translated, source);
    }

    private TranslateResult translateSelfHosted(String rawMessage, Language from, Language to) {
        SelfHostedClient selfHosted = new SelfHostedClient(automatic);
        RequestResult selfRequest;
        if (from == null)
            selfRequest = selfHosted.translateAuto(rawMessage, to);
        else
            selfRequest = selfHosted.translate(rawMessage, from, to);
        if (selfRequest.getCode() == SelfHostedClient.SKIP)
            return null;
        if (selfRequest.getCode() != 200) {
            logException(selfRequest);
            return null;
        }
        return accepted(selfRequest.getMessage(), selfRequest.getFrom());
    }

    //report the google failure, then retry the failed message on NLLB
    private TranslateResult afterGoogleFailure(RequestResult failed, String rawMessage,
                                               Language from, Language to) {
        boolean fallbackWasActive = ConfigManager.INSTANCE.isSessionFallbackActive();
        logException(failed);
        if (!fallbackWasActive && ConfigManager.INSTANCE.isSessionFallbackActive()) {
            return translateSelfHosted(rawMessage, from, to);
        }
        return null;
    }

    //anchors a chat regex to the start of the line.
    private static Pattern anchored(String regex) {
        return Pattern.compile(regex.startsWith("^") ? regex : "^" + regex);
    }

    //Finds the player name in the chat using regex groups
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private String findPlayerName(String message, int regexIndex) {
        Matcher matcher = anchored(ConfigManager.INSTANCE.getRegexList().get(regexIndex)).matcher(message);
        matcher.find();
        return matcher.group(ConfigManager.INSTANCE.getGroupList().get(regexIndex));
    }

    private void logException(RequestResult transRequest) {
        switch (transRequest.getCode()) {
            case 1:
                Log.logger.error("Cannot connect to translation server. Is player offline?");
                break;
            case 2:
                //Unknown response
                Log.logger.error(transRequest.getMessage());
                break;
            case SelfHostedClient.AUTH_FAIL:
                Log.logger.error("Self-hosted >> API key rejected");
                ChatUtil.printChatMessage(true, "Translation API key was rejected. Check selfHostedApiKey in the mod config", TextFormatting.RED);
                break;
            case SelfHostedClient.BLOCKED:
                Log.logger.error("Self-hosted >> request blocked before reaching the service (403)");
                ChatUtil.printChatMessage(true, "Translation blocked by the network in front of the server, not by your key. See the log", TextFormatting.RED);
                break;
            case SelfHostedClient.RATE_LIMITED:
                Log.logger.warn("Self-hosted >> rate limit reached");
                ChatUtil.printChatMessage(true, "Translation rate limit reached. Try again in a minute", TextFormatting.YELLOW);
                break;
            case 411:
                Log.logger.error("Google API >> API call error");
                ChatUtil.printChatMessage(true, "API call error. Please report this error as it shouldn't happen", TextFormatting.RED);
                break;
            case 429:
                //gg google died. use fallback.
                if (!ConfigManager.FALLBACK_ENGINE.equals(ConfigManager.INSTANCE.getActiveEngine())) {
                    ConfigManager.INSTANCE.setSessionEngine(ConfigManager.FALLBACK_ENGINE);
                    Log.logger.warn("Google denied access to translation API. Falling back to NLLB for this session");
                    ChatUtil.printChatMessage(true, "Google translate has stopped responding. Falling back to NLLB for this session...", TextFormatting.YELLOW);
                }
                break;
            case 403:
                Log.logger.error("Google API >> Exceeded API quota");
                ChatUtil.printChatMessage(true, "You have exceeded your quota. Please check your quota settings", TextFormatting.RED);
                ChatUtil.printChatMessage(true, "Falling back to free version until you restart the game", TextFormatting.RED);
                GooglePaidClient.setDisable();
                break;
            case 400:
                Log.logger.error("Google API >> API key invalid");
                ChatUtil.printChatMessage(true, "API key invalid. If you do not wish to use a key, please remove it from the settings", TextFormatting.RED);
                break;
            case 500:
                Log.logger.error("Google API >> Failed to determine source language: " + transRequest.getMessage());
                break;
            case 52001:
                Log.logger.error("Baidu API >> Connection timeout");
                break;
            case 52002:
                Log.logger.error("Baidu API >> Server side failure");
                ChatUtil.printChatMessage(true, "Server side failure. Cannot translate", TextFormatting.RED);
                break;
            case 52003:
                Log.logger.error("Baidu API >> Unauthorized request");
                ChatUtil.printChatMessage(true, "Authentication failure. Please check your App ID and API key", TextFormatting.RED);
                break;
            case 54003:
                Log.logger.warn("Baidu API >> Restricted request per second limit");
                if (!warnLimit) {
                    ChatUtil.printChatMessage(true, "Request restricted due to tier limits. Consider upgrading your plan", TextFormatting.YELLOW);
                    warnLimit = true;
                }
                break;
            case 54004:
                Log.logger.error("Baidu API >> Not enough balance");
                ChatUtil.printChatMessage(true, "Balance insufficient. Please go to Baidu's control panel to top up", TextFormatting.RED);
                break;
            case 54005:
                Log.logger.error("Baidu API >> Request too large");
                ChatUtil.printChatMessage(true, "Request denied due to size too large", TextFormatting.YELLOW);
                break;
            case 58001:
                Log.logger.error("Baidu API >> Translation direction not support");
                ChatUtil.printChatMessage(true, "Cannot translate from " + transRequest.getFrom().getName() + " to " + transRequest.getTo().getName(), TextFormatting.RED);
                break;
            case 58002:
                Log.logger.error("Baidu API >> Translation service is not enabled");
                ChatUtil.printChatMessage(true, "Translation service is not enabled. Please turn it on in Baidu's control panel", TextFormatting.RED);
                break;
            case 90107:
                Log.logger.error("Baidu API >> Verification failed");
                ChatUtil.printChatMessage(true, "Verification failed. Please check your verification status", TextFormatting.RED);
                break;
            default:
                Log.logger.error("Unknown error/Server side failure: " + transRequest.getMessage());
                break;
        }
    }

    //If this class is run as a thread, it handles incoming chat messages
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void run() {
        int regexIndex = 0;
        //There could be multiple regex that matches the chat message. In that case, choose the longest match after matching.
        if (message.trim().isEmpty())
            return;
        //Replace section signs one more time for the sake of bugs. Thanks Java.
        String messageTrim = message.replaceAll("\u00A7(.)", "");
        String headerMatch = null;
        for (String regex : ConfigManager.INSTANCE.getRegexList()) {
            Pattern pattern = anchored(regex);
            Matcher matcher = pattern.matcher(messageTrim);
            if (matcher.find()) {
                if (headerMatch == null) {
                    headerMatch = matcher.group(0);
                    //There should be no duplicates in the list. This should be fine.
                    regexIndex = ConfigManager.INSTANCE.getRegexList().indexOf(regex);
                } else if (headerMatch.length() < matcher.group(0).length()) {
                    headerMatch = matcher.group(0);
                    regexIndex = ConfigManager.INSTANCE.getRegexList().indexOf(regex);
                }
            }
        }
        //Ignore message if no regex match
        if (headerMatch == null)
            return;
        String sender = findPlayerName(headerMatch, regexIndex);
        //If the player name cannot be found, chances are it's not a chat message
        if (sender == null)
            return;
        Matcher matcher = anchored(ConfigManager.INSTANCE.getRegexList().get(regexIndex)).matcher(messageTrim);
        if (!matcher.find())
            return;
        //cut the header off by position. replace() would strip every copy of it.
        String rawMessage = messageTrim.substring(matcher.end());
        if (ConfigManager.INSTANCE.isServerTranslation(rawMessage))
            return;
        TranslateResult translatedMessage = translate(rawMessage);
        if (translatedMessage == null)
            return;
        addToLog(new TranslationLog(sender, rawMessage, translatedMessage));
        String fromStr = translatedMessage.getSourceLanguage() != null ? translatedMessage.getSourceLanguage().getName() : "Unknown";
        //In cases where the message language and the target language is the same
        if (translatedMessage.getMessage().trim().equals(rawMessage.trim()))
            return;
        String header = matcher.group(0).trim();
        String coloredHeader = ChatUtil.coloredPrefix(formattedMessage, header.length());
        if (coloredHeader == null)
            coloredHeader = header;
        String hoverText = "Sender: " + sender + "\n" + "Translation: " + fromStr + " -> " + to.getName();
        ChatUtil.printTranslation(coloredHeader, translatedMessage.getMessage(), fromStr, hoverText);
    }

    private void addToLog(TranslationLog log) {
        //Prevent adding the same entry over and over
        if (!translationLog.contains(log))
            translationLog.add(log);
        if (translationLog.size() > CACHE_SIZE)
            translationLog.pollFirst();
    }

    public static class TranslationLog {
        private final String sender;
        private final String message;
        private final TranslateResult result;

        public TranslationLog(String sender, String message, TranslateResult result) {
            this.sender = sender;
            this.message = message;
            this.result = result;
        }

        public String getMessage() {
            return message;
        }

        public String getSender() {
            return sender;
        }

        public TranslateResult getResult() {
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TranslationLog that = (TranslationLog) o;
            return Objects.equals(sender, that.sender) && Objects.equals(message, that.message) && Objects.equals(result, that.result);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sender, message, result);
        }
    }
}
