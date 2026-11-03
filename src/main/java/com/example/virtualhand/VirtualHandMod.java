package com.example.virtualhand;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class VirtualHandMod implements ClientModInitializer {
    public static final String MOD_ID = "virtualhand";

    @Override
    public void onInitializeClient() {
        VirtualHandManager.register();
        MechanicalInterfaceDetector.register();
    }

    public static final class VirtualHandManager {
        // Private, but accessible to the other classes nested in VirtualHandMod.
        private static final List<String> activeHand = new ArrayList<>();

        public static String itemAName = "Alpha";
        public static String itemBName = "Beta";
        public static String stringTarget = "Alpha";
        public static BlockPos linkedPos = null;

        private static final int HUD_X = 20;
        private static final int HUD_Y = 60;
        private static final int WHITE = 0xFFFFFFFF;
        private static final int YELLOW = 0xFFFFFF55;

        private VirtualHandManager() {
        }

        private static void register() {
            KeyMapping.Category category = KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(MOD_ID, "controls"));

            KeyMapping targetAKey = registerKey("key.virtualhand.target_a", InputConstants.KEY_Z, category);
            KeyMapping targetBKey = registerKey("key.virtualhand.target_b", InputConstants.KEY_X, category);
            KeyMapping clearKey = registerKey("key.virtualhand.clear", InputConstants.KEY_BACKSPACE, category);
            KeyMapping configKey = registerKey("key.virtualhand.config", InputConstants.KEY_G, category);

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                while (targetAKey.consumeClick()) {
                    stringTarget = itemAName;
                }
                while (targetBKey.consumeClick()) {
                    stringTarget = itemBName;
                }
                while (clearKey.consumeClick()) {
                    activeHand.clear();
                }
                while (configKey.consumeClick()) {
                    client.setScreen(new ConfigScreen());
                }
            });

            HudElementRegistry.attachElementBefore(
                    VanillaHudElements.CHAT,
                    Identifier.fromNamespaceAndPath(MOD_ID, "virtual_hand_hud"),
                    VirtualHandManager::extractHud);
        }

        private static KeyMapping registerKey(String translationKey, int keyCode, KeyMapping.Category category) {
            return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    translationKey,
                    InputConstants.Type.KEYSYM,
                    keyCode,
                    category));
        }

        private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
            Font font = Minecraft.getInstance().font;
            int lineHeight = font.lineHeight + 2;
            int y = HUD_Y;

            graphics.text(font, "Target: " + stringTarget, HUD_X, y, YELLOW, true);
            y += lineHeight;
            graphics.text(font, "Linked: " + formatPos(linkedPos), HUD_X, y, WHITE, true);
            y += lineHeight;
            graphics.text(font, "Hand (" + activeHand.size() + "):", HUD_X, y, WHITE, true);
            y += lineHeight;

            for (String entry : activeHand) {
                graphics.text(font, entry, HUD_X, y, WHITE, true);
                y += lineHeight;
            }
        }

        private static String formatPos(BlockPos pos) {
            if (pos == null) {
                return "None";
            }
            return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
    }

    public static final class ConfigScreen extends Screen {
        private static final int BUTTON_WIDTH = 200;
        private static final int BUTTON_HEIGHT = 20;
        private static final int BUTTON_SPACING = 24;
        private static final int WHITE = 0xFFFFFFFF;

        private String statusMessage = "";

        public ConfigScreen() {
            super(Component.literal("Virtual Hand Config"));
        }

        @Override
        protected void init() {
            int x = (this.width - BUTTON_WIDTH) / 2;
            int y = this.height / 2 - 30;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Link Target Block"),
                            button -> linkTargetBlock())
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());

            this.addRenderableWidget(Button.builder(
                            Component.literal("Set Item A From Hand"),
                            button -> setItemFromHand(true))
                    .bounds(x, y + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());

            this.addRenderableWidget(Button.builder(
                            Component.literal("Set Item B From Hand"),
                            button -> setItemFromHand(false))
                    .bounds(x, y + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }

        private void linkTargetBlock() {
            HitResult hitResult = this.minecraft.hitResult;

            if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
                VirtualHandManager.linkedPos = blockHit.getBlockPos().immutable();
                statusMessage = "Linked: " + VirtualHandManager.formatPos(VirtualHandManager.linkedPos);
            } else {
                statusMessage = "No block in crosshair";
            }
        }

        private void setItemFromHand(boolean isItemA) {
            if (this.minecraft.player == null) {
                return;
            }

            ItemStack stack = this.minecraft.player.getMainHandItem();
            if (stack.isEmpty()) {
                statusMessage = "Main hand is empty";
                return;
            }

            String name = stack.getHoverName().getString();

            if (isItemA) {
                // Keep the current target in sync if it pointed at the old name.
                if (VirtualHandManager.stringTarget.equals(VirtualHandManager.itemAName)) {
                    VirtualHandManager.stringTarget = name;
                }
                VirtualHandManager.itemAName = name;
                statusMessage = "Item A: " + name;
            } else {
                if (VirtualHandManager.stringTarget.equals(VirtualHandManager.itemBName)) {
                    VirtualHandManager.stringTarget = name;
                }
                VirtualHandManager.itemBName = name;
                statusMessage = "Item B: " + name;
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);

            String titleText = this.title.getString();
            graphics.text(this.font, titleText, centeredX(titleText), this.height / 2 - 55, WHITE, true);

            if (!statusMessage.isEmpty()) {
                graphics.text(this.font, statusMessage, centeredX(statusMessage), this.height / 2 + 50, WHITE, true);
            }
        }

        private int centeredX(String text) {
            return (this.width - this.font.width(text)) / 2;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    public static final class MechanicalInterfaceDetector {
        private MechanicalInterfaceDetector() {
        }

        private static void register() {
            UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
                // Ignores the integrated server's copy of this event in singleplayer.
                if (player != Minecraft.getInstance().player || hand != InteractionHand.MAIN_HAND) {
                    return InteractionResult.PASS;
                }

                // Sneaking while holding an item, or spectating, won't toggle the lever.
                boolean holdingItem = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
                if (player.isSpectator() || (player.isShiftKeyDown() && holdingItem)) {
                    return InteractionResult.PASS;
                }

                BlockPos linkedPos = VirtualHandManager.linkedPos;
                if (linkedPos == null) {
                    return InteractionResult.PASS;
                }

                BlockPos clickedPos = hitResult.getBlockPos();
                BlockState state = level.getBlockState(clickedPos);

                // This event fires before the toggle, so an unpowered lever is turning ON.
                if (state.getBlock() instanceof LeverBlock
                        && !state.getValue(BlockStateProperties.POWERED)
                        && clickedPos.distManhattan(linkedPos) == 1) {
                    VirtualHandManager.activeHand.add(VirtualHandManager.stringTarget);
                }

                return InteractionResult.PASS;
            });
        }
    }
}
