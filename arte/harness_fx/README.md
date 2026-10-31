# Harness de FX (renderiza o FX REAL fora do jogo)

Serve para **ver** um aviso sem abrir o Minecraft. Ele compila o
`dev/rptag/client/RealAvisoFx.java` **de verdade** contra um
`AvisoFxUtil` falso cujo `Buf` rasteriza os mesmos triângulos num
framebuffer em memória. O resultado é o FX como o Java o desenha — não é
um redesenho em Python.

## Como rodar

```bash
# 1) stubs (sem GL, sem registry)
javac --release 21 -proc:none -nowarn -cp "$(cat /var/tmp/cp.txt)" \
      -d /tmp/fxout $(find arte/harness_fx/stub -name '*.java' ! -name Harness.java)

# 2) o FX real + o harness (o /tmp/fxout vem PRIMEIRO no -cp)
javac --release 21 -proc:none -nowarn \
      -cp "/tmp/fxout:build/classes:$(cat /var/tmp/cp.txt)" -d /tmp/fxout \
      src/main/java/dev/rptag/client/RealAvisoFx.java arte/harness_fx/stub/dev/rptag/client/Harness.java

# 3) frames (t em segundos) e PNG
java -cp "/tmp/fxout:build/classes:$(cat /var/tmp/cp.txt)" \
     dev.rptag.client.Harness 640 360 "AVISO" "A COROA CONVOCA TODOS" 1.8 3.2 5.0
python3 arte/harness_fx/render.py /tmp/fx/frame_3.2.bin
```

Para outro FX: troque o nome da classe no `Harness.java` (ele chama
`iniciar(...)`, mexe em `inicio` por reflexão e chama `render`).

## Limites

- O texto sai com uma fonte aproximada (DejaVu) — o layout é o real, os
  glifos não.
- O stub do `AvisoFxUtil` repete os primitivos do real (`tri/quad/rect/
  seg/circ/elipse/diamante/brilho/vinheta`). **Se mudar um primitivo no
  real, atualize o stub**, senão a prévia mente.
- `SoundEvents` é nulo (não toca som).
