# Observabilidade

## Tracing

No campo da **observabilidade**, o **Tracing** (ou rastreamento distribuído) é a capacidade de acompanhar e registrar o caminho percorrido por uma única requisição à medida que ela atravessa os diversos serviços e componentes de um sistema. Ele é essencial em arquiteturas de microsserviços, onde um fluxo iniciado pelo cliente pode disparar uma longa cadeia de invocações entre diferentes sistemas, tornando difícil identificar onde ocorrem falhas ou problemas de desempenho.

Os principais conceitos e componentes que definem o Tracing são:

*   **Trace ID**: Cada requisição que entra no sistema recebe um **identificador único** chamado Trace ID. Esse ID é propagado entre os serviços (geralmente via cabeçalhos HTTP) para que todas as ações realizadas em nome daquela requisição possam ser correlacionadas.
*   **Span**: Representa uma **unidade individual de trabalho** dentro de um rastreamento. Um span pode ser a execução de um método específico, uma chamada a um banco de dados ou uma requisição remota a outro serviço.
*   **Estrutura de Árvore**: Os spans costumam ser aninhados, formando uma estrutura em árvore que permite visualizar a hierarquia das operações e exatamente quanto tempo cada parte do processamento levou para ser concluída.
*   **Metadados (Tags e Logs)**: É possível adicionar informações extras aos spans, como **Tags** (pares chave-valor para busca e filtragem, como o código de status HTTP) e **Logs** (mensagens de erro ou eventos específicos ocorridos naquele momento da execução).

### Padrões e Ferramentas Utilizadas
Atualmente, o padrão de mercado para implementar o rastreamento é o **OpenTelemetry (OTel)**, um conjunto de APIs e protocolos agnósticos a fornecedores que unifica a coleta de dados de telemetria. Para a visualização e análise desses dados, utilizam-se ferramentas como o **Jaeger** ou o **Zipkin**, que oferecem painéis onde é possível ver cronogramas detalhados das requisições e realizar a análise de causa raiz de falhas.

No contexto do **Quarkus**, o rastreamento é facilitado por extensões como `quarkus-opentelemetry`, que permitem que endpoints REST sejam rastreados automaticamente por padrão. Os desenvolvedores também podem customizar o rastreamento usando anotações como `@Traced` ou `@WithSpan` para incluir métodos específicos na cadeia de monitoramento ou até mesmo instrumentar conexões JDBC para visualizar a latência de consultas SQL.

---

A diferença entre **Logging**, **Monitoring** (Monitoramento) e **Tracing** (Rastreamento) reside na natureza dos dados coletados e na finalidade da análise dentro do campo da observabilidade. Embora todos visem entender o estado interno de um sistema, eles operam de formas distintas:

### **Logging (Registros de Eventos)**
*   **Definição:** Representa registros discretos de algo significativo que aconteceu durante a execução de um programa. 
*   **Características:** Uma linha de log é um tipo de **evento** que geralmente inclui uma classificação de severidade. Em sistemas distribuídos modernos, os logs devem ser tratados como fluxos contínuos capturados e armazenados separadamente.
*   **Objetivo:** É um fator chave para o **debug** (depuração) e monitoramento da saúde geral de uma aplicação, fornecendo detalhes textuais sobre falhas ou comportamentos específicos.

### **Monitoring e Metrics (Monitoramento e Métricas)**
*   **Definição:** É uma **observação ativa** do comportamento do sistema, frequentemente emparelhada com mecanismos de alerta.
*   **Características:** Baseia-se principalmente em **métricas**, que são valores numéricos (como consumo de memória, uso de CPU ou número de requisições por minuto). Ao contrário do log, que é um registro de fato, a métrica permite plotar gráficos e gerar estatísticas agregadas.
*   **Objetivo:** Prover uma visão em tempo real da saúde e desempenho do sistema, permitindo identificar proativamente comportamentos anômalos e tomar decisões arquiteturais baseadas em dados históricos e atuais.

### **Tracing (Rastreamento Distribuído)**
*   **Definição:** É a capacidade de seguir o **caminho de uma única requisição** à medida que ela atravessa múltiplos serviços e componentes de um sistema.
*   **Características:** Utiliza **Trace IDs** exclusivos para correlacionar ações e **Spans**, que representam unidades individuais de trabalho (como uma chamada de banco de dados ou uma invocação de método). O resultado costuma ser visualizado em uma estrutura de árvore.
*   **Objetivo:** É essencial para o diagnóstico de problemas em arquiteturas de microserviços, ajudando a encontrar **gargalos de desempenho** e entender a latência entre chamadas de API.

### **Resumo Comparativo**
*   O **Logging** responde a pergunta: *"O que aconteceu em um momento específico?"*.
*   O **Monitoring** responde: *"Como o sistema está se comportando agora e quais são as tendências?"*.
*   O **Tracing** responde: *"Por onde a requisição passou e por que ela demorou ou falhou em um ponto específico do fluxo?"*.


---

Com base na aula "Implemente Observabilidade COMPLETA no Quarkus com OpenTelemetry" do canal *Hugo Santos - O Programador de Sucesso*, extraí os conceitos centrais. Como o vídeo foca bastante na implementação prática (o *Como?*), estruturei este resumo focando no **O Quê?** e no **Por Quê?**, complementando com analogias pedagógicas para facilitar o aprendizado.

Abaixo estão os conceitos mais importantes, suas explicações e a análise de atualização (considerando o cenário tecnológico atual).

---

### 1. Observabilidade (Observability)
* **O Que é?** É a capacidade de entender o estado interno de um sistema analisando as saídas que ele gera (Logs, Métricas e Traces). 
* **Por Que usar?** Imagine dirigir um carro com o painel totalmente apagado e o capô selado. Você só vai saber que há um problema quando o carro parar de funcionar. A observabilidade acende o painel. Ela permite que você identifique gargalos de performance, descubra a causa raiz de bugs complexos e aja *antes* que o sistema caia completamente, melhorando a experiência do usuário final.
* **Necessidade de Atualização:** **Nenhuma.** O conceito de observabilidade continua sendo o pilar fundamental para qualquer arquitetura moderna de microsserviços. Sem ela, operar sistemas distribuídos em nuvem é "voar às cegas".

### 2. OpenTelemetry (OTel)
* **O Que é?** É um projeto de código aberto da CNCF (Cloud Native Computing Foundation) que fornece um padrão unificado (APIs, SDKs e ferramentas) para instrumentar, gerar, coletar e exportar dados de telemetria (métricas, logs e traces).
* **Por Que usar?** Antes do OpenTelemetry, cada ferramenta de monitoramento exigia que você usasse a biblioteca proprietária dela (o famoso *vendor lock-in*). Se você quisesse mudar do Datadog para o New Relic, precisava reescrever o código. O OpenTelemetry padronizou isso. Você instrumenta o código uma única vez usando o padrão OTel e pode enviar esses dados para qualquer plataforma do mercado.
* **Necessidade de Atualização:** **Super atual.** O OpenTelemetry consolidou-se como o padrão absoluto da indústria. Uma complementação importante é que hoje ele não foca apenas em traces e métricas, mas a especificação para **Logs** também já atingiu grande maturidade, unificando os "Três Pilares da Observabilidade" em um só lugar.

### 3. Tracing Distribuído (usando Jaeger)
* **O Que é?** É o acompanhamento do caminho completo que uma requisição faz dentro de um sistema. Quando um usuário clica em "Comprar", essa ação pode passar por 5 microsserviços diferentes e 2 bancos de dados. O tracing distribuído funciona como o **código de rastreio dos Correios**, gerando um identificador único para a requisição e marcando quanto tempo ela passou em cada etapa (chamadas de *Spans*). No vídeo, o **Jaeger** é usado como a interface visual para ler esses rastros.
* **Por Que usar?** Em sistemas distribuídos, descobrir *onde* uma requisição falhou ou *por que* ela está lenta é como procurar uma agulha num palheiro. O tracing mostra um gráfico visual exato: "A requisição levou 2 segundos, mas 1.8 segundos foi culpa de uma query malfeita no banco de dados de Pagamentos".
* **Necessidade de Atualização:** O conceito continua impecável. O Jaeger ainda é excelente para visualização local, mas vale complementar que, no mercado corporativo atual, ferramentas como **Grafana Tempo** ou soluções em nuvem geridas ganharam muita força ao lado do Jaeger.

### 4. Métricas e Micrometer (com Prometheus)
* **O Que é?** Métricas são números agregados sobre o comportamento do sistema em um período de tempo. O **Micrometer** é uma biblioteca que funciona como uma "fachada" para métricas em Java (similar ao que o SLF4J faz para logs). O **Prometheus** é o formato e a ferramenta que vai armazenar essas métricas. O professor mostra métricas automáticas e também ensina a criar métricas customizadas (como contar quantas vezes um método específico foi chamado usando `@Counted`).
* **Por Que usar?** Enquanto o Tracing olha para uma requisição *individual*, as métricas olham para o *todo*. Elas respondem a perguntas como: "Qual a taxa de erros por minuto?", "Qual o consumo médio de memória nos últimos 15 minutos?". É através de métricas que você configura alertas automáticos (ex: "Se o uso de CPU passar de 80%, me mande um alerta").
* **Necessidade de Atualização:** O professor cita a transição do Quarkus de abandonar o *MicroProfile Metrics* em favor do *Micrometer*. Essa é uma informação valiosa e continua **100% precisa**. O Micrometer venceu a "batalha" de padrões no ecossistema Java (sendo o padrão tanto no Spring Boot quanto no Quarkus). 

### 5. Quarkus Dev Services (Bônus de Produtividade)
* **O Que é?** É uma funcionalidade do framework Quarkus que detecta automaticamente quais extensões você está usando e sobe os contêineres necessários via Docker/Podman em tempo de desenvolvimento. No vídeo, ao adicionar a dependência do PostgreSQL, o Quarkus subiu um banco de dados sozinho, sem o professor precisar configurar uma string de conexão ou um `docker-compose`.
* **Por Que usar?** Foco na Experiência do Desenvolvedor (DX - *Developer Experience*). Antigamente, rodar um projeto local exigia um longo manual de instruções ("suba o banco X, configure a porta Y, suba a fila Z"). O Dev Services tira essa carga cognitiva. Você foca apenas em escrever o código do seu negócio, e a infraestrutura local se molda às suas necessidades automaticamente.
* **Necessidade de Atualização:** **Mais forte do que nunca.** O conceito de Dev Services baseado no projeto *Testcontainers* revolucionou o desenvolvimento Java moderno. Hoje, ele suporta dezenas de serviços (Kafka, Redis, Keycloak, etc.) e é uma das maiores vantagens competitivas do Quarkus.

### Resumo da Ópera
O vídeo acerta em cheio ao demonstrar uma pilha de arquitetura moderna. Se o seu foco for o aprendizado conceitual, lembre-se desta tríade: **Métricas** dizem *se* há um problema, **Traces** dizem *onde* está o problema, e os **Logs** dizem *qual* foi o erro exato. O OpenTelemetry é a cola que une os três.

---

Na prática, o **OpenTelemetry (OTel)** funciona como um conjunto padronizado de APIs, bibliotecas e protocolos projetados para coletar e exportar dados de telemetria (rastreamento, métricas e logs) de forma agnóstica a fornecedores. 

O funcionamento prático do OTel pode ser dividido nos seguintes pilares:

### 1. Instrumentação e Geração de Dados
A instrumentação é o processo de preparar o código para emitir dados. Ela pode ser:
*   **Automática**: Em frameworks como o Quarkus, ao adicionar a extensão `quarkus-opentelemetry`, todos os endpoints REST são **rastreados automaticamente** sem a necessidade de alterar o código de negócio.
*   **Manual**: Desenvolvedores podem usar anotações como `@WithSpan` em métodos específicos ou utilizar a API de `Tracer` para criar **spans customizados** e registrar unidades de trabalho detalhadas.

### 2. Propagação de Contexto
Para que o rastreamento distribuído funcione entre diferentes serviços, o OpenTelemetry utiliza a **propagação de contexto**. Isso significa que um **Trace ID** único é gerado na primeira requisição e "viaja" junto com ela através de cabeçalhos (geralmente HTTP), permitindo que spans de diferentes microsserviços sejam correlacionados em uma única árvore de execução.

### 3. Protocolo OTLP e Coleta
O OpenTelemetry utiliza um protocolo de comunicação padronizado chamado **OTLP (OpenTelemetry Line Protocol)** para enviar os dados para fora da aplicação. O fluxo de dados geralmente segue este caminho:
*   **OpenTelemetry Collector (Opcional)**: Em implantações de larga escala, as aplicações enviam dados para um **Collector central**, que pode filtrar, agrupar e processar as informações antes de enviá-las ao destino final.
*   **Exportação Direta**: Em ambientes menores ou para testes, a aplicação pode exportar os dados via OTLP diretamente para um backend compatível, como o **Jaeger**.

### 4. Processamento e Visualização
Uma vez exportados, os dados são armazenados em ferramentas de análise. O **Jaeger** é uma das ferramentas mais comuns para visualizar esses rastreamentos, permitindo que engenheiros vejam cronogramas detalhados de cada requisição e identifiquem **gargalos de performance** ou falhas em microsserviços distribuídos.

### 5. Unificação de Sinais
Embora o foco inicial tenha sido o rastreamento distribuído, o OTel busca unificar outros sinais de observabilidade. Por exemplo, a extensão `quarkus-micrometer-opentelemetry` permite que métricas do Micrometer e rastreamentos do OTel sejam enviados por uma saída unificada utilizando o protocolo OTLP.

---

Os **spans** são fundamentais para a observabilidade porque representam a **unidade básica de trabalho** dentro de um rastreamento (*trace*) distribuído. Enquanto um *trace* acompanha a jornada completa de uma requisição, o span detalha o que aconteceu em uma operação específica, como a execução de um método, uma consulta ao banco de dados ou uma chamada a outro serviço.

Abaixo, detalho os pontos que tornam os spans essenciais para o diagnóstico de sistemas:

### 1. Estrutura Hierárquica e Visibilidade do Fluxo
Os spans são tipicamente **aninhados**, o que significa que um span pode conter outros "spans filhos", formando uma estrutura em árvore. Essa hierarquia permite que o desenvolvedor visualize exatamente:
*   **A ordem das operações**: Qual serviço chamou qual e em que sequência.
*   **Onde o tempo foi gasto**: Um span registra o momento exato de início e fim da operação, permitindo calcular a latência individual de cada componente e identificar **gargalos de desempenho**.

### 2. Contexto e Enriquecimento com Metadados
Cada span carrega informações ricas que ajudam a entender o cenário de uma falha ou lentidão:
*   **Trace ID e Span ID**: Identificadores únicos que permitem correlacionar spans de diferentes serviços que pertencem à mesma transação.
*   **Tags**: Pares chave-valor com metadados sobre a operação, como a URL acessada, o método HTTP, o código de status da resposta ou até informações customizadas como o ID de um cliente.
*   **Logs (ou Eventos)**: Mensagens associadas a momentos específicos dentro do span, muito úteis para capturar exceções ou mensagens de erro detalhadas no contexto exato onde ocorreram.

### 3. Identificação de Causas Raiz
Sem os spans, em uma arquitetura de microsserviços, seria extremamente difícil saber se uma falha ocorreu devido a uma lentidão na rede, uma consulta SQL ineficiente ou um erro de lógica interna de um serviço específico. Ao examinar um span de banco de dados, por exemplo, é possível ver a **declaração SQL exata** que foi executada e quanto tempo ela demorou, facilitando a análise de causa raiz.

### 4. Automatização e Customização
Em frameworks modernos como o Quarkus, os spans para endpoints REST são gerados **automaticamente**. No entanto, sua importância é tão grande que os desenvolvedores podem criar **spans customizados** para qualquer método de negócio usando anotações como `@WithSpan`, garantindo que partes críticas da lógica interna também sejam monitoradas detalhadamente.

Em resumo, os spans transformam uma massa confusa de logs e métricas em uma **linha do tempo visual e contextualizada**, essencial para manter a resiliência e a responsividade de sistemas distribuídos.

Os **traces** (ou rastreamentos) são fundamentais para a observabilidade porque oferecem uma **visão panorâmica e detalhada da jornada completa de uma requisição** em um sistema distribuído. Enquanto os logs registram eventos isolados, o tracing "conecta os pontos", permitindo entender as interações complexas entre múltiplos microsserviços.


---


A importância dos traces pode ser resumida nos seguintes pontos principais:

### 1. Visualização do Fluxo de Requisições Distribuídas
Em arquiteturas modernas, uma única chamada de um cliente pode disparar uma cadeia de invocações entre dezenas de serviços. O trace permite:
*   **Seguir o caminho visualmente**: Ele funciona como um mapa que mostra por onde a requisição passou, atravessando fronteiras de rede e segurança.
*   **Entender dependências**: Ajuda a visualizar como os serviços dependem uns dos outros para completar uma tarefa específica.

### 2. Identificação Precisa de Gargalos de Desempenho
Sem o tracing, é extremamente difícil saber por que uma resposta está lenta em um sistema com muitos componentes.
*   **Análise de latência**: O trace mostra não apenas onde a requisição foi, mas **exatamente quanto tempo cada parte do processamento levou**.
*   **Localização de contenção**: Permite identificar rapidamente qual serviço ou componente específico (como um banco de dados ou um broker de mensagens) está causando atrasos no tempo de resposta final.

### 3. Diagnóstico e Análise de Causa Raiz
Quando ocorre um erro em uma cadeia de chamadas, o tracing é a ferramenta mais eficaz para o debug.
*   **Isolamento de falhas**: Engenheiros de operações podem determinar com facilidade em qual ponto exato da plataforma o problema ocorreu.
*   **Contextualização de erros**: Ferramentas como o **Jaeger** permitem ver metadados e logs de erro associados a cada etapa (span), facilitando a análise da causa raiz sem precisar vasculhar logs centrais de forma desconectada.

### 4. Correlação de Dados (Trace ID)
A grande "mágica" do tracing é o **Trace ID**, um identificador único anexado a cada requisição.
*   **Propagação de Contexto**: Esse ID viaja com a requisição (geralmente via cabeçalhos HTTP), permitindo correlacionar spans de diferentes serviços em uma única árvore de execução.
*   **Unificação com Logs**: É possível adicionar o Trace ID às mensagens de log, permitindo que o desenvolvedor filtre todos os logs de diferentes containers que pertencem a uma única transação específica.

Em resumo, os traces transformam a complexidade invisível de um sistema distribuído em um **fluxograma temporal compreensível**, sendo indispensáveis para manter a resiliência e a performance em ambientes de nuvem.

Para ligar a chamada de um serviço com sua resposta no tracing distribuído, utiliza-se um identificador único universal chamado **Trace ID**, que é anexado a cada requisição que entra no sistema. Esse ID funciona como um "fio condutor" que acompanha a requisição em toda a sua jornada, permitindo que todas as partes de uma transação distribuída sejam correlacionadas, independentemente de quantos serviços ela atravesse.

Abaixo estão os mecanismos práticos que permitem essa conexão:

*   **Propagação de Contexto:** O Trace ID é propagado entre os serviços através de metadados do protocolo de comunicação. Em requisições HTTP, isso é feito geralmente via **cabeçalhos HTTP**, garantindo que o serviço de destino receba o identificador e possa registrar suas próprias operações sob o mesmo rastreamento original.
*   **Estrutura de Spans (Pai e Filho):** Cada unidade de trabalho individual dentro de um trace é chamada de **Span**. Quando um serviço recebe uma chamada e executa uma operação (ou faz uma nova chamada a outro componente), ele cria um "span filho" que é vinculado ao "span pai" da requisição recebida. Essa hierarquia em árvore permite visualizar exatamente onde a chamada começou e onde a resposta foi gerada.
*   **Instrumentação Automática:** Em frameworks como o **Quarkus**, a integração com o **OpenTelemetry** permite que essa ligação ocorra de forma automática para endpoints REST. O sistema captura o início da chamada, gera os spans necessários e mantém o contexto ativo até que a resposta seja enviada de volta ao cliente.
*   **Visualização em Linha do Tempo:** Ferramentas de análise como o **Jaeger** ou **Zipkin** coletam esses IDs e spans para reconstruir o fluxo completo da requisição em um painel visual. Neles, é possível ver o gráfico de tempo (timeline), identificando o momento exato da chamada inicial, o processamento interno e o momento em que a resposta foi concluída.
*   **Tags e Metadados:** Para enriquecer a ligação entre chamada e resposta, os spans podem conter **tags** (como o código de status HTTP ou a URL acessada) e **logs** (como mensagens de erro específicas), facilitando o diagnóstico de falhas ocorridas durante o trajeto.