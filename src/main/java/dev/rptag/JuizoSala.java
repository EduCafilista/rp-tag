package dev.rptag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * (5.29.0) <b>A SALA DO JULGAMENTO — a arena que o dono mandou.</b>
 *
 * <p>O dono: <i>"essa deve ser a sala para matar o boss do juizo, o juizo sala"</i>, mandando um
 * arquivo {@code .schem}. A sala é uma arena circular aberta de <b>98 x 71 x 93</b> blocos
 * (piso de bedrock/pedra, anel de pilares, tochas de alma e correntes no teto).
 *
 * <p>Os blocos estão aqui <b>comprimidos</b>: a estrutura inteira vira 19576 caracteres em
 * Base64 (gzip + RLE), decodificados na hora de construir. Isso evita 647 mil linhas de código
 * e mantém o jar pequeno. Cada bloco é pedido <b>pelo nome</b> ao registro do próprio jogo; o
 * que não existir (blocos de outro mod, como o {@code sololeveling}) simplesmente não é posto.
 *
 * <p>Ninguém cava aqui: o {@link RegrasDoJuizo} cancela a quebra de bloco nestas dimensões.
 */
public final class JuizoSala {

    private JuizoSala() {
    }

    /** largura (eixo X), altura (Y) e comprimento (Z) da estrutura original. */
    public static final int LARGURA = 98, ALTURA = 71, COMPRIMENTO = 93;

    /** canto da sala relativo ao centro dela: a arena fica centrada em (0, ?, 0). */
    public static final int ORIGEM_X = -(LARGURA / 2), ORIGEM_Z = -(COMPRIMENTO / 2);

    /** a paleta: 112 estados de bloco, um por linha (índice = posição na lista). */
    private static final String PALETA = String.join("\n",
            "minecraft:air",
            "minecraft:bedrock",
            "minecraft:light_gray_concrete_powder",
            "minecraft:smooth_stone",
            "minecraft:stone_bricks",
            "minecraft:stone",
            "minecraft:chiseled_stone_bricks",
            "minecraft:lapis_ore",
            "minecraft:sea_lantern",
            "minecraft:light[level=15,waterlogged=false]",
            "minecraft:stone_stairs[facing=west,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:stone_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
            "minecraft:stone_stairs[facing=east,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:stone_stairs[facing=west,half=top,shape=straight,waterlogged=false]",
            "minecraft:stone_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
            "minecraft:light[level=10,waterlogged=false]",
            "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]",
            "minecraft:stone_stairs[facing=north,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]",
            "minecraft:light[level=11,waterlogged=false]",
            "sololeveling:evaluator_test[facing=east]",
            "minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]",
            "minecraft:stone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:spruce_wall_sign[facing=east,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=south,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=south,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
            "minecraft:blue_stained_glass",
            "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]",
            "minecraft:polished_deepslate",
            "minecraft:stone_brick_stairs[facing=north,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=west,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]",
            "minecraft:stone_stairs[facing=south,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:stone_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
            "minecraft:stone_stairs[facing=south,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:stone_stairs[facing=west,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:smooth_basalt",
            "minecraft:red_carpet",
            "minecraft:stone_brick_stairs[facing=north,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=east,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]",
            "minecraft:soul_campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]",
            "minecraft:soul_campfire[facing=east,lit=true,signal_fire=false,waterlogged=false]",
            "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=west,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=east,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=south,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:diorite_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]",
            "minecraft:chain[axis=y,waterlogged=false]",
            "minecraft:diorite_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=tall]",
            "minecraft:diorite_wall[east=tall,north=tall,south=none,up=true,waterlogged=false,west=none]",
            "minecraft:diorite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=tall]",
            "minecraft:diorite_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=tall]",
            "minecraft:diorite_wall[east=tall,north=none,south=tall,up=true,waterlogged=false,west=none]",
            "minecraft:diorite_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]",
            "minecraft:cobblestone",
            "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:cobblestone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:cobblestone_slab[type=top,waterlogged=false]",
            "minecraft:cobblestone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:cobblestone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:stone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:stone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:light[level=8,waterlogged=false]",
            "minecraft:stone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:stone_stairs[facing=north,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:stone_slab[type=bottom,waterlogged=false]",
            "minecraft:stone_stairs[facing=east,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_stairs[facing=north,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:stone_stairs[facing=north,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_stairs[facing=west,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:stone_stairs[facing=west,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_stairs[facing=east,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:stone_stairs[facing=south,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_stairs[facing=south,half=top,shape=outer_right,waterlogged=false]",
            "minecraft:polished_deepslate_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
            "minecraft:polished_deepslate_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
            "minecraft:polished_deepslate_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
            "minecraft:stone_slab[type=top,waterlogged=false]",
            "minecraft:polished_deepslate_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:polished_deepslate_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
            "minecraft:stone_brick_slab[type=top,waterlogged=false]",
            "minecraft:stone_brick_wall[east=tall,north=none,south=tall,up=true,waterlogged=false,west=none]",
            "minecraft:stone_brick_wall[east=tall,north=tall,south=none,up=true,waterlogged=false,west=none]",
            "minecraft:stone_brick_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=tall]",
            "minecraft:stone_brick_slab[type=bottom,waterlogged=false]",
            "minecraft:stone_brick_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]",
            "minecraft:stone_brick_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=low]",
            "minecraft:stone_brick_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=none]",
            "minecraft:stone_brick_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=none]",
            "minecraft:stone_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]",
            "minecraft:stone_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]",
            "minecraft:soul_sand",
            "minecraft:stone_brick_stairs[facing=west,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=north,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=east,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=north,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:bone_block[axis=y]",
            "minecraft:stone_brick_stairs[facing=south,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=west,half=top,shape=outer_left,waterlogged=false]",
            "minecraft:stone_brick_stairs[facing=east,half=top,shape=inner_right,waterlogged=false]",
            "minecraft:soul_fire",
            "minecraft:acacia_button[face=wall,facing=west,powered=false]",
            "minecraft:black_stained_glass",
            "minecraft:deepslate_tiles",
            "minecraft:soul_torch",
            "minecraft:smooth_stone_slab[type=top,waterlogged=false]",
            "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");

    /** o desenho, em pedaços (cada constante de classe tem limite de 64 KB). */
    private static final String[] DADOS = {
            "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"
    };

    private static BlockState[] estados;
    private static boolean construida = false;

    /**
     * Constrói a arena com o canto em (ox, oy, oz).
     *
     * @return quantos blocos foram colocados de verdade
     */
    public static int construir(ServerLevel lv, int ox, int oy, int oz) {
        if (construida) {
            return 0;
        }
        construida = true;
        int colocados = 0;
        try {
            BlockState[] est = estados();
            int[] dados = ler();
            int total = LARGURA * ALTURA * COMPRIMENTO;
            int pos = 0;
            for (int i = 0; i + 1 < dados.length && pos < total; i += 2) {
                int valor = dados[i];
                int run = dados[i + 1];
                BlockState st = valor < est.length ? est[valor] : null;
                if (st != null && !st.isAir()) {
                    for (int n = 0; n < run && pos + n < total; n++) {
                        int p = pos + n;
                        int x = p % LARGURA;
                        int z = (p / LARGURA) % COMPRIMENTO;
                        int y = p / (LARGURA * COMPRIMENTO);
                        lv.setBlock(new BlockPos(ox + x, oy + y, oz + z), st, 2);
                        colocados++;
                    }
                }
                pos += run;
            }
            RPTagMod.LOGGER.info("RP Tag: arena do Julgamento construida ({} blocos)", colocados);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: construcao da arena do Julgamento falhou", t);
        }
        return colocados;
    }

    /** true quando a arena já está de pé nesta sessão. */
    public static boolean pronta() {
        return construida;
    }

    /** onde o jogador desperta ao entrar na sala (relativo ao centro). */
    public static BlockPos entrada() {
        return new BlockPos(0, 3, COMPRIMENTO / 2 - 12);
    }

    /** onde o ADM deve ficar como boss (relativo ao centro). */
    public static BlockPos trono() {
        return new BlockPos(0, 3, ORIGEM_Z + 12);
    }

    // ------------------------------------------------------------------ dados
    private static BlockState[] estados() {
        if (estados != null) {
            return estados;
        }
        String[] nomes = PALETA.split("\n");
        estados = new BlockState[nomes.length];
        for (int i = 0; i < nomes.length; i++) {
            estados[i] = interpretar(nomes[i]);
        }
        return estados;
    }

    /**
     * "minecraft:stone_stairs[facing=west,half=top]" → o estado de verdade.
     *
     * <p>Tudo por nome: o arquivo veio de outra versão/outro modpack, então o registro do jogo é
     * a única fonte confiável. Bloco desconhecido vira {@code null} (não é colocado).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState interpretar(String texto) {
        try {
            String nome = texto;
            String props = "";
            int abre = texto.indexOf('[');
            if (abre >= 0) {
                nome = texto.substring(0, abre);
                int fecha = texto.indexOf(']', abre);
                props = fecha > abre ? texto.substring(abre + 1, fecha) : "";
            }
            ResourceLocation id = ResourceLocation.tryParse(nome);
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                return null;
            }
            Block bloco = BuiltInRegistries.BLOCK.get(id);
            BlockState st = bloco.defaultBlockState();
            if (props.isEmpty()) {
                return st;
            }
            for (String par : props.split(",")) {
                int igual = par.indexOf('=');
                if (igual <= 0) {
                    continue;
                }
                String chave = par.substring(0, igual).trim();
                String valor = par.substring(igual + 1).trim();
                Property prop = null;
                for (Property p : st.getProperties()) {
                    if (p.getName().equals(chave)) {
                        prop = p;
                        break;
                    }
                }
                if (prop == null) {
                    continue;
                }
                java.util.Optional<?> v = prop.getValue(valor);
                if (v.isPresent()) {
                    st = st.setValue(prop, (Comparable) v.get());
                }
            }
            return st;
        } catch (Throwable t) {
            return null;   // bloco de outro mod: fica de fora, a sala segue
        }
    }

    /** decodifica o desenho (Base64 → gzip → RLE) em pares (valor, quantidade). */
    private static int[] ler() {
        try {
            byte[] cru = Base64.getDecoder().decode(String.join("", DADOS));
            ByteArrayOutputStream saida = new ByteArrayOutputStream(cru.length * 8);
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(cru))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = gz.read(buf)) > 0) {
                    saida.write(buf, 0, n);
                }
            }
            byte[] d = saida.toByteArray();
            int[] out = new int[d.length];
            int o = 0, i = 0;
            while (i < d.length && o + 1 < out.length) {
                for (int k = 0; k < 2 && i < d.length; k++) {
                    int valor = 0, sh = 0;
                    while (i < d.length) {
                        int b = d[i++] & 0xFF;
                        valor |= (b & 0x7F) << sh;
                        if ((b & 0x80) == 0) {
                            break;
                        }
                        sh += 7;
                    }
                    if (k == 0) {
                        out[o++] = valor;
                    } else {
                        out[o++] = valor;
                    }
                }
            }
            return java.util.Arrays.copyOf(out, o);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: o desenho da arena nao pode ser lido", t);
            return new int[0];
        }
    }
}
