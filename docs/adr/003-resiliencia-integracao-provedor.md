# ADR 003: Estratégia de Resiliência e Máquina de Estados na Integração com Provedor Externo

## Status
**Aprovado**

## Contexto
A ativação física de produtos depende do endpoint `POST /provider/v1/activate` de um parceiro externo. Serviços de terceiros estão sujeitos a variados modos de falha:
- Respostas 400 (parâmetros inválidos, cliente sem cadastro no parceiro);
- Respostas 500/503 (indisponibilidade temporária de infraestrutura);
- Timeouts de conexão e de leitura (queda de link ou processamento excessivamente lento);
- Respostas 429 (Too Many Requests / Rate Limiting).

## Decisão
Estabeleceu-se uma matriz de decisão estrita combinando máquina de estados de domínio com padrões de resiliência:

### 1. Matriz de Comportamento por Retorno
| Retorno do Provedor | Novo Status | Nova Tentativa? | Justificativa |
| :--- | :--- | :--- | :--- |
| **Sucesso (2xx)** | `COMPLETED` | Não | O provedor confirmou a ativação. O protocolo retornado é persistido no registro. |
| **HTTP 400 (Bad Request)** | `FAILED` | Não | Erro de validação ou inconsistência cadastral. Retentar enviando os mesmos dados repetirá a falha. |
| **HTTP 500 / 502 / 503** | Mantém `PROCESSING` / Reagenda | Sim (até limite de 3x) | Falha de infraestrutura interna do parceiro. Deve ser retentada com backoff exponencial. |
| **Timeout de Conexão / Leitura** | Mantém `PROCESSING` | Sim (com `Idempotency-Key`) | O timeout é ambíguo: o provedor pode ter ativado antes de responder. A retentativa envia obrigatoriamente a chave de idempotência. |
| **HTTP 429 / 503 com `Retry-After`** | Mantém `PROCESSING` | Sim (respeitando intervalo) | Provedor sobrecarregado. Deve-se respeitar o cabeçalho `Retry-After` ou aplicar backoff com jitter para não agravar a sobrecarga. |

### 2. Timeouts Explícitos no Cliente HTTP
Configuração no `RestClient` com `SimpleClientHttpRequestFactory`:
- **Connect Timeout:** 2000 ms (tempo máximo para handshake TCP).
- **Read Timeout:** 3000 ms (tempo máximo aguardando os bytes da resposta).

### 3. Circuit Breaker (Resilience4j)
- Monitora a taxa de falhas nas chamadas externas (janela deslizante de 10 chamadas, limiar de 50% de falha).
- Se o provedor ficar fora do ar continuamente, o circuito abre por 10 segundos, rejeitando imediatamente novas chamadas (*fail-fast*) sem onerar as threads locais.

### 4. Máquina de Estados e Bloqueio Otimista (@Version)
- A entidade `ActivationRequest` gerencia suas transições através do enum `RequestStatus`.
- Transições inválidas (ex: de `COMPLETED` para `PROCESSING`) lançam `InvalidStatusTransitionException`.
- A coluna `version` (`@Version`) protege contra escritas concorrentes caso duas threads tentem alterar o status da mesma solicitação em paralelo.

## Consequências

### Positivas:
- Prevenção contra ativações duplicadas no parceiro através do `Idempotency-Key`.
- Proteção da saúde interna da aplicação via Circuit Breaker e timeouts agressivos.
- Transições de estado determinísticas e auditáveis.
