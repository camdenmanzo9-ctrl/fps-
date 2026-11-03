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
        private static final List<String> activeHand = new ArrayList<>();

        public static String itemAName = "Diamond Chestplate";
        public static String itemBName = "Diamond Helmet";
        public static String stringTarget = "Diamond Chestplate";
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
                    client.setScreen(new MatrixHubScreen());
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

    public static final class MatrixHubScreen extends Screen {
        private static final int MAX_BUTTON_WIDTH = 200;
        private static final int SCREEN_MARGIN = 20;
        private static final int BUTTON_HEIGHT = 20;
        private static final int BUTTON_SPACING = 24;
        private static final int WHITE = 0xFFFFFFFF;
        private static final int GOLD = 0xFFFFAA00;

        private String statusMessage = "";

        public MatrixHubScreen() {
            super(Component.literal("=== 50-50 MATRIX HUB ==="));
        }

        @Override
        protected void init() {
            // Recalculated by Minecraft whenever the window or GUI scale changes.
            int buttonWidth = Math.min(MAX_BUTTON_WIDTH, this.width - SCREEN_MARGIN * 2);
            int x = (this.width - buttonWidth) / 2;
            int y = (this.height - (BUTTON_SPACING * 2 + BUTTON_HEIGHT)) / 2;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Link Machine Block"),
                            button -> linkMachineBlock())
                    .bounds(x, y, buttonWidth, BUTTON_HEIGHT)
                    .build());

            this.addRenderableWidget(Button.builder(
                            Component.literal("Import Item A From Hand"),
                            button -> importItemFromHand(true))
                    .bounds(x, y + BUTTON_SPACING, buttonWidth, BUTTON_HEIGHT)
                    .build());

            this.addRenderableWidget(Button.builder(
                            Component.literal("Import Item B From Hand"),
                            button -> importItemFromHand(false))
                    .bounds(x, y + BUTTON_SPACING * 2, buttonWidth, BUTTON_HEIGHT)
                    .build());
        }

        private void linkMachineBlock() {
            HitResult hitResult = this.minecraft.hitResult;

            if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
                VirtualHandManager.linkedPos = blockHit.getBlockPos().immutable();
                statusMessage = "Linked: " + VirtualHandManager.formatPos(VirtualHandManager.linkedPos);
            } else {
                statusMessage = "No block in crosshair";
            }
        }

        private void importItemFromHand(boolean isItemA) {
            if (this.minecraft.player == null) {
                return;
            }

            ItemStack stack = this.minecraft.player.getMainHandItem();
            if (stack.isEmpty()) {
                statusMessage = "Main hand is empty";
                return;
            }

            String name = stack.getHoverName().getString().replaceFirst(" Log$", "");

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
            graphics.text(this.font, titleText, centeredX(titleText), SCREEN_MARGIN, GOLD, true);

            if (!statusMessage.isEmpty()) {
                int statusY = (this.height + BUTTON_SPACING * 2 + BUTTON_HEIGHT) / 2 + 10;
                graphics.text(this.font, statusMessage, centeredX(statusMessage), statusY, WHITE, true);
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
        // Long enough to cover normal server lag, short enough to recover from a rejected click.
        private static final long PENDING_TIMEOUT_MS = 1000L;

        private static boolean waitingForRelease = false;
        private static BlockPos pendingLever = null;
        private static long pendingSinceMs = 0L;

        private MechanicalInterfaceDetector() {
        }

        private static void register() {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (!client.options.keyUse.isDown()) {
                    waitingForRelease = false;
                }
            });

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
                BlockPos clickedPos = hitResult.getBlockPos();
                BlockState state = level.getBlockState(clickedPos);

                if (linkedPos == null
                        || !(state.getBlock() instanceof LeverBlock)
                        || clickedPos.distManhattan(linkedPos) != 1) {
                    return InteractionResult.PASS;
                }

                // A click on a powered lever turns it off, so the next ON click is a new flick.
                if (state.getValue(BlockStateProperties.POWERED)) {
                    if (clickedPos.equals(pendingLever)) {
                        pendingLever = null;
                    }
                    return InteractionResult.PASS;
                }

                long now = System.currentTimeMillis();
                boolean awaitingServerUpdate = clickedPos.equals(pendingLever)
                        && now - pendingSinceMs < PENDING_TIMEOUT_MS;

                // Holding right-click repeats this event, so one press can count only once.
                if (waitingForRelease || awaitingServerUpdate) {
                    return InteractionResult.PASS;
                }

                VirtualHandManager.activeHand.add(VirtualHandManager.stringTarget);
                waitingForRelease = true;
                pendingLever = clickedPos.immutable();
                pendingSinceMs = now;

                return InteractionResult.PASS;
            });
        }
    }
}
