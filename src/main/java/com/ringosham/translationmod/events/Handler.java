package com.ringosham.translationmod.events;

import com.ringosham.translationmod.common.ChatUtil;
import com.ringosham.translationmod.common.ConfigManager;
import com.ringosham.translationmod.common.Log;
import com.ringosham.translationmod.gui.TranslateGui;
import com.ringosham.translationmod.translate.SignTranslate;
import com.ringosham.translationmod.translate.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;


public class Handler {
    //ignore repeat hits on the same sign for this long (3 seconds)
    private static final long REPEAT_COOLDOWN_MS = 3000L;

    private boolean hintShown = false;
    private BlockPos lastSignPos;
    private long lastSignTime;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.getGui() == null) {
            Keyboard.enableRepeatEvents(false);
        }
    }

    @SubscribeEvent
    public void chatReceived(ClientChatReceivedEvent event) {
        ITextComponent eventMessage = event.getMessage();
        String message = eventMessage.getUnformattedText().replaceAll("§(.)", "");
        //keep the colored version too, so the translation can reuse the senders colors
        String formatted = eventMessage.getFormattedText();
        Thread translate = new Translator(message, formatted, null, ConfigManager.INSTANCE.getTargetLanguage());
        translate.start();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player == null)
            return;
        if (!hintShown) {
            hintShown = true;
            ChatUtil.printChatMessage(true, "Press [" + TextFormatting.AQUA + Keyboard.getKeyName(KeyBind.translateKey.getKeyCode()) + TextFormatting.WHITE + "] for translation settings", TextFormatting.WHITE);
            if (ConfigManager.INSTANCE.getRegexList().size() == 0) {
                Log.logger.warn("No chat regex in the configurations");
                ChatUtil.printChatMessage(true, "The mod needs chat regex to function. Check the mod options to add one", TextFormatting.RED);
            }
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        tryTranslateSign(event.getWorld(), event.getPos());
    }

    //both right and left click work
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        tryTranslateSign(event.getWorld(), event.getPos());
    }

    private void tryTranslateSign(World world, BlockPos pos) {
        if (!ConfigManager.INSTANCE.isTranslateSign()) {
            return;
        }
        //client side only
        if (world == null || pos == null || !world.isRemote) {
            return;
        }
        Block block = world.getBlockState(pos).getBlock();
        if (block != Blocks.STANDING_SIGN && block != Blocks.WALL_SIGN) {
            return;
        }
        //held buttons spam every tick and right click fires once per hand, so cooldown it
        long now = System.currentTimeMillis();
        if (pos.equals(lastSignPos) && now - lastSignTime < REPEAT_COOLDOWN_MS) {
            return;
        }
        lastSignPos = pos;
        lastSignTime = now;
        SignTranslate translate = getSignThread(world, pos);
        if (translate != null) {
            translate.start();
        }
    }

    //If config is somehow changed through other means
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        ConfigManager.INSTANCE.saveConfig();
    }

    @SubscribeEvent
    public void onKeybind(InputEvent.KeyInputEvent event) {
        if (KeyBind.translateKey.isPressed())
            Minecraft.getMinecraft().displayGuiScreen(new TranslateGui());
    }


    private SignTranslate getSignThread(World world, BlockPos pos) {
        //the tile entity might not be synced yet, which would throw out of the handler
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntitySign)) {
            return null;
        }
        TileEntitySign sign = (TileEntitySign) tile;
        StringBuilder text = new StringBuilder();
        //Four lines of text in signs
        for (int i = 0; i < 4; i++) {
            ITextComponent line = sign.signText[i];
            if (line == null) {
                continue;
            }
            //Combine each line of the sign with spaces.
            //Due to differences between languages, this may break asian languages. (Words don't separate with spaces)
            text.append(" ").append(line.getUnformattedText().replaceAll("§(.)", ""));
        }
        //trim() because the loop adds a space per line, so blank signs looked non-empty
        String signText = text.toString().replaceAll("§(.)", "").trim();
        if (signText.isEmpty()) {
            return null;
        }
        return new SignTranslate(signText, pos);
    }
}
