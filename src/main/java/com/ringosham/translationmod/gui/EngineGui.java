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

package com.ringosham.translationmod.gui;

import com.ringosham.translationmod.TranslationMod;
import com.ringosham.translationmod.common.ChatUtil;
import com.ringosham.translationmod.common.ConfigManager;
import com.ringosham.translationmod.common.Log;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.client.Minecraft.getMinecraft;

public class EngineGui extends CommonGui {
    private static final int guiWidth = 300;
    private static final int guiHeight = 185;
    private static final String title;

    private static final List<String> googleTooltip = new ArrayList<>();
    private static final List<String> baiduTooltip = new ArrayList<>();
    private static final List<String> selfHostedTooltip = new ArrayList<>();

    static {
        title = TranslationMod.MOD_NAME + " - Engine options";
        googleTooltip.add("By default, you are using the \"free\" version of Google translation");
        googleTooltip.add("This is the same API the Google translate website is using");
        googleTooltip.add("However, too many requests and Google will block you for a few minutes");
        googleTooltip.add("Cloud translation API is the paid version of Google translate");
        googleTooltip.add("Please check the mod page for details");
        baiduTooltip.add("If you cannot use Google due to country restrictions,");
        baiduTooltip.add("Baidu is your second option");
        baiduTooltip.add("An account is needed to use this API (Phone verification required)");
        baiduTooltip.add("Free tier only allows 1 request per second");
        baiduTooltip.add("Paying allows for more requests per second");
        baiduTooltip.add("Please check the mod page for details");
        selfHostedTooltip.add("Translations powered by LibreTranslate and NLLB-200,");
        selfHostedTooltip.add("running on _semilight's lserv box");
        selfHostedTooltip.add("");
        selfHostedTooltip.add(TextFormatting.GRAY + "Not a shared public API, so it cannot be rate-limited");
        selfHostedTooltip.add(TextFormatting.GRAY + "out from under everyone the way Google was");
    }

    private String engine;
    private GuiTextField googleKeyBox;
    private GuiTextField baiduKeyBox;
    private GuiTextField baiduAppIdBox;
    private GuiTextField apiKeyBox;

    EngineGui() {
        super(guiHeight, guiWidth);
        engine = ConfigManager.INSTANCE.getTranslationEngine();
    }

    @Override
    public void drawScreen(int x, int y, float tick) {
        super.drawScreen(x, y, tick);
        //say the fallback is running, the buttons still show what you picked
        drawStringLine(title, new String[]{
                "Please choose your translation engine",
                ConfigManager.INSTANCE.isSessionFallbackActive()
                        ? TextFormatting.YELLOW + "Google stopped responding, using NLLB until you restart"
                        : "The mod can only use one of them"
        }, 5);
        switch (engine) {
            case "google":
                fontRenderer.drawString("Cloud platform API key", getLeftMargin(), getYOrigin() + 75, 0x555555);
                googleKeyBox.drawTextBox();
                fontRenderer.drawString("Delete/Leave empty to use the free API", getLeftMargin(), getYOrigin() + 110, 0x555555);
                break;
            case "selfhosted":
                //the address is fixed so it's just stated, key is editable in case it leaks and needs to be changed
                fontRenderer.drawString("Translations powered by LibreTranslate and NLLB-200",
                        getLeftMargin(), getYOrigin() + 66, 0x555555);
                fontRenderer.drawString("from _semilight's lserv box.",
                        getLeftMargin(), getYOrigin() + 78, 0x555555);
                fontRenderer.drawString("API key", getLeftMargin(), getYOrigin() + 100, 0x555555);
                apiKeyBox.drawTextBox();
                fontRenderer.drawString("Do not change this unless you know what you are doing.",
                        getLeftMargin(), getYOrigin() + 136, 0x777777);
                break;
            case "baidu":
                fontRenderer.drawString("Baidu developer App ID", getLeftMargin(), getYOrigin() + 65, 0x555555);
                baiduAppIdBox.drawTextBox();
                fontRenderer.drawString("Baidu API key", getLeftMargin(), getYOrigin() + 95, 0x555555);
                baiduKeyBox.drawTextBox();
                break;
        }
        if (this.buttonList.get(0).isMouseOver())
            drawHoveringText(googleTooltip, x, y);
        if (this.buttonList.get(1).isMouseOver())
            drawHoveringText(baiduTooltip, x, y);
        if (this.buttonList.size() > 4 && this.buttonList.get(4).isMouseOver())
            drawHoveringText(selfHostedTooltip, x, y);

    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.googleKeyBox = new GuiTextField(0, this.fontRenderer, getLeftMargin(), getYOrigin() + 90, guiWidth - 10, 15);
        googleKeyBox.setCanLoseFocus(true);
        googleKeyBox.setMaxStringLength(84);
        googleKeyBox.setEnableBackgroundDrawing(true);
        googleKeyBox.setText(ConfigManager.INSTANCE.getGoogleKey());
        this.baiduAppIdBox = new GuiTextField(1, this.fontRenderer, getLeftMargin(), getYOrigin() + 75, guiWidth - 10, 15);
        baiduAppIdBox.setCanLoseFocus(true);
        baiduAppIdBox.setMaxStringLength(20);
        baiduAppIdBox.setEnableBackgroundDrawing(true);
        baiduAppIdBox.setText(ConfigManager.INSTANCE.getBaiduAppId());
        this.baiduKeyBox = new GuiTextField(2, this.fontRenderer, getLeftMargin(), getYOrigin() + 105, guiWidth - 10, 15);
        baiduKeyBox.setCanLoseFocus(true);
        baiduKeyBox.setEnableBackgroundDrawing(true);
        baiduKeyBox.setMaxStringLength(24);
        baiduKeyBox.setText(ConfigManager.INSTANCE.getBaiduKey());
        //labels sit 10px above each box and the buttons are at the bottom, so keep the last box above them
        this.apiKeyBox = new GuiTextField(5, this.fontRenderer, getLeftMargin(), getYOrigin() + 110, guiWidth - 10, 15);
        apiKeyBox.setCanLoseFocus(true);
        apiKeyBox.setMaxStringLength(120);
        apiKeyBox.setEnableBackgroundDrawing(true);
        apiKeyBox.setText(ConfigManager.INSTANCE.getSelfHostedApiKey());

        int engineButtonWidth = (guiWidth - 20) / 3;
        this.buttonList.add(new GuiButton(0, getLeftMargin(), getYOrigin() + 40, engineButtonWidth, regularButtonHeight, "Google"));
        this.buttonList.add(new GuiButton(1, getLeftMargin() + engineButtonWidth + 5, getYOrigin() + 40, engineButtonWidth, regularButtonHeight, "Baidu"));
        this.buttonList.add(new GuiButton(2, getRightMargin(regularButtonWidth), getYOrigin() + guiHeight - regularButtonHeight - 5, regularButtonWidth, regularButtonHeight, "Apply and close"));
        this.buttonList.add(new GuiButton(3, getRightMargin(regularButtonWidth) - regularButtonWidth - 5, getYOrigin() + guiHeight - regularButtonHeight - 5, regularButtonWidth, regularButtonHeight, "Back"));
        //added last so the tooltip indices stay right
        this.buttonList.add(new GuiButton(4, getLeftMargin() + 2 * (engineButtonWidth + 5), getYOrigin() + 40, engineButtonWidth, regularButtonHeight, "NLLB"));
        switch (engine) {
            case "google":
                this.buttonList.get(0).enabled = false;
                break;
            case "baidu":
                this.buttonList.get(1).enabled = false;
                break;
            case "selfhosted":
                this.buttonList.get(4).enabled = false;
                break;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                engine = "google";
                this.buttonList.get(0).enabled = false;
                this.buttonList.get(1).enabled = true;
                this.buttonList.get(4).enabled = true;
                break;
            case 1:
                engine = "baidu";
                this.buttonList.get(0).enabled = true;
                this.buttonList.get(1).enabled = false;
                this.buttonList.get(4).enabled = true;
                break;
            case 2:
                applyKey();
                break;
            case 4:
                engine = "selfhosted";
                this.buttonList.get(0).enabled = true;
                this.buttonList.get(1).enabled = true;
                this.buttonList.get(4).enabled = false;
                break;
            case 3:
                configGui();
                break;
        }
        super.actionPerformed(button);
    }

    //boxes for the other engines aren't drawn but still sit in the same rows, so only the visible tab gets clicks
    private GuiTextField[] activeBoxes() {
        switch (engine) {
            case "google":
                return new GuiTextField[]{googleKeyBox};
            case "baidu":
                return new GuiTextField[]{baiduAppIdBox, baiduKeyBox};
            case "selfhosted":
                return new GuiTextField[]{apiKeyBox};
            default:
                return new GuiTextField[0];
        }
    }

    //These methods need to be overridden. Otherwise, Textboxes don't work.
    @Override
    public void mouseClicked(int x, int y, int state) throws IOException {
        super.mouseClicked(x, y, state);
        for (GuiTextField box : activeBoxes()) {
            box.mouseClicked(x, y, state);
        }
    }

    @Override
    public void keyTyped(char typedchar, int keycode) throws IOException {
        for (GuiTextField box : activeBoxes()) {
            if (box.isFocused()) {
                box.textboxKeyTyped(typedchar, keycode);
            }
        }
        super.keyTyped(typedchar, keycode);
    }

    private void configGui() {
        Keyboard.enableRepeatEvents(false);
        getMinecraft().displayGuiScreen(new ConfigGui());
    }

    private void applyKey() {
        Keyboard.enableRepeatEvents(true);
        ConfigManager.INSTANCE.setGoogleKey(googleKeyBox.getText());
        ConfigManager.INSTANCE.setBaiduAppId(baiduAppIdBox.getText());
        ConfigManager.INSTANCE.setBaiduKey(baiduKeyBox.getText());
        //remove a trailing space
        ConfigManager.INSTANCE.setSelfHostedApiKey(apiKeyBox.getText().trim());
        ConfigManager.INSTANCE.setTranslationEngine(engine);
        ConfigManager.INSTANCE.saveConfig();
        Log.logger.info("Saved engine options");
        ChatUtil.printChatMessage(true, "New translation engine options have been applied.", TextFormatting.WHITE);
        getMinecraft().displayGuiScreen(null);
    }
}
