# Central de Monitoramento

Mod NeoForge para **Minecraft Java 1.21.1**. Sensores leem o que está ao lado deles (energia, itens, fluidos, redes do AE2, máquinas do Mekanism, eixos do Create…). Uma Central junta as leituras de um canal. As Telas mostram tudo como lista, cartões, gráfico ou medidores, e os alarmes avisam o time quando algo sai do normal.

- Versão atual: **2.1.0**
- Addon: **Auditor de Fábrica** (`../factory-auditor`), que exige a Central `[2.1.0,3)`.
- Idiomas: inglês (`en_us`) e português (`pt_br`).

## Requisitos

| | Versão |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 ou mais novo da 21.1.x |
| Java | 21 |

Instale o jar em `mods/` no cliente **e** no servidor. Os dois precisam do mesmo jar, porque o protocolo de rede é versionado. Nenhum outro mod é obrigatório.

## Blocos e itens

| Nome | Id | Para que serve |
|---|---|---|
| Sensor | `sensor` | Encostado num bloco, lê uma métrica dele (ou da área em volta) e manda para o canal. |
| Central de Monitoramento | `central` | Guarda o histórico do canal, roda as somas e os alarmes. Gasta 40 FE/t, com bateria de 5 s. |
| Tela | `screen` | Mostra o canal. Telas lado a lado formam um painel único de até 12×8, com moldura conectada. |
| Acoplador Cinético | `kinetic_coupler` | Só com o Create: alimenta a Central por um eixo a 32 RPM ou mais, no lugar de FE. |
| Chave de Canal | `channel_key` | Agachar e clicar copia o canal de um bloco; clicar aplica em outro. |

Quebrar uma Tela guarda o canal e a configuração no item.

## Métricas

Cada Sensor lê uma métrica. "Automática" escolhe sozinha pelo bloco encostado (máquina → saída de itens, inventário → itens, e assim por diante).

- **Bloco ao lado**: `energy`, `fluid`, `fluid_total`, `items`, `item_flow` (entrada), `item_out` (saída), `redstone`.
- **Área e mundo**: `time`, `weather`, `light`, `tps`, `mspt`, `players`, `players_near`, `team_online`, `mobs`, `animals`, `ground_items`, `crops`, `xp`.
- **AE2**: `ae2_items`, `ae2_fluid`, `ae2_crafting`, `ae2_types`, `ae2_energy`, `ae2_usage`, `ae2_injection`, `ae2_channels`, `ae2_cpus`.
- **Mekanism**: `mek_chemical`, `mek_heat`, `mek_radiation`, `mek_progress`, `mek_formed`.
- **Create**: `create_speed`, `create_stress`, `create_overstressed`.
- **Ars Nouveau**: `ars_source`. **Third Life Plus**: `team_lives`.
- **Auditor de Fábrica** (addon): `audit_rate`.

Filtros: itens, entrada, saída, itens no chão e itens da rede ME aceitam um item ou uma tag. `mobs` e `animals` aceitam um ovo de spawn para contar um tipo só. `fluid_total` e `ae2_fluid` aceitam um balde do fluido.

Cada linha pode mostrar o valor, a porcentagem, a taxa (por tick, segundo ou minuto), a previsão ("enche em…"), o menor, o maior ou a média, em períodos de 5 min, 6 h ou 7 d.

## Telas

- Modos: **Lista**, **Cartões**, **Gráfico**, **Destaque**, **Alarmes** e **Medidores**.
- 9 cores de tema. Vermelho e laranja ficam reservados para os alarmes.
- Escala de texto de 1x a 4x, páginas quando o conteúdo não cabe, título opcional e um ícone por linha.
- A métrica `time` aparece como `HH:MM`; no modo Destaque vira um relógio grande.
- **Somas**: até 8 por canal, cada uma como soma, média, menor ou maior dos Sensores com a mesma métrica.
- Com o Create, um Display Link escreve linhas na Tela, e o Sensor serve de fonte para um Display Board.

## Alarmes

- Até 8 regras por canal: **abaixo de**, **acima de** ou **sem leitura**. Severidades: info, aviso e crítico.
- Cada regra tem um tempo de espera (0 a 3600 s) e um período para as regras de menor, maior e média.
- A Central emite redstone com o alarme, e um comparador lê 5, 10 ou 15 conforme a severidade.
- Agachar e clicar na Central silencia os alarmes ativos.
- Avisos no chat para o dono ou para o time (FTB Teams). Quem estiver offline recebe na próxima entrada (até 50 recados).

## Integrações

Todas são opcionais e só carregam com o mod presente: AE2, Mekanism, Create, Ars Nouveau, Third Life Plus, FTB Teams e Jade.

## Configuração

Servidor (`monitoringcenter-server.toml`):

| Chave | Padrão | O que faz |
|---|---|---|
| `maxSensorsPerChannel` | 64 | Sensores por canal. |
| `areaMaxRadius` | 8 | Raio máximo das métricas de área. |
| `centralFePerTick` | 40 | Consumo da Central. |
| `batterySeconds` | 5 | Segundos de energia guardada na Central. |
| `noticeCooldownSeconds` | 300 | Intervalo mínimo entre avisos do mesmo alarme. |
| `alarmHysteresisPercent` | 5 | Margem para o alarme resolver sem ficar piscando. |
| `couplerMinRpm` | 32 | Rotação mínima do Acoplador. |
| `couplerStressImpact` | 4 | Stress que o Acoplador cobra da rede do Create. |

Cliente: `performanceMode` (troca os gráficos de linha por barras e desliga o pulso de alarme da Tela).

## Compilar e testar

```bash
./gradlew build                  # build/libs/monitoringcenter-2.1.0.jar
./gradlew test                   # testes JUnit
./gradlew runGameTestServer      # gametests num servidor real
./gradlew runPackGameTestServer  # gametests com os mods do pack carregados
```

`libs/` (fora do git) guarda os jars aninhados do Create usados só para compilar. Para recriá-la: `unzip -o -j create-1.21.1-6.0.10.jar 'META-INF/jarjar/*.jar' -d libs`.

## Documentos

- `CHANGELOG.md`: o que mudou em cada versão.
