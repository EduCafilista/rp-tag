# Produção de animações

Este diretório contém as cenas Blender, os scripts de exportação e as ferramentas
de prévia dos avisos de tela do RP Tag. O texto das mensagens é desenhado pelo
cliente Minecraft e não deve ser incorporado aos quadros renderizados.

## Organização

| Caminho | Responsabilidade |
| --- | --- |
| `cenas/morte.py` | Cena do aviso Morte |
| `cenas/inferno.py` | Cena do aviso Inferno |
| `cenas/gelo.py` | Cena do aviso Gelo |
| `cenas/void.py` | Cena do aviso Void (câmera em perspectiva) |
| `cenas/divino.py` | Cena do aviso Divino |
| `cenas/egipcio.py` | Cena do aviso Egípcio (câmera em perspectiva) |
| `cenas/comum.py` | Primitivas compartilhadas pelas cenas que a utilizam |
| `cenas/perspectiva.py` | Parâmetros e projeção da câmera em perspectiva, sem dependência do Blender |
| `cenas/metas.py` | Marcadores, fade, prévia e qualidade JPEG de cada aviso, sem dependência do Blender |
| `cenas/suave.py` | Pesos de atenuação sem degrau (zona do texto, período de leitura) |
| `exportar.py` | Conversão de PNG RGBA em pares JPEG de cor e alfa |
| `validar.py` | Validação dos assets destinados ao JAR |
| `inspecionar.py` | Inspeção dos quadros e metadados de renderização |
| `previa.py` | Composição das prévias sobre fundos de referência |
| `referencias/` | Referências de gameplay e propostas anteriores |
| `tests/` | Testes das ferramentas Python |
| `render/` | Quadros e metadados gerados; fora do Git |
| `previa/` | Imagens e animações de avaliação; fora do Git |

A presença de um script de conceito não significa aprovação visual ou integração
no mod. O estado do código deve ser verificado nas classes Java e nos assets
em `src/main/resources/assets/rptag/fx/`.

## Ambiente

O ambiente utilizado pelo projeto inclui Blender 5.2, Python 3 e Pillow.
Os comandos a seguir usam o caminho de instalação do Blender no Windows;
adapte-o quando necessário.

## Renderização e avaliação

```powershell
# Renderizar uma cena completa
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b --factory-startup -P arte/cenas/inferno.py

# Renderizar apenas os instantes de avaliação
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' -b --factory-startup -P arte/cenas/gelo.py -- --quadros 12,36,72,127

# Inspecionar e compor os quadros sobre gameplay
python arte/inspecionar.py gelo --quadros 12,36,72,127
python arte/previa.py gelo --quadros 12,36,72,127 --fundo arte/referencias/gameplay_claro.png

# Gerar uma prévia animada após renderizar a sequência completa
python arte/previa.py gelo --gif --fundo arte/referencias/gameplay_escuro.png
```

O formato de saída utilizado é 1280 × 720, 24 quadros por segundo, duração de
seis segundos e fundo transparente. As cenas Inferno, Gelo e Void utilizam título em
`cy = 0.45 h`; Divino utiliza `cy = 0.42 h`. A posição do texto deve ser conferida com o efeito Java
correspondente. O enquadramento de uma cena não deve ser copiado de outro aviso
sem verificar essas diferenças.

## Exportação

Após a aprovação visual, execute:

```powershell
python arte/exportar.py inferno
python arte/validar.py
python arte/previa.py inferno --quadros 12,72 --exportado --fundo arte/referencias/gameplay_claro.png
```

Cada quadro produz um arquivo de cor RGB e um arquivo de alfa em escala de
cinza. O arquivo `fx.json` registra frequência, dimensões, quantidade de quadros
e marcadores. O fade é aplicado na exportação e não deve ser aplicado novamente
ao compor os JPEGs exportados.

O tamanho dos assets depende da textura e da compressão. Registre o tamanho real
da exportação, em vez de utilizar estimativas de versões anteriores.

A qualidade padrão é 85 para a cor e 90 para o alfa. Uma cena pode reduzir a qualidade
da cor declarando `"jpeg": {"cor": N}` em `metas.py`; o alfa permanece em 90. O Divino usa
cor 75 porque o efeito cobre quase toda a tela (aprovado pelo responsável).

## Câmera em perspectiva

`comum.camera_perspectiva(distancia, centro_y)` substitui a câmera ortográfica por uma
em perspectiva cujo plano z = 0 cobre o mesmo quadro de 16 × 9 unidades, com o ponto de
fuga em `(0, centro_y)`. Objetos em z negativo ficam atrás desse plano e convergem para
o título. Use `perspectiva.projetar` para converter posições do mundo em frações da tela.

Atenuações por região ou por tempo devem usar as funções de `suave.py`. Condições
secas (`if` dentro/fora) fazem objetos sumirem ou reaparecerem por degrau.

## Critérios de avaliação

- Conferir o efeito sobre fundos claros e escuros de gameplay.
- Manter a mensagem legível e a área central compatível com o layout do aviso.
- Avaliar pixels transparentes e transições de alfa também após a compressão.
- Verificar continuidade de movimento, saída das partículas e encerramento.
- Conferir os enquadramentos 16:9, 16:10 e 21:9.
- Comparar o resultado no cliente com a prévia aprovada antes da entrega.

As referências de gameplay deste diretório foram capturadas no cliente local.
A tipografia usada pelo compositor é uma aproximação para avaliação, não uma
reprodução integral da renderização de texto do Minecraft.

## Testes

```powershell
python -m unittest discover -s arte/tests -v
python arte/validar.py
```

Não substitua um efeito existente por uma proposta de arte sem aprovação do
responsável. Registre propostas canceladas como histórico, sem apresentá-las
como próximas etapas obrigatórias.
