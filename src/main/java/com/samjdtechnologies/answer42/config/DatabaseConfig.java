package com.samjdtechnologies.answer42.config;

import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.auto-commit}")
    private boolean autoCommit;

    @Value("${spring.datasource.hikari.connection-timeout}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.transaction-isolation}")
    private String transactionIsolation;

    @Value("${spring.jpa.properties.hibernate.default_schema}")
    private String hibernateDefaultSchema;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String hibernateDdlAuto;

    /**
     * Configures and creates a HikariCP configuration with connection properties
     * from application.properties.
     * 
     * @return the configured HikariConfig instance
     */
    @Bean
    public HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setAutoCommit(autoCommit);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setTransactionIsolation(transactionIsolation);
        
        // Add additional data source properties
        config.addDataSourceProperty("ApplicationName", "answer42");
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        config.addDataSourceProperty("sslmode", "disable");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "true");

        
        return config;
    }
    
    /**
     * Creates the primary DataSource bean using HikariCP connection pool.
     * 
     * @return a DataSource configured with HikariCP properties
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        return new HikariDataSource(hikariConfig());
    }
    
    /**
     * Creates the EntityManagerFactory with Hibernate as the JPA provider.
     * Configures entity scanning, database connection, and Hibernate properties.
     * 
     * @return a LocalContainerEntityManagerFactoryBean for JPA entity management
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(true);
        
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("com.samjdtechnologies.answer42.model");
        factory.setDataSource(dataSource());
        
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.default_schema", hibernateDefaultSchema);
        jpaProperties.put("hibernate.hbm2ddl.auto", hibernateDdlAuto);
        jpaProperties.put("hibernate.format_sql", "false");
        jpaProperties.put("hibernate.jdbc.lob.non_contextual_creation", "true");
        jpaProperties.put("hibernate.jdbc.time_zone", "UTC");
        
        // Add connection information explicitly for Hibernate
        jpaProperties.put("hibernate.connection.driver_class", driverClassName);
        jpaProperties.put("hibernate.connection.url", jdbcUrl);
        jpaProperties.put("hibernate.connection.username", username);
        jpaProperties.put("hibernate.connection.password", password);
        jpaProperties.put("hibernate.connection.autocommit", String.valueOf(autoCommit));
        jpaProperties.put("hibernate.connection.isolation", transactionIsolation);
        
        factory.setJpaProperties(jpaProperties);
        
        return factory;
    }
    
    /**
     * Creates a JPA transaction manager linked to the entity manager factory.
     * 
     * @param entityManagerFactory the EntityManagerFactory to manage transactions for
     * @return a PlatformTransactionManager implementation for JPA
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory);
        return txManager;
    }
}
