package com.wallan.activation.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do OpenAPI 3.0 / Swagger UI para documentação interativa da API.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Product Activation Service API",
                version = "v1.0.0",
                description = "Serviço de ativação de produtos de clientes com persistência resiliente, garantia de idempotência e integração com provedor externo.",
                contact = @Contact(
                        name = "Wallan Peixoto",
                        email = "bobwallan2@gmail.com"
                )
        )
)
@SecurityScheme(
        name = "OAuth2",
        type = SecuritySchemeType.OAUTH2,
        description = "Autenticação OAuth 2.0 Client Credentials com tokens JWT",
        flows = @OAuthFlows(
                clientCredentials = @OAuthFlow(
                        tokenUrl = "https://auth.internal.example.com/oauth2/token",
                        scopes = {
                                @OAuthScope(name = "activation:write", description = "Permissão para criar solicitações de ativação"),
                                @OAuthScope(name = "activation:read", description = "Permissão para consultar solicitações de ativação")
                        }
                )
        )
)
public class OpenApiConfig {
}
