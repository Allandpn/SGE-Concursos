package br.com.estudos.shared.teste;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Os dois pools e o roteamento (ADR-061): o real e o do schema {@code teste}; o {@code DataSource} que o app usa (JPA, JdbcTemplate,
 * Flyway na subida) entrega o do teste só quando a requisição está marcada pelo {@link ModoTesteFiltro}.
 */
@Configuration
public class ModoTesteConfig {

    public static final String SCHEMA = "teste";

    // As mesmas propriedades spring.datasource.hikari.* que o pool automático do Spring Boot leria (os testes limitam o tamanho).
    @Bean
    @Qualifier("dataSourceReal")
    @org.springframework.boot.context.properties.ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSourceReal(DataSourceProperties propriedades) {
        var ds = propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setPoolName("real");
        return ds;
    }

    @Bean
    @Qualifier("dataSourceTeste")
    public HikariDataSource dataSourceTeste(DataSourceProperties propriedades, @Qualifier("dataSourceReal") HikariDataSource real) {
        var ds = propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setPoolName("teste");
        ds.setMaximumPoolSize(Math.min(4, real.getMaximumPoolSize()));
        ds.setMinimumIdle(0);
        ds.setInitializationFailTimeout(-1); // não abre conexão na subida
        ds.addDataSourceProperty("currentSchema", SCHEMA + ",public");
        return ds;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("dataSourceReal") HikariDataSource real, @Qualifier("dataSourceTeste") HikariDataSource teste) {
        var roteador = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return ModoTeste.ligado() ? "teste" : "real";
            }
        };
        roteador.setTargetDataSources(Map.of("real", real, "teste", teste));
        roteador.setDefaultTargetDataSource(real);
        roteador.afterPropertiesSet();
        return roteador;
    }
}
