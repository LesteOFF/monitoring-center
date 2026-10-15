# CHANGELOG — Central de Monitoramento

## 2.2.0
- Bloco novo **Atuador** (`actuator`): emite redstone a partir de uma regra de alarme do canal ou de um Sensor com limite, leitura, período e espera próprios; saída normal ou invertida; acende quando ligado. Fica desligado sem energia no canal e não é afetado por silenciar o alarme.
- GUI do Atuador no mesmo estilo das outras; partes do editor de regras (nome do Sensor, rótulo do limite, lista de Sensores) passaram a ser compartilhadas com a Central.
- Protocolo de rede na versão 3: cliente e servidor precisam do mesmo jar.
- Gametests novos: regra (inclusive após silenciar), saída invertida e Sensor com limite próprio sem regras no canal.

## 2.1.0
- API aberta para o addon Auditor de Fábrica: `WorkshopScreen` virou `public abstract` (construtor e cores `INK`/`MUTED`/`ACCENT` protegidos) e `Scenario` ficou público. O Auditor exige `[2.1.0,3)`, então uma Central antiga é recusada na carga com mensagem clara.

## 2.0.0 e revisões de 13/10/2026
Diário das revisões que levaram à 2.0.0, na ordem em que foram feitas.

### Revisão de 13/10/2026 (tarde): correções do teste no jogo e melhorias vindas do CC:Tweaked
- **Assets que não apareciam (Acoplador sem textura)**: o mod Lightspeed guarda a lista de arquivos do jar numa chave id + versão + nome do jar. A versão no `neoforge.mods.toml` agora leva um carimbo de build (`1.0.0+data-hora`) para a chave mudar a cada jar. Se um asset novo não aparecer, apagar `lightspeed-cache/1.21.1/*/monitoringcenter*.lsc`.
- **Sensor**: lê a capacidade a cada leitura, primeiro sem lado (interior do bloco) e depois pela face encostada; sem `BlockCapabilityCache`. Métrica nova `item_flow` (entrada de itens por segundo, conferida todo tick, só aumentos). Forma do bloco igual ao modelo (`AttachedShapes`), também no Acoplador.
- **Jade**: saiu o `addConfig` duplicado que derrubava o plugin.
- **Tela**: escala de texto 1x a 4x por Tela (limitada pelo tamanho); rodapé `< 2/5 >` com páginas quando o conteúdo não cabe; moldura conectada por `multipart` (`left/right/up/down`, modelos gerados por `tools/GenerateScreenModels.java`); contorno único do painel; malha em cache no `PanelRenderer`; 2 cartões por bloco.
- **GUIs**: moldura, área, campos e botões são sprites nine-slice em `textures/gui/sprites/workshop/` (arte inicial de `tools/GenerateGuiSprites.java`, editável à mão).
- Hipótese ainda não conferida no jogo: o valor errado no Sophisticated Storage vinha do cache de capacidade.

### Revisão de 13/10/2026 (terceira rodada)
- **Filtro do Sensor**: clicar no Sensor com item na mão não define mais filtro (era fácil pôr um filtro sem querer e a contagem virar 0). O filtro aparece na GUI, com "Usar item da mão" e "Limpar filtro" (`Payloads.SetSensorFilter`).
- **Entrada e saída de itens**: `ItemFlow` conta por tipo de item, aumentos e reduções separados. Métricas `item_flow` (Entrada) e `item_out` (Saída).
- **Automático**: inventário + energia = máquina → Saída de itens; só inventário → Itens; depois a ordem antiga.
- **Gráfico da Tela**: eixo com máximo/meio/mínimo da primeira série, linha do tempo, linha de 2 px com área preenchida, grade vertical, legenda com cor e valor atual.
- **GUIs escuras**: sprites regenerados com a paleta do CC:Tweaked (moldura `C5C5C5`, interior `232323`), destaque âmbar; texto claro em `WorkshopScreen`.
- Ainda por confirmar no jogo: se o 0 no barril e no Creative Bin era mesmo filtro indevido.

### Revisão de 13/10/2026 (quarta rodada)
- **Ocupação (%)**: a capacidade agora respeita o tamanho real da pilha (o baú informa 99 por slot) e existe também com filtro (slots vazios + slots do item).
- **Entrada/Saída**: média das últimas 5 leituras, para o número não oscilar.
- **Gráfico**: uma escala por unidade partindo de 0, sem área preenchida nem grade vertical, legenda em colunas com todas as séries.
- **GUI da Tela**: pré-visualização ao vivo (`PanelPreview`, mesmo pintor do mundo), opções em botões segmentados, ícone por linha (automático, item da mão ou símbolo).
- **Texturas dos blocos**: Central, Sensor e Tela regeneradas por `tools/GenerateBlockTextures.java` na paleta das GUIs; as anteriores estão em `output/texture-backup/`.
- Energia acima de 2,1 G FE: com o Mekanism presente, a métrica `energy` lê primeiro o `IStrictEnergyHandler` (long, convertido para FE) e só depois a capacidade do NeoForge. Blocos de outros mods continuam limitados a `int`. Falta conferir no jogo.

### Alarmes (lote 4 do plano de melhorias)
- **Sem leitura**: comparação nova `MISSING`, dispara quando o Sensor fica sem valor (chunk descarregado, bloco removido, integração caída). Tem carência fixa de 10 s para não disparar na carga do mundo; o limite é ignorado.
- **Espera**: campo "Espera (s)" na regra (0 a 3600). O alarme só dispara se a condição durar esse tempo; a contagem zera quando o valor volta.
- **Período da regra**: regras sobre menor valor, maior valor e média escolhem 5 min, 6 h ou 7 d.
- **Silenciar**: agachar e clicar na Central silencia os alarmes ativos. O alarme continua na lista, a linha fica âmbar em vez de piscar em vermelho e a redstone para. Volta ao normal quando o alarme resolve e dispara de novo.
- **Comparador**: a Central dá sinal 5, 10 ou 15 conforme a severidade mais alta ativa e não silenciada, independente do nível escolhido para a saída direta.
- **Histórico**: as 6 últimas linhas resolvidas e o estado de silenciado agora são salvos.
- Regras antigas carregam com espera 0 e período de 5 min.
- Webhook do Discord: fora, por decisão.

### Tela e GUIs (lote 5 do plano de melhorias)
- **Modo Medidores** (`GAUGE`): mesma grade dos Cartões; cada célula mostra nome, porcentagem (ou valor, sem capacidade) e uma barra. Numa soma, a barra é empilhada com a parte de cada Sensor da mesma métrica.
- **Relógio**: a métrica `time` aparece como `HH:MM` em todos os modos; no Destaque vira um relógio grande.
- **Cores da Tela**: 9 em vez de 3 (verde, lima, amarelo, rosa, roxo e azul novos). Vermelho e laranja ficaram de fora por serem as cores de alarme.
- **Título**: campo opcional na aba Aparência; substitui o nome do canal no cabeçalho da Lista e dos Alarmes.
- **Somas**: cada uma escolhe Soma, Média, Menor ou Maior. O ícone muda junto (sigma, traço, seta para baixo, seta para cima).
- **Jade**: Central e Tela mostram o canal e a quantidade de alarmes ativos.
- Na GUI da Tela, "Como mostrar" e "Cor da Tela" viraram botões de escolha (não cabiam mais em segmentos).
- O estado enviado ao cliente passou a levar a métrica do Sensor; a versão do protocolo de rede subiu para 2, então cliente e servidor precisam do mesmo jar.
- Não feito: tela de configuração própria (o pack já tem o Configured, que a oferece) e rolagem no editor de linhas (já existia paginação).

### Sensores e integrações (lote 6 do plano de melhorias)
- **Filtro por tag**: botão "Por tag" na GUI do Sensor lista as tags do item do filtro. Vale para Itens, Entrada, Saída e Item na rede ME. Itens no chão continua só por item.
- **Fluido (todos os tanques)**: métrica `fluid_total`. Com um balde (ou outro recipiente) no filtro, soma só aquele fluido.
- **Área**: `animals` (filtra por ovo de spawn), `players_near` e `xp`.
- **AE2**: `ae2_fluid` (recipiente do fluido no filtro), `ae2_crafting` (quantidade pedida do item do filtro) e `ae2_types` (tipos distintos guardados).
- **Mekanism**: `mek_progress` (0 a 100%) e `mek_formed` (1 quando o multibloco está formado). Usam classes internas do Mekanism (`TileEntityProgressMachine`, `TileEntityMultiblock`); se uma versão futura as mudar, só essas duas métricas se desligam.
- **Create**: cofre de itens e tanque já eram lidos por Itens e Fluido; há um gametest para o cofre.
- Não feito: químicos da rede ME (dependem do Applied Mekanistics), bytes usados nas células do AE2 (sem API direta), dados de reator e turbina do Mekanism, cenas do Ponder, peripheral do CC:Tweaked.
