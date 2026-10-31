package com.flapjackpants.stacksquing.client;

import com.flapjackpants.stacksquing.config.ModConfig;
import com.flapjackpants.stacksquing.core.CommandProcessor;
import com.flapjackpants.stacksquing.core.GroupStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StackSquingClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("stacksquing");
    private static final List<String> HISTORY = new ArrayList<>();

    private static ModConfig config;
    private static GroupStore groups;
    private static CommandProcessor state;

    public static CommandProcessor state() {
        return state;
    }

    public static ModConfig config() {
        return config;
    }

    public static GroupStore groups() {
        return groups;
    }

    public static List<String> history() {
        return HISTORY;
    }

    public static Path listFolder() {
        return config().resolvedListFolder(FabricLoader.getInstance().getConfigDir());
    }

    public static void saveConfig() {
        try {
            config.save(configFile());
        } catch (IOException e) {
            LOGGER.error("Failed to save stackSquing config", e);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("stacksquing.json");
    }

    @Override
    public void onInitializeClient() {
        config = ModConfig.load(configFile());
        groups = new GroupStore(config.resolvedGroupsFile());
        state = new CommandProcessor(config, StackSquingClient::saveConfig, groups, StackSquingClient::listFolder);

        KeyMapping openBook = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stacksquing.open_book", InputConstants.KEY_B,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("stacksquing", "main"))));
        ContainerBook.register(openBook);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openBook.consumeClick()) {
                if (client.gui.screen() == null) {
                    state.onOpen();
                    client.gui.setScreen(new BookScreen());
                }
            }
        });
    }
}
