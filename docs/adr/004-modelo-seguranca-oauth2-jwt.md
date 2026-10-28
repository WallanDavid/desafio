# ADR 004: Modelo de Segurança com OAuth 2.0 Resource Server e JWT

## Status
**Aprovado**

## Contexto
O serviço de ativação de produtos é consumido exclusivamente por outros sistemas de backend (comunicação *Machine-to-Machine* - M2M), tais como portais de e-commerce, sistemas legados de CRM e orquestradores de pedidos.

Em ambiente corporativo de nuvem:
1. Sistemas consumidores não utilizam usuário e senha interativos.
2. É mandatório adotar autenticação baseada em padrões abertos da indústria (OAuth 2.0 / OpenID Connect).
3. Deve haver segregação de privilégios (Least Privilege): sistemas que apenas consultam relatórios não devem ter permissão de disparar ativações.
4. Respostas de falhas de autenticação e autorização devem seguir o padrão RFC 9457 com correlação de logs.

## Decisão

### 1. Fluxo OAuth 2.0 Client Credentials
Adotou-se o fluxo **OAuth 2.0 Client Credentials**:
- O sistema chamador autentica-se diretamente contra o Provedor de Identidade corporativo (ex: Keycloak, Okta, AWS Cognito, Auth0) fornecendo `client_id` e `client_secret`.
- O IdP emite um token de acesso em formato **JWT (JSON Web Token)** assinado assimetricamente (algoritmo RS256/ES256).
- A nossa API atua puramente como **OAuth 2.0 Resource Server**, validando a assinatura do token de forma stateless através da chave pública obtida via endpoint JWKS (`/.well-known/jwks.json`).

### 2. Validações Realizadas no JWT
A aplicação valida rigorosamente:
- **Assinatura criptográfica:** Verificada via chaves públicas do JWKS (com cache local e rotação automática).
- **Emissor (`iss`):** Deve coincidir com o Identity Provider confiável.
- **Audiência (`aud`):** Deve indicar que o token foi emitido especificamente para o serviço `product-activation-service`.
- **Expiração (`exp` e `nbf`):** Rejeição imediata de tokens expirados (com tolerância máxima de clock skew de 60s).

### 3. Autorização Granular por Escopos (Scopes)
Os endpoints da API exigem autoridades específicas mapeadas a partir da claim `scope`:
- `POST /api/v1/requests` -> Exige `SCOPE_activation:write`.
- `GET /api/v1/requests/**` -> Exige `SCOPE_activation:read`.
- Endpoints de monitoramento (`/actuator/health`, `/swagger-ui/**`) são abertos para probes de orquestradores (Kubernetes).

### 4. Armazenamento e Rotação de Credenciais do Provedor Externo
Para a credencial necessária para chamar o provedor parceiro:
- As chaves de API / segredos de autenticação são injetados exclusivamente via **variáveis de ambiente** ou recuperados dinamicamente via serviço de gerenciamento de segredos em nuvem (**AWS Secrets Manager**, **HashiCorp Vault** ou **GCP Secret Manager**).
- Proibição absoluta de chaves hardcoded no código ou commits de repositório.
- A rotação de chaves é transparente sem necessidade de recompilação do código.

### 5. Isolamento e Prevenção contra BOLA (Broken Object Level Authorization)
- Em ambiente multi-tenant ou com múltiplos canais parceiros, o token JWT carrega a claim do consumidor (`client_id` ou `tenant_id`).
- Nas consultas de solicitações, a aplicação pode restringir a visibilidade do `customerId` garantindo que um parceiro nunca visualize dados de clientes pertencentes a outro canal.

## Consequências

### Positivas:
- **Arquitetura 100% Stateless:** Nenhuma sessão HTTP ou estado em memória no servidor, permitindo escala horizontal infinita de pods.
- **Padrão corporativo:** Alinhamento completo com arquiteturas modernas de microsserviços em cloud.
- **Mensagens de erro padronizadas:** 401 e 403 serializados em `application/problem+json` com rastreabilidade via `traceId`.
