package dev.rptag.client;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.math.Axis;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * (5.48.0) <b>O GUARDA-CHUVA NA MÃO — de verdade, ancorado no OSSO da mão.</b>
 *
 * <h2>Por que isto existe</h2>
 * O dono, cinco versões seguidas: <i>"a umbrella continua bugada"</i>, <i>"ainda não está na
 * mão?"</i>, <i>"não fica na minha mão também a umbrella"</i>. A abordagem antiga era tentar
 * acertar a mão por <b>tentativa</b>: mexer no bloco {@code display} do modelo
 * ({@code thirdperson_righthand}) e torcer para a cadeia de transformações do Minecraft cair
 * onde a gente queria. Só que essa cadeia tem seis matrizes (corpo, pivô do braço,
 * {@code translateToHand}, o giro de 90/180 do item na mão...) e cada versão errava por um
 * pedaço diferente.
 *
 * <p>Agora o mod <b>desenha o guarda-chuva ele mesmo</b>, em {@code RenderPlayerEvent.Post}:
 * <ol>
 *   <li>{@code PlayerRenderer.getModel().translateToHand(braco, pose)} — esta é a <b>mesma
 *       função do jogo</b> que coloca qualquer item na mão do jogador. Zero chute: a origem do
 *       modelo cai exatamente onde o jogo põe a mão;</li>
 *   <li>a partir dali o guarda-chuva é levantado (rotação) e deslocado pelos ajustes abaixo;</li>
 *   <li>é desenhado com o contexto {@code FIXED}, que no modelo é <b>identidade</b> — o modelo
 *       cru tem ~0,9 bloco de altura e quem posiciona é esta classe.</li>
 * </ol>
 *
 * <p>No modelo, o {@code thirdperson_*} ficou com escala ZERO de propósito: senão o vanilla
 * desenharia um SEGUNDO guarda-chuva por cima deste.
 *
 * <h2>Os ajustes (e por que dá para mexer em jogo)</h2>
 * Como eu não vejo o seu jogo, cada número daqui é comandável no cliente:
 * <pre>
 *   /umbrella                 mostra os valores atuais
 *   /umbrella pos 0 -0.1 0    desloca (blocos)
 *   /umbrella rot 0 0 0       gira (graus)
 *   /umbrella escala 1.2      tamanho
 *   /umbrella virar           vira a cúpula (se ela aparecer pra baixo)
 *   /umbrella reset           volta ao padrão
 * </pre>
 * Tudo é salvo em {@code config/rptag-umbrella.txt} e volta na próxima sessão — então dá para
 * acertar no olho e continuar jogando, sem reinstalar nada.
 *
 * <p>Regra de ouro do mod: erro aqui não desenha o guarda-chuva, nunca derruba o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class UmbrellaNaMao {

    private UmbrellaNaMao() {
    }

    // ---------------------------------------------------------------- ajustes

    /** deslocamento em BLOCOS, no referencial da mão. */
    private static float px = 0.0F, py = -0.02F, pz = 0.0F;
    /** giro em GRAUS. */
    private static float gx = 0.0F, gy = 0.0F, gz = 0.0F;
    /** tamanho (1,0 = o modelo cru, ~0,9 bloco de altura). */
    private static float escala = 1.35F;
    /** cúpula pra baixo? aperta com /umbrella virar. */
    private static boolean virar = false;

    /** os ajustes são lidos UMA vez, no primeiro uso (não existe setup de cliente no mod). */
    private static boolean carregado;

    private static void garantirCarregado() {
        if (!carregado) {
            carregado = true;
            carregar();
        }
    }

    private static File arquivo() {
        try {
            return new File(Minecraft.getInstance().gameDirectory, "config/rptag-umbrella.txt");
        } catch (Throwable t) {
            return null;
        }
    }

    /** lê os ajustes salvos (uma linha: "px py pz gx gy gz escala virar"). */
    public static void carregar() {
        try {
            File f = arquivo();
            if (f == null || !f.isFile()) {
                return;
            }
            String[] p = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                    .trim().split("\\s+");
            if (p.length < 8) {
                return;
            }
            px = Float.parseFloat(p[0]);
            py = Float.parseFloat(p[1]);
            pz = Float.parseFloat(p[2]);
            gx = Float.parseFloat(p[3]);
            gy = Float.parseFloat(p[4]);
            gz = Float.parseFloat(p[5]);
            escala = Float.parseFloat(p[6]);
            virar = Boolean.parseBoolean(p[7]);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui ler os ajustes do guarda-chuva", t);
        }
    }

    private static void salvar() {
        try {
            File f = arquivo();
            if (f == null) {
                return;
            }
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            String linha = px + " " + py + " " + pz + " " + gx + " " + gy + " " + gz + " "
                    + escala + " " + virar;
            Files.write(f.toPath(), linha.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui salvar os ajustes do guarda-chuva", t);
        }
    }

    private static String resumo() {
        return String.format("pos [%.2f %.2f %.2f] rot [%.0f %.0f %.0f] escala %.2f virar %s",
                px, py, pz, gx, gy, gz, escala, virar ? "SIM" : "nao");
    }

    // ---------------------------------------------------------------- desenho

    @SubscribeEvent
    public static void aoDesenharJogador(RenderPlayerEvent.Post event) {
        try {
            garantirCarregado();
            Player p = event.getEntity();
            if (p == null || p.isSpectator()) {
                return;
            }
            ItemStack naMao = UmbrellaHud.naMao(p);
            if (naMao == null || naMao.isEmpty()) {
                return;
            }
            // qual é o braço que está segurando
            HumanoidArm braco = p.getMainHandItem() == naMao
                    ? p.getMainArm() : p.getMainArm().getOpposite();
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            // 1) A MÃO. Esta é a mesma conta do jogo (ItemInHandLayer usa exatamente isto).
            event.getRenderer().getModel().translateToHand(braco, pose);
            // 2) o modelo do guarda-chuva sobe a partir do cabo: vira ele pra cima (o eixo +Y
            //    do modelo aponta pra BAIXO no espaço da mão, daí os 180 na base).
            if (!virar) {
                pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            }
            pose.mulPose(Axis.YP.rotationDegrees(gy));
            pose.mulPose(Axis.ZP.rotationDegrees(gz));
            pose.mulPose(Axis.XP.rotationDegrees(gx));
            // 3) deslocamento fino (blocos) e tamanho
            pose.translate(px, py, pz);
            pose.scale(escala, escala, escala);
            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderStatic(naMao, ItemDisplayContext.FIXED,
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY, pose,
                    event.getMultiBufferSource(), p.level(), p.getId());
            pose.popPose();
        } catch (Throwable t) {
            // o guarda-chuva é enfeite: se falhar, o jogador continua inteiro
        }
    }

    // ---------------------------------------------------------------- comandos

    /** o comando de ajuste ao vivo — sem ele, cada tentativa seria um jar novo. */
    @SubscribeEvent
    public static void aoRegistrarComandos(RegisterClientCommandsEvent event) {
        try {
            garantirCarregado();
            event.getDispatcher().register(Commands.literal("umbrella")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "\u00a7dguarda-chuva \u00a77" + resumo()), false);
                        return 1;
                    })
                    .then(Commands.literal("pos")
                            .then(Commands.argument("x", FloatArgumentType.floatArg(-2.0F, 2.0F))
                                    .then(Commands.argument("y", FloatArgumentType.floatArg(-2.0F, 2.0F))
                                            .then(Commands.argument("z", FloatArgumentType.floatArg(-2.0F, 2.0F))
                                                    .executes(ctx -> {
                                                        px = FloatArgumentType.getFloat(ctx, "x");
                                                        py = FloatArgumentType.getFloat(ctx, "y");
                                                        pz = FloatArgumentType.getFloat(ctx, "z");
                                                        salvar();
                                                        return avisar(ctx.getSource());
                                                    })))))
                    .then(Commands.literal("rot")
                            .then(Commands.argument("x", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                    .then(Commands.argument("y", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                            .then(Commands.argument("z", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                                    .executes(ctx -> {
                                                        gx = FloatArgumentType.getFloat(ctx, "x");
                                                        gy = FloatArgumentType.getFloat(ctx, "y");
                                                        gz = FloatArgumentType.getFloat(ctx, "z");
                                                        salvar();
                                                        return avisar(ctx.getSource());
                                                    })))))
                    .then(Commands.literal("escala")
                            .then(Commands.argument("v", FloatArgumentType.floatArg(0.2F, 3.0F))
                                    .executes(ctx -> {
                                        escala = FloatArgumentType.getFloat(ctx, "v");
                                        salvar();
                                        return avisar(ctx.getSource());
                                    })))
                    .then(Commands.literal("virar").executes(ctx -> {
                        virar = !virar;
                        salvar();
                        return avisar(ctx.getSource());
                    }))
                    .then(Commands.literal("reset").executes(ctx -> {
                        px = 0.0F;
                        py = -0.02F;
                        pz = 0.0F;
                        gx = 0.0F;
                        gy = 0.0F;
                        gz = 0.0F;
                        escala = 1.35F;
                        virar = false;
                        salvar();
                        return avisar(ctx.getSource());
                    })));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: comando /umbrella nao registrou", t);
        }
    }

    private static int avisar(net.minecraft.commands.CommandSourceStack fonte) {
        fonte.sendSuccess(() -> Component.literal("\u00a7dguarda-chuva \u00a77" + resumo()), true);
        return 1;
    }
}
