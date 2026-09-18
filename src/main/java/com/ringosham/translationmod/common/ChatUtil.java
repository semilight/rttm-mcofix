package com.ringosham.translationmod.common;

import com.ringosham.translationmod.TranslationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.List;

public class ChatUtil {
    private static final String prefix = TextFormatting.GREEN + "[" + TextFormatting.RESET + "RTTM" + TextFormatting.GREEN + "] " + TextFormatting.RESET;
    //shared so the settings preview matches
    public static final String RTTM_TAG = "§a[§rRTTM§a]§r";
    public static final String SIGN_TAG = "§a[§rSIGN§a]§r";

    public static void printChatMessage(boolean addPrefix, String message, TextFormatting color) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        Style style = new Style();
        style.setColor(color);
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage((new TextComponentString((addPrefix ? prefix : "") + color + message).setStyle(style)));
    }

    public static void printChatMessageAdvanced(String message, String hoverText, boolean bold, boolean italic, boolean underline, TextFormatting color) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        Style style = new Style();
        style.setColor(color)
                .setBold(bold)
                .setItalic(italic)
                .setUnderlined(underline);
        if (hoverText != null)
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(hoverText)));
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentString(message).setStyle(style));
    }

    //prints a translation as "<name> [RTTM] text (Language)"
    //splits onto extra lines at the message limit
    //the color and style settings as chat codes
    private static String styleCodes() {
        StringBuilder codes = new StringBuilder();
        TextFormatting color = TextFormatting.getValueByName(ConfigManager.INSTANCE.getColor());
        if (color != null) {
            codes.append(color);
        }
        if (ConfigManager.INSTANCE.isBold()) {
            codes.append(TextFormatting.BOLD);
        }
        if (ConfigManager.INSTANCE.isItalic()) {
            codes.append(TextFormatting.ITALIC);
        }
        if (ConfigManager.INSTANCE.isUnderline()) {
            codes.append(TextFormatting.UNDERLINE);
        }
        return codes.toString();
    }

    public static void printSign(String translation, String langName, String hoverText) {
        String tag = SIGN_TAG + " ";
        String suffix = " §7(" + langName + ")";
        String style = styleCodes();
        int budget = 256 - ("[SIGN] ".length() + (" (" + langName + ")").length() + style.length());
        if (budget < 16)
            budget = 16;
        List<String> chunks = wrapByChars(translation, budget);
        for (int i = 0; i < chunks.size(); i++) {
            boolean last = i == chunks.size() - 1;
            //reset before the language so it doesnt get bolded too
            printFormattedLine(tag + style + chunks.get(i) + (last ? "§r" + suffix : ""), hoverText);
        }
    }

    public static void printTranslation(String coloredHeader, String translation, String langName, String hoverText) {
        if (coloredHeader == null)
            coloredHeader = "";
        String tag = "§r " + RTTM_TAG + " ";
        String suffix = " §7(" + langName + ")";
        //count visible characters only so we break at the message limit not the chat width
        int headerVisible = stripColors(coloredHeader).length();
        int fixedVisible = headerVisible + " [RTTM] ".length() + (" (" + langName + ")").length();
        int budget = 256 - fixedVisible;
        if (budget < 16)
            budget = 16;
        List<String> chunks = wrapByChars(translation, budget);
        for (int i = 0; i < chunks.size(); i++) {
            boolean last = i == chunks.size() - 1;
            printFormattedLine(coloredHeader + tag + chunks.get(i) + (last ? suffix : ""), hoverText);
        }
    }

    private static void printFormattedLine(String text, String hoverText) {
        Style style = new Style();
        if (hoverText != null)
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(hoverText)));
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentString(text).setStyle(style));
    }

    //takes the first N visible characters of a colored string, color codes included
    //used to rebuild the sender's header for the translation
    public static String coloredPrefix(String formatted, int visibleLen) {
        if (formatted == null)
            return null;
        int count = 0;
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            if (c == '§' && i + 1 < formatted.length()) {
                i++;    //skip the code char
                continue;
            }
            count++;
            if (count == visibleLen)
                return formatted.substring(0, i + 1);
        }
        return formatted;
    }

    private static String stripColors(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static List<String> wrapByChars(String text, int budget) {
        List<String> out = new ArrayList<>();
        text = text.trim();
        if (text.length() <= budget) {
            out.add(text);
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() == 0) {
                while (word.length() > budget) {
                    out.add(word.substring(0, budget));
                    word = word.substring(budget);
                }
                line.append(word);
            } else if (line.length() + 1 + word.length() <= budget) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                while (word.length() > budget) {
                    out.add(word.substring(0, budget));
                    word = word.substring(budget);
                }
                line.append(word);
            }
        }
        if (line.length() > 0)
            out.add(line.toString());
        return out;
    }

    public static void printCredits() {
        ChatUtil.printChatMessage(false, TranslationMod.MOD_NAME + " by Ringosham. Version " + TranslationMod.MOD_VERSION, TextFormatting.AQUA);
        ChatUtil.printChatMessage(false, "MCO-specific fixes by _semilight", TextFormatting.AQUA);
        ChatUtil.printChatMessage(false, "Online translation services powered by Google", TextFormatting.AQUA);
        ChatUtil.printChatMessage(false, "Translation results may not be 100% accurate", TextFormatting.AQUA);
    }
}
