package com.ringosham.translationmod.gui;

import com.ringosham.translationmod.TranslationMod;
import com.ringosham.translationmod.client.types.Language;
import com.ringosham.translationmod.common.ConfigManager;
import com.ringosham.translationmod.translate.Retranslate;
import com.ringosham.translationmod.common.LanguageFilter;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class LanguageSelectGui extends CommonGui {
    private static final int guiWidth = 400;
    private static final int guiHeight = 200;
    private final ConfigGui config;
    private final int langSelect;
    private final String sender;
    private final String message;
    private LangList langList;
    //third mode for editing the never-translate list, stays open so you can add multiple
    private final boolean skipListMode;

    //Calling from ConfigGui
    LanguageSelectGui(ConfigGui config, int langSelect) {
        super(guiHeight, guiWidth);
        this.config = config;
        this.langSelect = langSelect;
        this.message = null;
        this.sender = null;
        this.skipListMode = false;
    }

    //from ConfigGui, to edit the never-translate list
    LanguageSelectGui(ConfigGui config) {
        super(guiHeight, guiWidth);
        this.config = config;
        this.langSelect = -1;
        this.message = null;
        this.sender = null;
        this.skipListMode = true;
    }

    //Calling from RetranslateGui
    LanguageSelectGui(String sender, String message) {
        super(guiHeight, guiWidth);
        this.message = message;
        this.sender = sender;
        this.config = null;
        this.langSelect = -1;
        this.skipListMode = false;
    }

    @Override
    public void drawScreen(int x, int y, float tick) {
        super.drawScreen(x, y, tick);
        if (skipListMode) {
            fontRenderer.drawString(TranslationMod.MOD_NAME + " - Never translate", getLeftMargin(), getTopMargin(), 0x555555);
            fontRenderer.drawString(TextFormatting.GREEN + "Green" + TextFormatting.RESET
                            + " = left untranslated in chat. Your target language is always skipped.",
                    getLeftMargin(), getYOrigin() + guiHeight - regularButtonHeight - 18, 0x555555);
        } else {
            fontRenderer.drawString(TranslationMod.MOD_NAME + " - Language select", getLeftMargin(), getTopMargin(), 0x555555);
        }
        langList.drawScreen(x, y, tick);
    }

    @Override
    public void initGui() {
        //the hint line needs its own row above the buttons
        int listBottom = getYOrigin() + guiHeight - 10 - regularButtonHeight - (skipListMode ? 12 : 0);
        langList = new LangList(mc, this, guiWidth - 18, guiHeight - 48, getYOrigin() + 15, listBottom, getLeftMargin(), 15, width, height);
        langList.setMarkSkipped(skipListMode);
        this.buttonList.add(new GuiButton(0, getRightMargin(regularButtonWidth), getYOrigin() + guiHeight - regularButtonHeight - 5, regularButtonWidth, regularButtonHeight,
                skipListMode ? "Add / Remove" : "Select language"));
        this.buttonList.add(new GuiButton(1, getLeftMargin(), getYOrigin() + guiHeight - regularButtonHeight - 5, regularButtonWidth, regularButtonHeight, "Back"));
    }

    @Override
    public void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                if (langList.getSelected() != null) {
                    if (skipListMode)
                        this.toggleSkipped(langList.getSelected());
                    else if (config != null)
                        this.selectLanguage(langList.getSelected());
                    else
                        this.retranslate(langList.getSelected());
                }
                break;
            case 1:
                if (skipListMode)
                    mc.displayGuiScreen(new ConfigGui(config, -1, null));
                else if (config != null)
                    this.selectLanguage(null);
                else
                    mc.displayGuiScreen(new RetranslateGui());
                break;
        }
    }

    private void toggleSkipped(Language lang) {
        List<String> skipped = new ArrayList<>(ConfigManager.INSTANCE.getSkipLanguages());
        String code = lang.getGoogleCode();
        boolean removed = false;
        Iterator<String> it = skipped.iterator();
        while (it.hasNext()) {
            //match the same way the filter does
            if (LanguageFilter.isBlocked(Collections.singletonList(it.next()), code, lang.getName())) {
                it.remove();
                removed = true;
            }
        }
        if (!removed) {
            skipped.add(code);
        }
        ConfigManager.INSTANCE.setSkipLanguages(skipped);
    }

    @SuppressWarnings("ConstantConditions")
    private void selectLanguage(Language lang) {
        mc.displayGuiScreen(new ConfigGui(config, langSelect, lang));
    }

    private void retranslate(Language source) {
        Thread retranslate = new Retranslate(sender, message, source, ConfigManager.INSTANCE.getTargetLanguage());
        retranslate.start();
        mc.displayGuiScreen(null);
    }
}
