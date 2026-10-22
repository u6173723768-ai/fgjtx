package com.mediaclient;

import com.mediaclient.config.MediaConfig;
import com.mediaclient.module.ClientModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Client-side utility modules. Aim assistance changes the player's normal visible
 * rotation; this class does not spoof movement packets or hide rotations.
 */
public final class ExtendedModules {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private static KeyMapping zoomKey;
    private static Double originalGamma;
    private static Integer originalFov;
    private static Integer originalFpsLimit;
    private static ParticleStatus originalParticles;
    private static long sessionStartedAt;
    private static long lastAutoEatTick = Long.MIN_VALUE;
    private static long lastPlantTick = Long.MIN_VALUE;
    private static long lastReminderTick = Long.MIN_VALUE;
    private static long lastTotemAlertTick = Long.MIN_VALUE;
    private static long lastTriggerTick = Long.MIN_VALUE;

    private ExtendedModules() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("media_client", "utilities"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.media_client.zoom", net.minecraft.client.InputType.KEYSYM, GLFW.GLFW_KEY_C, category));
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("media_client", "utility_hud"),
                (graphics, tickCounter) -> renderHud(graphics));
    }

    public static void tick(Minecraft client) {
        if (client == null) return;
        applyClientOptions(client);

        if (client.level == null || client.player == null) {
            sessionStartedAt = 0L;
            return;
        }
        if (sessionStartedAt == 0L) sessionStartedAt = System.currentTimeMillis();

        Player player = client.player;
        if (client.screen == null) {
            if (enabled("auto_sprint") && client.options.keyUp.isDown()
                    && !client.options.keyDown.isDown() && !player.isUsingItem()
                    && !player.isPassenger() && player.getFoodData().getFoodLevel() > 6
                    && !player.isSprinting()) {
                player.setSprinting(true);
            }

            if (enabled("auto_respawn") && player.isDeadOrDying()
                    && client.screen instanceof DeathScreen) {
                player.requestRespawn();
            }

            if (enabled("auto_tool") && client.options.keyAttack.isDown()
                    && client.hitResult instanceof BlockHitResult hit && client.gameMode != null) {
                selectBestTool(player, client.level.getBlockState(hit.getBlockPos()));
            }

            if (enabled("auto_eat") && player.getFoodData().getFoodLevel() <= 14
                    && player.getMainHandItem().isEdible() && !player.isUsingItem()
                    && client.gameMode != null
                    && (lastAutoEatTick == Long.MIN_VALUE || client.level.getGameTime() - lastAutoEatTick >= 4)) {
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                lastAutoEatTick = client.level.getGameTime();
            }

            if (enabled("auto_planter") && client.options.keyUse.isDown()
                    && client.hitResult instanceof BlockHitResult hit && client.gameMode != null
                    && (lastPlantTick == Long.MIN_VALUE || client.level.getGameTime() - lastPlantTick >= 4)) {
                BlockState targetState = client.level.getBlockState(hit.getBlockPos());
                ItemStack held = player.getMainHandItem();
                if (targetState.is(Blocks.FARMLAND) && held.getItem() instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof CropBlock) {
                    client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                    lastPlantTick = client.level.getGameTime();
                }
            }

            if (enabled("target_assist") && client.options.keyAttack.isDown()) {
                assistAim(player);
            }

            if (enabled("triggerbot") && client.gameMode != null
                    && client.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof Player target && target != player
                    && !target.isRemoved() && !target.isSpectator()
                    && player.distanceToSqr(target) <= 5.5 * 5.5
                    && player.getAttackStrengthScale(0.5F) >= 0.99F
                    && (lastTriggerTick == Long.MIN_VALUE || client.level.getGameTime() != lastTriggerTick)) {
                client.gameMode.attack(player, target);
                player.swing(InteractionHand.MAIN_HAND);
                lastTriggerTick = client.level.getGameTime();
            }
        }

        if (enabled("durability_guard")) protectToolDurability(player);
        if (enabled("inventory_reminder")) warnLowDurability(player);
        if (enabled("totem_alert")) warnTotem(player);
    }

    private static void applyClientOptions(Minecraft client) {
        if (enabled("fullbright")) {
            if (originalGamma == null) originalGamma = client.options.gamma().get();
            if (client.options.gamma().get() < 15.0) client.options.gamma().set(15.0);
        } else if (originalGamma != null) {
            client.options.gamma().set(originalGamma);
            originalGamma = null;
        }

        if (enabled("fps_cap")) {
            if (originalFpsLimit == null) originalFpsLimit = client.options.framerateLimit().get();
            if (client.options.framerateLimit().get() != 120) client.options.framerateLimit().set(120);
        } else if (originalFpsLimit != null) {
            client.options.framerateLimit().set(originalFpsLimit);
            originalFpsLimit = null;
        }

        if (enabled("low_particles")) {
            if (originalParticles == null) originalParticles = client.options.particles().get();
            if (client.options.particles().get() != ParticleStatus.MINIMAL) {
                client.options.particles().set(ParticleStatus.MINIMAL);
            }
        } else if (originalParticles != null) {
            client.options.particles().set(originalParticles);
            originalParticles = null;
        }

        boolean zooming = enabled("zoom") && zoomKey != null && zoomKey.isDown();
        if (zooming) {
            if (originalFov == null) originalFov = client.options.fov().get();
            if (client.options.fov().get() != 30) client.options.fov().set(30);
        } else if (originalFov != null) {
            client.options.fov().set(originalFov);
            originalFov = null;
        }
    }

    private static void assistAim(Player player) {
        Minecraft client = CLIENT;
        if (client.level == null) return;
        Player best = null;
        float bestScore = Float.MAX_VALUE;
        for (Player candidate : client.level.players()) {
            if (candidate == player || candidate.isRemoved() || candidate.isSpectator()
                    || candidate.isInvisible() || !player.hasLineOfSight(candidate)
                    || player.distanceToSqr(candidate) > 5.5 * 5.5) continue;
            var delta = candidate.getEyePosition().subtract(player.getEyePosition());
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
            float yawDiff = Mth.wrapDegrees(yaw - player.getYRot());
            float pitchDiff = pitch - player.getXRot();
            if (Math.abs(yawDiff) > 18.0F || Math.abs(pitchDiff) > 14.0F) continue;
            float score = Math.abs(yawDiff) + Math.abs(pitchDiff) * 0.7F;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) return;
        var delta = best.getEyePosition().subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        float yawStep = Mth.clamp(Mth.wrapDegrees(desiredYaw - player.getYRot()) * 0.18F, -2.5F, 2.5F);
        float pitchStep = Mth.clamp((desiredPitch - player.getXRot()) * 0.18F, -2.0F, 2.0F);
        player.setYRot(player.getYRot() + yawStep);
        player.setXRot(Mth.clamp(player.getXRot() + pitchStep, -89.0F, 89.0F));
    }

    private static void selectBestTool(Player player, BlockState state) {
        int selected = player.getInventory().selected;
        ItemStack current = player.getInventory().getItem(selected);
        float bestSpeed = current.isEmpty() ? 0.0F : current.getDestroySpeed(state);
        int bestSlot = selected;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.isEmpty()) continue;
            float speed = candidate.getDestroySpeed(state);
            if (speed > bestSpeed + 0.01F) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        if (bestSlot != selected) player.getInventory().selected = bestSlot;
    }

    private static void protectToolDurability(Player player) {
        int selected = player.getInventory().selected;
        ItemStack held = player.getInventory().getItem(selected);
        if (held.isEmpty() || !held.isDamageableItem()) return;
        int remaining = held.getMaxDamage() - held.getDamageValue();
        if ((double) remaining / held.getMaxDamage() > 0.12) return;

        int bestSlot = -1;
        int bestRemaining = remaining;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) continue;
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.isEmpty() || candidate.getItem().getClass() != held.getItem().getClass()
                    || !candidate.isDamageableItem()) continue;
            int candidateRemaining = candidate.getMaxDamage() - candidate.getDamageValue();
            if (candidateRemaining > bestRemaining) {
                bestRemaining = candidateRemaining;
                bestSlot = slot;
            }
        }
        if (bestSlot >= 0 && (double) bestRemaining / player.getInventory().getItem(bestSlot).getMaxDamage() > 0.25) {
            player.getInventory().selected = bestSlot;
            player.displayClientMessage(Component.literal("[Media] Switched to a tool with more durability"), true);
        }
    }

    private static void warnLowDurability(Player player) {
        if (player.getMainHandItem().isEmpty()) return;
        ItemStack held = player.getMainHandItem();
        if (!held.isDamageableItem()) return;
        int remaining = held.getMaxDamage() - held.getDamageValue();
        if ((double) remaining / held.getMaxDamage() >= 0.20) return;
        long now = CLIENT.level.getGameTime();
        if (lastReminderTick == Long.MIN_VALUE || now - lastReminderTick >= 100) {
            player.displayClientMessage(Component.literal("[Media] Low durability: " + held.getHoverName().getString()), true);
            lastReminderTick = now;
        }
    }

    private static void warnTotem(Player player) {
        if (player.getHealth() > 8.0F || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        boolean hasTotem = false;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) { hasTotem = true; break; }
        }
        if (!hasTotem) return;
        long now = CLIENT.level.getGameTime();
        if (lastTotemAlertTick == Long.MIN_VALUE || now - lastTotemAlertTick >= 80) {
            player.displayClientMessage(Component.literal("[Media] Low health: a Totem is in your hotbar but not in your offhand"), true);
            lastTotemAlertTick = now;
        }
    }

    private static void renderHud(net.minecraft.client.gui.GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) return;
        Player player = client.player;
        int y = 8;
        if (enabled("coordinates_hud")) {
            var pos = player.blockPosition();
            graphics.drawString(client.font, String.format(Locale.ROOT, "XYZ %d %d %d", pos.getX(), pos.getY(), pos.getZ()),
                    8, y, 0xFFE5D5FF, true);
            y += 12;
        }
        if (enabled("fps_hud")) {
            graphics.drawString(client.font, "FPS " + client.getFps(), 8, y, 0xFFE5D5FF, true);
            y += 12;
        }
        if (enabled("armor_hud")) {
            StringBuilder armor = new StringBuilder("Armor ");
            boolean first = true;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!first) armor.append(" | ");
                first = false;
                if (stack.isEmpty()) armor.append("--");
                else if (stack.isDamageableItem()) {
                    int remaining = stack.getMaxDamage() - stack.getDamageValue();
                    armor.append(remaining).append('/').append(stack.getMaxDamage());
                } else armor.append(stack.getHoverName().getString());
            }
            graphics.drawString(client.font, armor.toString(), 8, y, 0xFFE5D5FF, true);
            y += 12;
        }
        if (enabled("ping_hud") && client.getConnection() != null) {
            var info = client.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) {
                graphics.drawString(client.font, "Ping " + info.getLatency() + " ms", 8, y, 0xFFE5D5FF, true);
                y += 12;
            }
        }
        if (enabled("session_hud")) {
            long start = sessionStartedAt == 0L ? System.currentTimeMillis() : sessionStartedAt;
            long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
            graphics.drawString(client.font, String.format(Locale.ROOT, "Session %02d:%02d:%02d",
                    elapsedSeconds / 3600, (elapsedSeconds / 60) % 60, elapsedSeconds % 60),
                    8, y, 0xFFE5D5FF, true);
            y += 12;
        }
        if (enabled("schematic_status") && MediaClient.SCHEMATIC_BUILDER != null) {
            graphics.drawString(client.font, "Schematic: " + MediaClient.SCHEMATIC_BUILDER.status(),
                    8, y, 0xFFB88AFF, true);
            y += 12;
        }
        if (enabled("target_hud") && client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target && target != player) {
            graphics.drawString(client.font,
                    String.format(Locale.ROOT, "%s  HP %.1f  %.1fm", target.getName().getString(),
                            target.getHealth(), player.distanceTo(target)),
                    8, y, 0xFFFFC85A, true);
        }
    }

    private static boolean enabled(String id) {
        return MediaClient.isModuleEnabled(id);
    }
}
