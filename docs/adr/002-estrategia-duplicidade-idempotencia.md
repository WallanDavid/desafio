# ADR 002: Estratégia contra Duplicidade e Garantia de Idempotência do externalId

## Status
**Aprovado**

## Contexto
O campo `externalId` representa a chave de negócio fornecida pelo sistema emissor da solicitação. Sistemas distribuídos frequentemente realizam tentativas de reenvio (*retries*) em casos de timeout de rede, além de clientes finais clicarem repetidamente em botões de confirmação.

Adicionalmente, requisições idênticas podem atingir instâncias diferentes da aplicação no mesmo milissegundo (*race condition*). 

Um padrão comum — porém falho — consiste em apenas executar um `findByExternalId` no banco e, caso não exista, efetuar o `save`. Em ambientes concorrentes, duas threads executam o `SELECT` antes que qualquer uma tenha feito o `INSERT`, ambas concluem que o registro não existe e ambas tentam inserir, gerando duplicidade caso a chave única não exista, ou erro 500 caso a chave única lance exceção não tratada.

## Decisão
Adotou-se uma estratégia multicamadas de **idempotência estrita e tratamento de concorrência a nível de banco de dados**:

1. **Restrição Única no Banco de Dados:**
   - Criação de uma restrição de unicidade física (`CONSTRAINT uk_activation_request_external_id UNIQUE (external_id)`), indexada para busca rápida.
   - O banco de dados relacional atua como a barreira definitiva e atômica contra inserções duplicadas.

2. **Isolamento de Transação na Inserção (ActivationPersister):**
   - A operação de inserção e flush no banco é isolada no componente `ActivationPersister` utilizando `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - Se ocorrer colisão concorrente de chave única, a exceção `DataIntegrityViolationException` afeta estritamente a microtransação isolada, impedindo que a transação principal da requisição seja marcada silenciosamente como `rollback-only` (evitando o infame `UnexpectedRollbackException` do Spring).

3. **Semântica da Resposta e Análise de Divergência de Payload:**
   - **Caso 1: Mesma chave e mesmo payload (Retry Legítimo):**
     - Ocorre quando a requisição é um reenvio idêntico (`customerId` e `productCode` iguais).
     - **Resposta:** HTTP `200 OK` retornando o registro já existente e seu status atual. O reprocessamento não é duplicado, respeitando a semântica de idempotência.
   - **Caso 2: Mesma chave com payload divergente (Conflito de Negócio):**
     - Ocorre quando um sistema tenta reutilizar o mesmo `externalId` para outro cliente ou outro produto.
     - **Resposta:** HTTP `409 Conflict` (ou `422 Unprocessable Entity`), com detalhamento no padrão RFC 9457 indicando que a chave já pertence a outra solicitação com dados diferentes.
   - **Caso 3: Duas requisições idênticas concorrentes no mesmo milissegundo:**
     - A primeira thread efetua o commit e retorna `201 Created` com o header `Location`.
     - A segunda thread intercepta a colisão na constraint UNIQUE, captura o registro recém-gravado pela primeira thread, valida a equivalência de payload e responde com `200 OK` (ou 201), garantindo 0 falhas e exatamente 1 registro gravado.

## Consequências

### Positivas:
- **Zero duplicidade física:** Impossibilidade matemática e física de criar dois registros com o mesmo `externalId`.
- **Experiência transparente para clientes:** Retries de rede recebem o status existente de forma limpa sem erros 500.
- **Proteção contra anomalias de negócio:** Reutilização incorreta de IDs com produtos diferentes é sumariamente bloqueada com mensagem clara.
