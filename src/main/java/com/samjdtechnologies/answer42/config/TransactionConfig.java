package com.samjdtechnologies.answer42.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableTransactionManagement
public class TransactionConfig {

    @Value("${spring.datasource.hikari.transaction-isolation}")
    private String transactionIsolation;

    @Bean
    public TransactionTemplate readOnlyTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(getIsolationLevel(transactionIsolation));
        return template;
    }

    @Bean
    public TransactionTemplate writeTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(false);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(getIsolationLevel(transactionIsolation));
        return template;
    }

    private int getIsolationLevel(String isolationLevel) {
        if (isolationLevel == null) {
            return TransactionDefinition.ISOLATION_DEFAULT;
        }
        
        switch (isolationLevel) {
            case "TRANSACTION_READ_UNCOMMITTED":
                return TransactionDefinition.ISOLATION_READ_UNCOMMITTED;
            case "TRANSACTION_READ_COMMITTED":
                return TransactionDefinition.ISOLATION_READ_COMMITTED;
            case "TRANSACTION_REPEATABLE_READ":
                return TransactionDefinition.ISOLATION_REPEATABLE_READ;
            case "TRANSACTION_SERIALIZABLE":
                return TransactionDefinition.ISOLATION_SERIALIZABLE;
            default:
                return TransactionDefinition.ISOLATION_DEFAULT;
        }
    }
}