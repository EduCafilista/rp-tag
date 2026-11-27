package dev.rptag.vampire;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class VampireAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Vampire.MODID);

    /** É vampiro? Salvo no jogador, sincronizado (a barra de sangue e o morcego precisam disso no cliente). */
    public static final Supplier<AttachmentType<Boolean>> VAMPIRE = TYPES.register("vampire", () ->
            AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .copyOnDeath()
                    .build());

    /** Está na forma de morcego? Não salva: ao entrar de novo no jogo volta ao normal. */
    public static final Supplier<AttachmentType<Boolean>> BAT = TYPES.register("vampire_bat", () ->
            AttachmentType.builder(() -> false)
                    .sync(ByteBufCodecs.BOOL)
                    .build());

    private VampireAttachments() {}
}
