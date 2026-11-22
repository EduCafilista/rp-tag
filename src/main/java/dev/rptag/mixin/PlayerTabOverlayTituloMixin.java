package dev.rptag.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.rptag.client.ClientTitulos;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * (5.78.9) Garante que o título ({@code [Classe] Nome [Pessoal]}) apareça na lista do TAB mesmo
 * quando outro mod (ex.: <b>Hidden Names</b>, cujo {@code PlayerTabOverlayMixin} faz
 * {@code @Overwrite} em {@code getNameForDisplay} e ignora {@code getTabListDisplayName()}) troca
 * o nome na mão.
 *
 * <p>{@code priority = 2000}: aplica DEPOIS do {@code @Overwrite} do Hidden Names (prioridade
 * padrão 1000). Como {@link ClientTitulos#aplicar} tem guarda anti-duplicação, no jogo sem Hidden
 * Names — onde o servidor já mandou o título pronto no {@code TabListNameFormat} — este mixin
 * devolve o próprio componente sem tocar em nada.
 */
@Mixin(value = PlayerTabOverlay.class, priority = 2000)
public abstract class PlayerTabOverlayTituloMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void rptag$aplicarTituloNoTab(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        try {
            if (info == null || info.getProfile() == null) {
                return;
            }
            UUID id = info.getProfile().getId();
            if (id == null || ClientTitulos.get(id) == null) {
                return;
            }
            Component atual = cir.getReturnValue();
            if (atual == null) {
                return;
            }
            Component novo = ClientTitulos.aplicar(id, atual);
            if (novo != atual) {
                cir.setReturnValue(novo);
            }
        } catch (Throwable ignorado) {
        }
    }
}
