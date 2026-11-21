package dev.rptag.king;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

public final class KingAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, King.MODID);

    /** Fim da recarga de cada habilidade (7 valores, em game time), só para a roda mostrar. Não persiste. */
    public static final Supplier<AttachmentType<List<Integer>>> COOLDOWNS = TYPES.register("king_cd", () ->
            AttachmentType.<List<Integer>>builder(() -> List.of(0, 0, 0, 0, 0, 0, 0))
                    .sync(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
                    .build());

    private KingAttachments() {}
}
