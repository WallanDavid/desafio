package com.wallan.activation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do serviço de ativação de produtos de clientes.
 */
@SpringBootApplication
public class ProductActivationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductActivationServiceApplication.class, args);
    }
}
