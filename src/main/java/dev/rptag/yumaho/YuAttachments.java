package dev.rptag.yumaho;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class YuAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, YuMaho.MODID);

    /** Quem tem as habilidades de Yu-Maho (liberado por OP: /yumaho enable). */
    public static final Supplier<AttachmentType<Boolean>> ENABLED = TYPES.register("yu_enabled", () ->
            AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build());

    /** Poder escolhido pelo jogador, 0..100 (%). */
    public static final Supplier<AttachmentType<Integer>> POWER = TYPES.register("yu_power", () ->
            AttachmentType.builder(() -> 50)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .copyOnDeath()
                    .build());

    /** Energia acumulada do Fa Jin, 0..100. Variável separada do poder. Não persiste. */
    public static final Supplier<AttachmentType<Integer>> ENERGY = TYPES.register("yu_energy", () ->
            AttachmentType.builder(() -> 0)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    private YuAttachments() {}
}
