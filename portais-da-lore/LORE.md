# Portais da Lore (loreportals) v1.0.3

Mod SEPARADO (não é o rp-tag!) de **portais redondos** pra contar lore no
servidor. NeoForge **1.21.1**, modid `loreportals`.

## Os DOIS modos de portal

### 1. PAR — "coloquei dois, conectei os dois"
1. Crafte 2× **Núcleo do Portal** e o **Cristal de Vínculo**.
2. Coloque os 2 núcleos (qualquer distância, até entre dimensões).
3. Clique o cristal no 1º núcleo → ele marca (vale clicar na moldura
   do anel também — acha o núcleo sozinho). Clique no 2º →
   **PORTAIS CONECTADOS** — cada núcleo ganha um PORTAL OVAL azul
   girando em cima (vertical, ~2 blocos de altura).
4. Encoste no núcleo (dentro do oval) por ~0,5 s → teleporte pro outro
   lado. Sem mensagens de chat — o feedback é o efeito.
5. Shift+cristal num núcleo = quebra o vínculo.

### 2. MOLDE — "formato específico + núcleo obrigatório"
O formato é um **anel redondo 5×5** (11 **Molduras Rúnicas**) com o
**Núcleo no vão do meio de baixo**:

```
  .  F  F  F  .
  F  .  .  .  F
  F  .  N  .  F      F = Moldura Rúnica
  F  .  .  .  F      N = Núcleo do Portal (no chão)
  .  F  F  F  .
```

Quando o anel fecha, o portal **acorda sozinho** — um grande disco de
galáxia roxa vertical (raio ~2 blocos) no vão do anel. Entre nele e
viaje pro portal parceiro. Quebrou uma moldura? O portal dorme (mas
guarda o vínculo). Recolocou? Ele acorda de novo. O núcleo guarda a
orientação, então dá pra ativar até 4 anéis ao redor (N/S/L/O).

## Extras
- **/portal ajuda, info, listar, ir <n>, desvincular, apagar <n>, contar**
- Contador de viagens (por portal e por jogador) salvo no mundo.
- Partículas ambiente + som de portal; efeito sonoro na travessia.
- Cristal mostra tooltip com as instruções.
- Aba criativa "Portais da Lore" (§5 roxo).

## Receitas
| Item | Receita |
|---|---|
| Moldura Rúnica ×8 | tijolos de pedra + lasca de ametista (borda/ametista) |
| Núcleo do Portal | 4 molduras + ametistas + **olho de ender** no meio |
| Cristal de Vínculo | ametista + pérola de ender + diamante (diagonal) |

## Arquitetura (código)
- `LorePortalsMod` — registros (blocos/itens/BE/aba).
- `PortalCoreBlock` — BE do núcleo; clique = valida anel e ativa.
- `PortalFrameBlock` — moldura; coloca/quebra → acorda núcleos próximos.
- `PortalBlockEntity` — valida o anel (4 orientações), ativa/desativa,
  sincroniza pro cliente; anel = 11 offsets em `ANEL`.
- `PortalData` — SavedData `loreportals_data` no overworld: portais,
  vínculos simétricos, contagem de viagens.
- `PortalWorldTick` — tick do servidor: valida anéis (20 ticks),
  detecta jogador dentro do disco e teleporta (10 ticks + cooldown 3 s).
- `CristalVinculoItem` — marca/conecta/desvincula.
- `PortalCommands` — `/portal ...`.
- `client/PortalRenderer` — o disco procedural (galáxia roxa vertical
  pro MOLDE; círculo azul horizontal pro PAR), com espirais, aro e
  estrelas piscando. Sem texturas (POSITION_COLOR aditivo).
- `client/ClientInit` — registra o renderer.

## Compilar (igual rp-tag — Gradle NÃO roda no sandbox)
```
bash toolchain.sh                                  # 1x por sessão (ou se /var/tmp zerou)
find src/main/java -name "*.java" > /var/tmp/sources.txt
javac --release 21 -encoding UTF-8 -proc:none -nowarn \
  -cp "$(cat /var/tmp/cp.txt)" -d build/classes @/var/tmp/sources.txt
cp -r src/main/resources/* build/classes/
(cd build/classes && jar --create --file ../libs/portais-da-lore-1.0.0.jar .)
```

## Instalar
Jogue `portais-da-lore-1.0.0.jar` na pasta `mods/` do servidor **e** do
cliente (NeoForge 1.21.1). Sem dependências além do NeoForge.
