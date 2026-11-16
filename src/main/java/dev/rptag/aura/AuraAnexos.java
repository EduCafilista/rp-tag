package dev.rptag.aura;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * (5.60.0) A AURA GUARDADA NO JOGADOR (0 = desligada, 1 = iluminacao).
 *
 * <p>E um anexo do NeoForge: salva no mundo, sobrevive a morte ({@code copyOnDeath}) e o valor
 * chega sozinho em quem ve o jogador — por isso a aura aparece pra todo mundo, e nao so na tela
 * de quem ligou.
 */
public final class AuraAnexos {
    public static final DeferredRegister<AttachmentType<?>> TIPOS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, dev.rptag.RPTagMod.MODID);

    public static final Supplier<AttachmentType<Integer>> AURA = TIPOS.register("aura", () ->
            AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /**
     * (5.62.0) FORMA MACACO: fica no jogador (sem sync — quem usa e so o servidor).
     * O {@code MacacoForm} reaplica os modificadores sozinho a cada 2 s enquanto estiver ligada.
     */
    public static final Supplier<AttachmentType<Boolean>> MACACO = TIPOS.register("macaco", () ->
            AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    private AuraAnexos() {}

    /** Atalho: a aura esta ligada neste jogador? */
    public static boolean ligada(net.minecraft.world.entity.player.Player p) {
        return p.getData(AURA.get()) == AuraEstilo.LIGADA;
    }
}
