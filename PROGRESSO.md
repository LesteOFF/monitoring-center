# PROGRESSO — Central de Monitoramento

Plano: `C:\Users\Eduardo\.claude\plans\mod-central-de-monitoramento.md`

O histórico de mudanças por versão está em `CHANGELOG.md`. Este arquivo guarda só o estado atual, as decisões e o que falta.

## Estado
- Versão: **2.1.0** (`gradle.properties`). O jar `build/libs/monitoringcenter-2.1.0.jar` é o que está em `D:\curseforge\minecraft\Instances\NostagiaCraft\mods\`.
- Concluídas: fases 0 a 7, a etapa final de assets e os lotes 4, 5 e 6 do plano de melhorias.
- Última verificação registrada: `gradlew --offline -q build test runGameTestServer` verde, 22 gametests e 87 testes JUnit; `gradlew --offline -q runPackGameTestServer` verde com 28 gametests (os 22 mais 6 de integração, com os mods do pack).
- **Falta, fora do projeto:** rodar `D:\curseforge\minecraft\Instances\NostagiaCraft\tools\build_server_folder.ps1`, subir para o servidor **antes** de abrir o jogo, e a conferência dentro do jogo (lista no fim).
- **Orçamentos de desempenho não foram medidos com o spark** (exige o jogo aberto). Só há a guarda `SchedulerBudgetTest` (agendador + séries de 64 sensores muito abaixo de 0,05 ms por tick, sem contar a leitura das capacidades).

## Não feito
- Químicos da rede ME (dependem do Applied Mekanistics), bytes usados nas células do AE2 (sem API direta), dados de reator e turbina do Mekanism, cenas do Ponder, periférico do CC:Tweaked.
- Rolagem no editor de linhas (há paginação) e página no JEI.
- Lote 7: tablet portátil, canal entre dimensões, chunk loading pela Central, exportar CSV, Atuador.
- Fora, por decisão: tela de configuração própria (o pack já tem o Configured) e webhook do Discord.
- Ainda por confirmar no jogo: se o valor errado no Sophisticated Storage vinha do cache de capacidade; se o 0 no barril e no Creative Bin era filtro indevido; energia acima de 2,1 G FE com o Mekanism.

## Como reconstruir
- Build: `gradlew -q build test runGameTestServer`. Com os mods do pack: `gradlew -q runPackGameTestServer`.
- `libs/` (fora do git) guarda os jars aninhados do Create usados só para compilar: `unzip -o -j create-1.21.1-6.0.10.jar 'META-INF/jarjar/*.jar' -d libs`.
- Ícones do painel: `"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java" tools/GenerateAssets.java`. Fonte: o mesmo com `tools/GenerateFont.java`. O `java` do PATH é JRE 25 sem compilador; o `javap` do PATH é JDK 8 e serve.
- O ícone da Chave de Canal e o modelo da Tela vieram do Blockbench. As texturas atuais da Central e do Sensor foram refeitas com `image_gen`, com fontes/prompts em `output/art-source-v2/`; `java tools/ImportBlockTextures.java` gera esses dois atlas 32×32 e as pranchas. O Acoplador referencia materiais nativos do Create. A Tela mantém o atlas 64×64 da primeira revisão.

## Fase 0 — respostas
- **Render da Tela (reescrito em 13/10/2026)**: a pintura em `TextureTarget` fora da tela falhou no jogo (conteúdo errado, ausente ou congelado) e foi removida. Agora o painel é desenhado direto no mundo pelo `PanelRenderer`, todo frame, como o monitor do CC:Tweaked: pixel fixo de 1/64 de bloco, margem de 4 px, linhas de 10 px; Tela maior mostra mais linhas e colunas (`PanelLayout`). Preenchimentos em `RenderType.textBackground()`, fonte e ícones em `RenderType.text(...)`. Sem dígitos rolando e sem `pixelsPerBlock`.
- **Grid ME a partir de um vizinho**: `GridHelper.getNodeHost(level, pos)` → `getGridNode(Direction)` → `getGrid()`.
- **Bloco cinético sem Registrate**: sim, `KineticBlock` + `IBE<T>` com `DeferredRegister` comum, registrado só com Create presente.

## Decisões que mudaram algo do plano
- `MetricReader` é interface de um método; ícone e unidade ficam no record `Metric`.
- `MonitorNetwork` é `SavedData` por dimensão; a caixa de recados fica na instância do Overworld.
- `SensorScheduler` é função pura sobre todos os sensores da dimensão (garante um sensor de área por tick entre canais).
- Séries em `float` (7 KB por sensor): acima de ~16 M a taxa perde resolução fina.
- Canal padrão de um bloco novo: `principal` do dono. Renomeia-se na tela do bloco ou com a Chave de Canal (agachar copia, clicar aplica).
- Filtro de item do Sensor: clique direito com o item na mão, só em métricas com filtro; repetir o item limpa. Mobs filtram por ovo de spawn.
- Canal sem observador e sem regra amostra a cada 5 s, com a série alinhada em 1 amostra = 1 s.
- Bateria com histerese: depois de desligar, a Central só religa com 1 s de custo guardado. Aviso "Central desligada" respeita `noticeCooldownSeconds` e não sai por simples descarga de chunk.
- A Central precisa estar em chunk carregado; sem energia ela também não emite redstone.
- Multibloco da Tela: só retângulo cheio de até 12×8 vira painel único; fora disso cada bloco é 1×1. A formação roda 1 tick depois da colocação. Na fusão doa o maior painel existente; ao quebrar o controlador, canal e configuração passam para os pedaços.
- Linhas automáticas: lista vazia mostra os sensores do canal em ordem. Corante numa linha automática materializa as linhas. O editor mostra 7 linhas e preserva as demais (até 16).
- **Soma**: soma todos os sensores do canal com a mesma métrica (nome + métrica), não uma lista escolhida. Até 8 por canal.
- Limite em porcentagem = regra sobre a derivada "Porcentagem". Histerese relativa ao limite (5%, piso de 1 unidade); âmbar = 3 margens.
- Até 8 regras por canal. Regras e somas vivem no canal e apontam para sensores dele: ao trocar o canal da Central, o que foi editado na tela é gravado no canal antigo, e o canal novo mantém as regras que já tinha.
- Aviso adiado, não descartado: alarme ativo dentro do cooldown é avisado quando o cooldown vence; o "resolvido" só sai se o "disparou" saiu.
- "Vidas do time" = menor número de vidas entre os membros online.
- Radiação, ambiente, servidor e time são métricas sem vizinho (cadência de 5 s).
- Linhas vindas de Display Link ficam na Tela até ela ser reconfigurada.
- Integrações carregam por nome via reflexão, só com o mod presente (com method reference o verificador da JVM carregava classes do AE2 ausente). Erro de leitura devolve "sem amostra"; só `LinkageError` desliga a integração.
- Comentários `ponytail:` não são usados (regra "sem comentários no código").

## Atalhos conhecidos (teto e como subir)
- Mais de 100 sensores de área na dimensão: o período deles cresce para caber um por tick.
- Componente de Telas com mais de 192 blocos conectados: o excedente fica fora da formação.
- Pacotes do cliente: até 40 por segundo por jogador (`RateLimit`); o excedente é descartado e o histórico é pedido de novo sozinho.
- `ClientChannels`: o servidor avisa (`ChannelClosed`) quando o jogador deixa de observar um canal. A visão aberta só pela GUI da Central, longe de qualquer Tela, ainda fica até o logout.
- Stress do Create: um cálculo por rede por segundo, compartilhado entre os sensores dela (o custo de cada cálculo continua proporcional ao tamanho da rede).
- Recados sem retirada há mais de 30 dias são descartados ao carregar o mundo (cobre o time do FTB Teams desfeito).
- Acoplador Cinético agora tem renderer próprio para o meio eixo nativo do Create, independente do visualizer Flywheel; falta conferir a rotação na cena completa do jogador.

## Integrações puladas
- Nenhuma: AE2, Mekanism, Create, Ars Nouveau, Third Life Plus, FTB Teams e Jade estavam em `mods/`. Aether e Kaleidoscope não entram neste mod.
- Mekanism, AE2 e Create têm gametests próprios em `gametest/IntegrationGameTests`, que só são registrados no run `packGameTestServer` (carrega os jars do pack de `pack_mods_dir`, mundo em `run-pack/`). Ars Nouveau, Third Life Plus, FTB Teams e Jade carregam nesse run mas não têm teste próprio.

## Assinaturas conferidas (javap)
AE2 19.2.18
- `GridHelper.getNodeHost(Level, BlockPos): IInWorldGridNodeHost`; `getGridNode(Direction): IGridNode`; `IGridNode.getGrid(): IGrid` (lança `IllegalStateException` em nó destruído)
- `IGrid.getStorageService()/getEnergyService()/getCraftingService()/getPathingService()`
- `IStorageService.getCachedInventory(): KeyCounter`; `KeyCounter.get(AEKey): long`; `AEItemKey.of(ItemStack)`
- `IEnergyService.getStoredPower()/getMaxStoredPower()/getAvgPowerUsage()/getAvgPowerInjection(): double`
- `IPathingService.getUsedChannels(): int`; `ICraftingService.getCpus(): ImmutableSet<ICraftingCPU>`; `ICraftingCPU.isBusy()`

Create 6.0.10
- `KineticBlockEntity(BlockEntityType<?>, BlockPos, BlockState)`; `getSpeed(): float`; `isOverStressed()`; `hasNetwork()`; `getOrCreateNetwork(): KineticNetwork`; `tick()`
- `KineticNetwork.calculateStress()/calculateCapacity(): float`
- `KineticBlock(Properties)` + `IRotate.hasShaftTowards(LevelReader, BlockPos, BlockState, Direction)`, `getRotationAxis(BlockState)`
- `IBE<T>.getBlockEntityClass()/getBlockEntityType()`
- `BlockStressValues.IMPACTS: SimpleRegistry<Block, DoubleSupplier>` → `register(K, V)`
- `CreateRegistries.DISPLAY_TARGET/DISPLAY_SOURCE: ResourceKey<Registry<...>>`
- `DisplayTarget.acceptText(int, List<MutableComponent>, DisplayLinkContext)`, `provideStats(DisplayLinkContext)`; `DisplayTarget.BY_BLOCK.register(Block, DisplayTarget)`
- `DisplaySource.provideText(DisplayLinkContext, DisplayTargetStats)`; `DisplaySource.BY_BLOCK.add(Block, DisplaySource)`; `DisplaySource.EMPTY`
- `DisplayTargetStats(int maxRows, int maxColumns, DisplayTarget type)`; `DisplayLinkContext.level()/getTargetPos()/getSourceBlockEntity()`

Mekanism 10.7.19
- `mekanism.common.capabilities.Capabilities.CHEMICAL.block(): BlockCapability<IChemicalHandler, Direction>`; `Capabilities.HEAT: BlockCapability<IHeatHandler, Direction>`
- `IChemicalHandler.getChemicalTanks()/getChemicalInTank(int): ChemicalStack/getChemicalTankCapacity(int): long`; `ChemicalStack.getAmount(): long`
- `IHeatHandler.getTotalTemperature(): double`; `IRadiationManager.INSTANCE.getRadiationLevel(Level, BlockPos): double`

Ars Nouveau 5.13.3
- `CapabilityRegistry.SOURCE_CAPABILITY: BlockCapability<ISourceCap, Direction>`; `ISourceCap.getSource()/getSourceCapacity(): int`; `ISourceTile.getSource()/getMaxSource(): int`

Third Life Plus 1.1.4
- `ThirdLifePlusModVariables.PLAYER_VARIABLES: Supplier<AttachmentType<PlayerVariables>>`; campo `double Lives`

FTB Teams 2101.1.11
- `FTBTeamsAPI.api().isManagerLoaded()/getManager()`; `TeamManager.getTeamForPlayer(ServerPlayer)/getTeamByID(UUID): Optional<Team>`; `Team.getTeamId()/getMembers()`

Jade 15.10.6: `registerBlockDataProvider`, `registerBlockComponent`, `IServerDataProvider<BlockAccessor>`.

Cliente (NeoForge 21.1.256)
- `RenderType.text(ResourceLocation)` e `RenderType.textBackground()` (ambos com cull; quads na ordem do `BakedGlyph`), `PoseStack.mulPose(Matrix4f)`, `ItemRenderer.renderStatic(ItemStack, ItemDisplayContext, int, int, PoseStack, MultiBufferSource, Level, int)`, `LevelRenderer.getFrustum()`

Mod ids no pack: `ae2`, `mekanism`, `create`, `ars_nouveau`, `third_life_plus`, `ftbteams`, `jade`.

## Só dá para conferir dentro do jogo
Render e desempenho (o risco maior do plano)
- A Tela aparece com Sodium + ImmediatelyFast nas quatro direções? Texto não espelhado e dentro da moldura, ícones de item e de fluido corretos, mais linhas e colunas conforme a Tela cresce, valores mudando a cada segundo.
- Painel 12×8 que cruza seções de chunk não some ao olhar de lado (`shouldRenderOffScreen` + teste de frustum próprio).
- `/sparkc profiler` diante de uma Tela 12×8 cheia: queda de FPS ≤ 2%; nada do mod no perfil com a Tela fora de vista. `/spark profiler` com 64 sensores: ≤ 0,05 ms por tick. Repetir com `performanceMode`.
- Os cinco modos, pulso vermelho em alarme, três temas de dia e no escuro, fonte legível.

Jogo
- Telas do Sensor, da Tela e da Central (abas Somas e Alarmes; a lista usa até 8 linhas) cabem na escala de GUI usada.
- Corante pinta a linha clicada (agora pela altura do clique, não pelo bloco); Chave de Canal em bloco não controlador da Tela.
- Dois jogadores do mesmo time: um sai, o alarme dispara, ele entra e recebe o recado. Agrupamento de avisos simultâneos.
- Redstone da Central liga e desliga com o alarme.
- Cada integração presente: ME (item com filtro, energia, canais, CPUs), Mekanism (químico, temperatura, radiação), Ars (jarro de Source), vidas do Third Life Plus, FTB Teams (time recebe os avisos), Jade no Sensor.
- Create: Acoplador encostado na Central com eixo a 32 RPM ou mais mantém o canal sem FE e aparece no stress da rede; Display Link apontado para a Tela escreve linhas; Sensor como fonte num Display Board.
- Um dia rodando: gráfico de 6 h e de 7 d, e o tamanho do `monitoringcenter.dat`.
- Receitas (Sensor, Central, Tela, Chave de Canal, Acoplador) estão coerentes com o pack.
