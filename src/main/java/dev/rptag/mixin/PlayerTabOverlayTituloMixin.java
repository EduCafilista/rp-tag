package dev.rptag.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.rptag.HiddenNamesCompat;
import dev.rptag.RPTags;
import dev.rptag.client.ClientPersonaCache;
import dev.rptag.client.ClientRPStates;
import dev.rptag.client.ClientTitulos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * (5.78.9 / 5.78.10) Garante que o <b>título</b> ({@code [Classe] Nome [Pessoal]}) <b>E a tag
 * {@code ● ʀᴘ} / {@code ○ ᴏꜰꜰ ʀᴘ}</b> apareçam SEMPRE na lista do TAB, mesmo quando o mod
 * <b>Hidden Names</b> está instalado.
 *
 * <h2>O que acontecia na 5.78.9</h2>
 * O {@code PlayerTabOverlayMixin} do Hidden Names faz {@code @Overwrite} em
 * {@code getNameForDisplay(PlayerInfo)} e ignora {@code getTabListDisplayName()} para <b>todos</b>
 * os jogadores (porque todo jogador entra em {@code NameData.DATA} no login). Com isso:
 * <ul>
 *   <li>quem <b>não tinha título</b> saía no {@code return} logo no começo do mixin e perdia a tag
 *       {@code ○ ᴏꜰꜰ ʀᴘ} / {@code ● ʀᴘ};</li>
 *   <li>quem tinha título ou {@code /hiddennames setName} ganhava o título, mas o mixin não
 *       anexava {@code RPTags.tag(inRp)} no final — e quem entrou sem {@code setName} ficava com a
 *       tag congelada do primeiro login mesmo trocando {@code /rp on}/{@code /rp off}!</li>
 * </ul>
 *
 * <p>{@code priority = 2000}: roda depois do {@code @Overwrite} do Hidden Names (prioridade 1000).
 * Sem o Hidden Names instalado, o servidor já manda título + tag prontos via
 * {@code TabListNameFormat}, então as guardas anti-duplicação deixam o componente intacto.
 */
@Mixin(value = PlayerTabOverlay.class, priority = 2000)
public abstract class PlayerTabOverlayTituloMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void rptag$aplicarTituloNoTab(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        try {
            if (info == null || info.getProfile() == null) {
                return;
            }
            // em servidor vanilla (sem o RP Tag no servidor) não inventa tag nem título
            if (!ClientRPStates.hasData() && ClientTitulos.get(info.getProfile().getId()) == null) {
                return;
            }
            UUID id = info.getProfile().getId();
            if (id == null) {
                return;
            }
            Component atual = cir.getReturnValue();
            if (atual == null) {
                return;
            }

            boolean hnAtivo = HiddenNamesCompat.ativo();
            Component base = atual;

            // 1) Se o Hidden Names está ativo, dois cuidados:
            //    a) se o jogador tem /persona ativa, ela prevalece como nome do personagem;
            //    b) se não teve /hiddennames setName, o NameData guardou a tag congelada do
            //       primeiro login — removemos a tag velha para colocar o estado ATUAL (RP / OFF RP).
            if (hnAtivo) {
                String persona = ClientPersonaCache.getName(id);
                if (persona != null && !persona.isEmpty()) {
                    base = Component.literal(persona);
                } else {
                    base = HiddenNamesCompat.removerTagAntiga(base);
                }
            }

            // 2) Envolve com o título [Classe] Nome [Pessoal] (anti-duplicação interna)
            Component comTitulo = ClientTitulos.aplicar(id, base);

            // 3) Anexa a tag viva ● ʀᴘ / ○ ᴏꜰꜰ ʀᴘ se ainda não estiver lá
            Component resultado = comTitulo;
            if (ClientRPStates.hasData() && !RPTags.hasTag(comTitulo)) {
                MutableComponent mc = comTitulo.copy()
                        .append(RPTags.tag(ClientRPStates.isInRp(id)));
                if (info.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR) {
                    mc = mc.withStyle(ChatFormatting.ITALIC);
                }
                resultado = mc;
            }

            if (resultado != atual) {
                cir.setReturnValue(resultado);
            }
        } catch (Throwable ignorado) {
        }
    }
}
