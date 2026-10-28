# ADR 001: Separação de Transações de Banco de Dados da Chamada ao Provedor Externo

## Status
**Aprovado**

## Contexto
O serviço de ativação de produtos necessita realizar chamadas HTTP remotas para um provedor externo (`POST /provider/v1/activate`). Serviços externos de telecomunicações e infraestrutura frequentemente apresentam latências variáveis, oscilando entre 300 ms em condições ideais e mais de 3 segundos durante picos de carga ou degradação de infraestrutura.

Se a chamada HTTP ao provedor for executada dentro do escopo de um método anotado com `@Transactional` (transação de banco de dados aberta):
1. A thread da aplicação retém uma conexão do pool de conexões (HikariCP) durante todo o tempo de espera do provedor externo.
2. Em um cenário com tráfego simultâneo e aumento de latência externa para 3 segundos, um pool típico de 20 a 50 conexões é rapidamente exaurido em poucos instantes.
3. Todas as demais operações do sistema (como `GET /api/v1/requests/{id}`) passam a falhar por *Connection Timeout* no pool, provocando falha em cascata (cascading failure) e derrubando a aplicação inteira.

Adicionalmente, existe o risco de *crash* do contêiner ou falha de rede exatamente no momento entre a gravação no banco e o envio ao provedor.

## Decisão
Adotou-se o princípio arquitetural de **estrita separação entre transações de banco de dados e chamadas I/O de rede (HTTP)**:

1. **Ciclo em Duas Fases Assíncronas:**
   - **Fase 1 (Inserção Imediata):** O endpoint `POST /api/v1/requests` persiste a solicitação com status `PENDING` em uma transação de banco de dados ultrarrápida (poucos milissegundos) e comita imediatamente.
   - **Fase 2 (Processamento Externo Assíncrono):** A orquestração da chamada ao provedor é delegada para o componente `ActivationProcessor`, executado em thread pool assíncrono dedicado (`@Async`).
   - A chamada HTTP remota é realizada **100% fora de qualquer transação de banco de dados**. Somente após a resposta (ou erro) do provedor ser recebida é que uma nova transação pontual é aberta para atualizar o status final (`COMPLETED` ou `FAILED`).

2. **Garantia de Entrega sem Perda (Reconciliation Scheduler):**
   - Para garantir que nenhuma solicitação fique órfã caso o contêiner sofra reboot ou crash entre a Fase 1 e a Fase 2, foi implementado o `ReconciliationScheduler`.
   - Este componente varre periodicamente solicitações que permaneceram em `PENDING` ou `PROCESSING` além do limiar de tolerância (ex: 30 segundos) e dispara o reprocessamento seguro.
   - Toda chamada ao provedor envia o cabeçalho `Idempotency-Key: {id}`, garantindo que o provedor não duplique a ativação física caso a solicitação já tenha sido executada antes de uma queda.

## Consequências

### Positivas:
- **Resiliência a lentidão externa:** Flutuações de latência do provedor externo afetam apenas o tempo de conclusão da solicitação assíncrona, sem prender conexões do banco de dados e sem degradar as leituras e cadastros.
- **Isolamento de recursos:** O pool de conexões com o PostgreSQL permanece livre e dimensionado apenas para transações de alta performance (OLTP).
- **Consistência eventual e tolerância a falhas:** O agendador de reconciliação garante que todas as solicitações registradas sejam efetivamente processadas até o estado terminal.

### Negativas / Mitigações:
- O status inicial retornado no `POST` é `PENDING`. Os clientes consultam o status atualizado via `GET /api/v1/requests/{id}` ou podem ser notificados via webhooks/mensageria em evoluções futuras.
