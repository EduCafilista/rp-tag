package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rptag.aura.GravState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;

/**
 * Pés na parede (lado do cliente): com o poder ativo o jogador gruda em paredes e tetos.
 *  - Parede: W sobe, S desce, A/D andam de lado; pular solta (impulso para fora); agachar solta.
 *  - Teto: anda de ponta-cabeça (câmera gira 180°, A/D invertidos); agachar ou pular solta.
 * A gravidade é desligada só para o jogador local enquanto grudado; o modelo gira para todos verem.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class WallWalkClient {
    private static final int[] NX = {0, 0, 1, -1, 0, 0}, NZ = {0, 0, 0, 0, 1, -1};
    private static int detach = 0;
    private static boolean held = false;
    private static float roll = 0f;
    private static boolean pushed = false;

    /** 0 nada, 1 teto, 2..5 parede (normal +X, -X, +Z, -Z). */
    public static int surface(Player p) {
        if (!GravState.wall(GravState.get(p)) || !GravState.isGravity(p)) return 0;
        Level lv = p.level();
        AABB bb = p.getBoundingBox();
        double[][] D = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            if (!lv.noCollision(p, bb.move(D[i][0] * 0.1, 0, D[i][1] * 0.1))) return 2 + i;
        }
        if (!p.onGround() && !lv.noCollision(p, bb.expandTowards(0, 0.1, 0))) return 1;
        return 0;
    }

    private static void release(Player p) {
        if (held) { p.setNoGravity(false); held = false; }
    }

    private static boolean facing(Player p, int surf) {
        Vec3 look = p.getLookAngle();
        return look.x * -NX[surf] + look.z * -NZ[surf] > 0.3;
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        Player p = e.getEntity();
        Input in = e.getInput();
        if (detach > 0) { detach--; release(p); return; }
        int surf = surface(p);
        if (surf == 0) { release(p); return; }
        boolean wall = surf >= 2;
        boolean engaged = held || !p.onGround();
        if (!engaged && wall && in.forwardImpulse > 0 && facing(p, surf)) engaged = true;   // começa a subir do chão
        if (!engaged) return;
        if (in.shiftKeyDown) { detach = 8; release(p); return; }
        if (in.jumping) {
            if (wall) p.setDeltaMovement(NX[surf] * 0.55, 0.45, NZ[surf] * 0.55);
            else p.setDeltaMovement(p.getDeltaMovement().x, -0.3, p.getDeltaMovement().z);
            detach = 10; release(p); in.jumping = false;
            return;
        }
        float yaw = p.getYRot() * ((float) Math.PI / 180f);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw), leftX = Math.cos(yaw), leftZ = Math.sin(yaw);
        float f = in.forwardImpulse, s = in.leftImpulse;
        Vec3 v;
        if (wall) {
            double nx = NX[surf], nz = NZ[surf], tx = -nz, tz = nx;
            double lat = (leftX * tx + leftZ * tz) * s;                    // "esquerda" projetada ao longo da parede
            v = new Vec3(tx * lat * 0.22 - nx * 0.1, f * 0.22, tz * lat * 0.22 - nz * 0.1);
        } else {
            s = -s;                                                        // câmera de ponta-cabeça
            v = new Vec3((fwdX * f + leftX * s) * 0.2, 0.08, (fwdZ * f + leftZ * s) * 0.2);
        }
        p.setDeltaMovement(v);
        p.setNoGravity(true);
        p.fallDistance = 0;
        held = true;
        in.forwardImpulse = 0; in.leftImpulse = 0; in.jumping = false;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        float target = (held && surface(p) == 1) ? 180f : 0f;
        roll += (target - roll) * 0.15f;
        if (Math.abs(roll) > 0.5f) e.setRoll(e.getRoll() + roll);
    }

    // --- o modelo gira para todos verem (teto = de cabeça para baixo; parede = pés na parede)
    @SubscribeEvent
    public static void renderPre(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        pushed = false;
        if (p.onGround()) return;
        int s = surface(p);
        if (s == 0) return;
        PoseStack ps = e.getPoseStack();
        float h = p.getBbHeight();
        ps.pushPose();
        pushed = true;
        if (s == 1) {
            ps.translate(0, h / 2, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(180f));
            ps.translate(0, -h / 2, 0);
        } else {
            float nx = NX[s], nz = NZ[s];
            ps.translate(nx * 0.6f, h / 2, nz * 0.6f);
            ps.mulPose(new Quaternionf().setAngleAxis((float) Math.PI / 2, nz, 0f, -nx));
            ps.translate(0, -h / 2, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderPost(RenderPlayerEvent.Post e) {
        if (pushed) { e.getPoseStack().popPose(); pushed = false; }
    }

    private WallWalkClient() {}
}
