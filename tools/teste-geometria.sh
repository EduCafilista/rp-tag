#!/bin/bash
# Testa a geometria do balao (BubbleGeometry) SEM precisar do Minecraft.
# Requer JDK 21 (javac). Rode na raiz do projeto:  bash tools/teste-geometria.sh
set -e
OUT=$(mktemp -d)
SRC=src/main/java/dev/rptag/client/BubbleGeometry.java
# os testes ficam no mesmo pacote (acessam metodos package-private)
mkdir -p "$OUT/src/dev/rptag/client"
cp tools/teste-geometria/*.java "$OUT/src/dev/rptag/client/"
if command -v javac >/dev/null 2>&1; then JAVAC="javac"; else JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"; fi
$JAVAC -d "$OUT/cls" "$SRC" "$OUT"/src/dev/rptag/client/*.java
echo "== triangulacao (areas) =="
java -cp "$OUT/cls" dev.rptag.client.AreaTest
echo "== build() em 6000 combinacoes aleatorias =="
java -cp "$OUT/cls" dev.rptag.client.BuildFuzz
