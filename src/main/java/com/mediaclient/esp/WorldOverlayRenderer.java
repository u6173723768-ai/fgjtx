package com.mediaclient.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mediaclient.MediaClient;
import com.mediaclient.activity.ActivityDetector;
import com.mediaclient.activity.ActivityEvent;
import com.mediaclient.config.MediaConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Map;

public final class WorldOverlayRenderer {
    private static final Minecraft CLIENT = Minecraft.getInstance();
    private WorldOverlayRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(WorldOverlayRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (CLIENT.level == null || CLIENT.player == null) return;
        MediaConfig config = MediaConfig.get();
        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack matrices = context.matrices();
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.LINES);
        // Bound block geometry per frame so dense ESP areas cannot flood the render buffer.
        int[] boxBudget = {256};

        if (MediaClient.isModuleEnabled("player_esp")) {
            double rangeSq = config.playerRange * (double) config.playerRange;
            for (Player player : CLIENT.level.players()) {
                if (player == CLIENT.player || player.isRemoved() || player.distanceToSqr(CLIENT.player) > rangeSq) continue;
                if (config.ignoreInvisiblePlayers && player.isInvisible()) continue;
                String name = player.getName().getString();
                boolean friend = config.friendNames.stream().anyMatch(n -> n != null && n.equalsIgnoreCase(name));
                if (config.hideFriends && friend) continue;
                if (config.hideTeammates && CLIENT.player.getTeam() != null && CLIENT.player.getTeam() == player.getTeam()) continue;
                int color = friend && config.useFriendColor ? config.friendColor : config.playerColor;
                if (config.playerBox || config.playerOutline) {
                    if (config.playerEsp2D) draw2DBox(matrices.last(), lines, player, color);
                    else drawBox(matrices.last(), lines, player.getBoundingBox().inflate(0.04), color);
                }
                if (config.playerName) {
                    StringBuilder label = new StringBuilder(name);
                    if (config.playerDistance) label.append(String.format(" | %.1fm", Math.sqrt(player.distanceToSqr(CLIENT.player))));
                    if (config.playerHealth) label.append(String.format(" | HP %.0f", player.getHealth()));
                    if (config.playerArmor) label.append(" | Armor ").append(armorValue(player)).append("/4");
                    if (config.playerHeldItems) {
                        if (!player.getMainHandItem().isEmpty()) label.append(" | ").append(player.getMainHandItem().getHoverName().getString());
                        if (!player.getOffhandItem().isEmpty()) label.append(" / ").append(player.getOffhandItem().getHoverName().getString());
                    }
                    context.commandQueue().submitNameTag(matrices, player.position().add(0, player.getBbHeight() + 0.25, 0), 0,
                            Component.literal(label.toString()), false, 0x50000000, player.distanceToSqr(CLIENT.player), context.worldState().cameraRenderState);
                }
            }
        }
        if (MediaClient.isModuleEnabled("item_esp") && boxBudget[0] > 0) {
            double itemRange = Math.min(config.playerRange, 128);
            double rangeSq = itemRange * itemRange;
            int drawnItems = 0;
            for (ItemEntity item : CLIENT.level.getEntitiesOfClass(ItemEntity.class,
                    CLIENT.player.getBoundingBox().inflate(itemRange))) {
                if (drawnItems >= 64 || boxBudget[0] <= 0) break;
                if (item.isRemoved() || item.distanceToSqr(CLIENT.player) > rangeSq) continue;
                drawBox(matrices.last(), lines, item.getBoundingBox().inflate(0.06), 0xFFFFD166);
                drawnItems++;
                boxBudget[0]--;
            }
        }

        if (MediaClient.isModuleEnabled("chest_esp")) {
            double rangeSq = config.containerRange * (double) config.containerRange;
            for (Map.Entry<Long, BlockState> entry : MediaClient.ACTIVITY_DETECTOR.indexedContainers().entrySet()) {
                if (boxBudget[0] <= 0) break;
                BlockPos pos = BlockPos.of(entry.getKey());
                if (pos.distToCenterSqr(CLIENT.player.position()) <= rangeSq) {
                    drawBlockBox(matrices.last(), lines, pos, config.containerColor);
                    boxBudget[0]--;
                }
            }
        }
        if (MediaClient.isModuleEnabled("machine_esp")) {
            double rangeSq = config.machineRange * (double) config.machineRange;
            for (Map.Entry<Long, BlockState> entry : MediaClient.ACTIVITY_DETECTOR.indexedMachines().entrySet()) {
                if (boxBudget[0] <= 0) break;
                BlockPos pos = BlockPos.of(entry.getKey());
                if (pos.distToCenterSqr(CLIENT.player.position()) <= rangeSq) {
                    drawBlockBox(matrices.last(), lines, pos, config.machineColor);
                    boxBudget[0]--;
                }
            }
        }
        if (MediaClient.isModuleEnabled("activity_esp")) {
            for (ActivityEvent event : MediaClient.ACTIVITY_DETECTOR.currentEvents()) {
                if (boxBudget[0] <= 0) break;
                BlockPos pos = event.pos();
                double r = config.activityRadius * 2.0;
                if (pos.distToCenterSqr(CLIENT.player.position()) <= r * r) {
                    drawBlockBox(matrices.last(), lines, pos, colorForEvent(event, config));
                    boxBudget[0]--;
                }
            }
        }
        matrices.popPose();
    }

    private static int armorValue(Player p) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) if (!p.getItemBySlot(s).isEmpty()) n++;
        return n;
    }
    private static int colorForEvent(ActivityEvent e, MediaConfig c) {
        String id=e.blockId();
        if (id.contains("amethyst")) return 0xFFB48DFF;
        if (id.contains("kelp") || id.contains("crop") || id.contains("wheat") || id.contains("potato") || id.contains("carrot")) return 0xFF71E6A2;
        if (ActivityDetector.isContainerId(id)) return c.containerColor;
        if (id.contains("piston") || id.contains("dispenser") || id.contains("dropper") || id.contains("redstone") || id.contains("hopper")) return c.machineColor;
        return c.activityColor;
    }
    private static void drawBlockBox(PoseStack.Pose pose, VertexConsumer lines, BlockPos pos, int color) {
        drawBox(pose, lines, new AABB(pos.getX()+0.02,pos.getY()+0.02,pos.getZ()+0.02,pos.getX()+0.98,pos.getY()+0.98,pos.getZ()+0.98), color);
    }
    private static void draw2DBox(PoseStack.Pose pose, VertexConsumer lines, Player p, int color) {
        double yaw=Math.toRadians(p.getYRot()), dx=Math.cos(yaw)*0.36, dz=Math.sin(yaw)*0.36;
        double x=p.getX(), z=p.getZ(), bottom=p.getY(), top=bottom+p.getBbHeight();
        line(pose,lines,x-dx,bottom,z-dz,x+dx,bottom,z+dz,color);
        line(pose,lines,x-dx,top,z-dz,x+dx,top,z+dz,color);
        line(pose,lines,x-dx,bottom,z-dz,x-dx,top,z-dz,color);
        line(pose,lines,x+dx,bottom,z+dz,x+dx,top,z+dz,color);
    }
    private static void drawBox(PoseStack.Pose p, VertexConsumer c, AABB b, int color) {
        double x1=b.minX,y1=b.minY,z1=b.minZ,x2=b.maxX,y2=b.maxY,z2=b.maxZ;
        line(p,c,x1,y1,z1,x2,y1,z1,color); line(p,c,x1,y1,z2,x2,y1,z2,color);
        line(p,c,x1,y2,z1,x2,y2,z1,color); line(p,c,x1,y2,z2,x2,y2,z2,color);
        line(p,c,x1,y1,z1,x1,y1,z2,color); line(p,c,x2,y1,z1,x2,y1,z2,color);
        line(p,c,x1,y2,z1,x1,y2,z2,color); line(p,c,x2,y2,z1,x2,y2,z2,color);
        line(p,c,x1,y1,z1,x1,y2,z1,color); line(p,c,x2,y1,z1,x2,y2,z1,color);
        line(p,c,x1,y1,z2,x1,y2,z2,color); line(p,c,x2,y1,z2,x2,y2,z2,color);
    }
    private static void line(PoseStack.Pose pose, VertexConsumer consumer, double x1,double y1,double z1,double x2,double y2,double z2,int argb) {
        int a=(argb>>>24)&255,r=(argb>>>16)&255,g=(argb>>>8)&255,b=argb&255;
        consumer.addVertex(pose,(float)x1,(float)y1,(float)z1).setColor(r,g,b,a).setNormal(pose,0.0F,1.0F,0.0F);
        consumer.addVertex(pose,(float)x2,(float)y2,(float)z2).setColor(r,g,b,a).setNormal(pose,0.0F,1.0F,0.0F);
    }
}
