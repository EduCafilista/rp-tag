package dev.loreportals;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRISTAL DE VINCULO — a ferramenta que conecta DOIS portais.
 *
 * <p>Use num nucleo (ele marca o primeiro), depois use no segundo: os dois
 * se conectam com trovao de particulas. Shift+use num nucleo quebra o
 * vinculo dele. Cada jogador tem sua propria marcacao em andamento.</p>
 */
public class CristalVinculoItem extends Item {

    /** Marcacao em andamento: jogador -> chave do primeiro portal. */
    private static final Map<UUID, String> MARCADO = new HashMap<>();

    public CristalVinculoItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Resolve o nucleo: clique DIRETO no nucleo, OU numa MOLDURA do anel
        // (acha o nucleo mais perto — se houver exatamente 1 por perto).
        PortalBlockEntity portal = null;
        if (level.getBlockEntity(pos) instanceof PortalBlockEntity be) {
            portal = be;
        } else if (level.getBlockState(pos).getBlock() instanceof dev.loreportals.PortalFrameBlock) {
            java.util.List<PortalBlockEntity> perto = PortalBlockEntity.nucleosProximos(level, pos, 4);
            if (perto.size() == 1) {
                portal = perto.get(0);
            } else if (perto.size() > 1) {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "\u00a7cHa " + perto.size() + " nucleos por perto \u2014 clica DIRETO no nucleo que quer usar."));
                return InteractionResult.CONSUME;
            }
        }
        if (portal == null) {
            return InteractionResult.PASS;
        }
        MinecraftServer server = level.getServer();
        PortalData data = PortalData.get(server);
        String key = portal.chave();
        UUID id = ctx.getPlayer().getUUID();

        // SHIFT + use = desvincular
        if (ctx.getPlayer().isShiftKeyDown()) {
            if (portal.isLinked()) {
                data.desvincular(key);
                portal.atualizarDoVinculo();
                ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 25, 0.5, 0.8, 0.5, 0.02);
                level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                        SoundSource.BLOCKS, 0.9F, 0.8F);
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "\u00a75\u00bb \u00a77Vinculo quebrado. O portal agora esta sozinho."));
            } else {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "\u00a75\u00bb \u00a77Este portal nao tem vinculo."));
            }
            return InteractionResult.CONSUME;
        }

        // normal = marcar / conectar
        String marcado = MARCADO.get(id);
        if (marcado == null || marcado.equals(key)) {
            MARCADO.put(id, key);
            // REGISTRA o nucleo no dado do mundo AGORA (como PAR solto).
            // Sem isso o vincular() falha: o 1o portal nunca existia no registro!
            if (level instanceof ServerLevel sl) {
                portal.ativarParSolto(sl);
            }
            ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                    pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.05);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 1.0F, 0.7F);
            ctx.getPlayer().sendSystemMessage(Component.literal(
                    "\u00a75\u00bb \u00a7dPortal marcado (" + key + ")"));
            if (key.equals(marcado)) {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "\u00a77(Este e o mesmo portal \u2014 use em OUTRO nucleo pra conectar)"));
            } else {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "\u00a77Agora use o cristal no SEGUNDO nucleo pra conectar os dois."));
            }
            return InteractionResult.CONSUME;
        }

        // tem dois marcados: conectar!
        // (garante que OS DOIS existem no registro antes de vincular)
        if (data.get(key) == null && level instanceof ServerLevel sl) {
            portal.ativarParSolto(sl);
        }
        if (data.get(marcado) == null) {
            MARCADO.remove(id);
            ctx.getPlayer().sendSystemMessage(Component.literal(
                    "\u00a7cA marcacao do 1\u00ba portal se perdeu (portal quebrado ou reinicio). "
                            + "Marca os dois de novo."));
            return InteractionResult.CONSUME;
        }
        if (data.vincular(marcado, key)) {
            refresh(server, marcado);
            refresh(server, key);
            MARCADO.remove(id);
            ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 60, 0.5, 1.0, 0.5, 0.12);
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN,
                    SoundSource.BLOCKS, 0.8F, 1.3F);
            ctx.getPlayer().sendSystemMessage(Component.literal(
                    "\u00a75\u00bb \u00a7d\u00a7lPORTAIS CONECTADOS! \u00a7r\u00a7dAtravesse o circulo pra viajar."));
        } else {
            MARCADO.remove(id);
            ctx.getPlayer().sendSystemMessage(Component.literal(
                    "\u00a7cNao deu pra conectar (portal quebrado ou marcacao invalida). Tenta de novo."));
        }
        return InteractionResult.CONSUME;
    }

    private static void refresh(MinecraftServer server, String key) {
        String[] parts = key.split("\\|", 4);
        if (parts.length != 4) {
            return;
        }
        ServerLevel sl = LorePortalsMod.nivel(server, parts[0]);
        if (sl != null && sl.getBlockEntity(new BlockPos(Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))) instanceof PortalBlockEntity be) {
            be.atualizarDoVinculo();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("\u00a7dUse em 2 nucleos pra conecta-los")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("\u00a77Shift+use num nucleo pra desvincular"));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
