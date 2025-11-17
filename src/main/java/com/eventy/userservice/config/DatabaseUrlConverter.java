package com.eventy.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DatabaseUrlConverter {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        if (databaseUrl.startsWith("postgres://")) {
            // Extraction des infos
            String jdbcUrl = convertToJdbc(databaseUrl);
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(extractUsername(databaseUrl));
            ds.setPassword(extractPassword(databaseUrl));
            return ds;
        }
        // Cas normal
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(databaseUrl);
        return ds;
    }

    private String convertToJdbc(String url) {
        // Format attendu: postgres://user:pass@host:port/db
        String tmp = url.replace("postgres://", "");
        String[] userInfoAndHost = tmp.split("@");
        String[] userInfo = userInfoAndHost[0].split(":");
        String[] hostAndDb = userInfoAndHost[1].split("/", 2);
        String[] hostAndPort = hostAndDb[0].split(":");
        return "jdbc:postgresql://" + hostAndPort[0] + ":" + hostAndPort[1] + "/" + hostAndDb[1];
    }

    private String extractUsername(String url) {
        String tmp = url.replace("postgres://", "");
        String[] userInfoAndHost = tmp.split("@");
        String[] userInfo = userInfoAndHost[0].split(":");
        return userInfo[0];
    }

    private String extractPassword(String url) {
        String tmp = url.replace("postgres://", "");
        String[] userInfoAndHost = tmp.split("@");
        String[] userInfo = userInfoAndHost[0].split(":");
        return userInfo[1];
    }
}