package br.com.estudos;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EstudosApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstudosApplication.class, args);
    }

    // docs/SPRINT-5-FRENTE.md §1: primeiro "hoje" do sistema (represamento).
    // Nunca LocalDate.now() solto (09_CODE_STYLE §3.2) — injeta este bean.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
