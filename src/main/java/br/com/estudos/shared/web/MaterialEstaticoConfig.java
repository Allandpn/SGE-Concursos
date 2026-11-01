package br.com.estudos.shared.web;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Os PDFs do material de leitura, servidos pelo próprio SGE (ADR-044,
 * docs/SPRINT-19-MATERIAL-AUTOMATICO.md §6). O planejamento escreve em
 * `MATERIAL_DIR`; aqui é só leitura. O resource handler do Spring não lista
 * diretório e não resolve caminho que sai da pasta (`..`).
 */
@Configuration
public class MaterialEstaticoConfig implements WebMvcConfigurer {

    private final String diretorio;
    private final String editais;

    public MaterialEstaticoConfig(@Value("${estudos.material.dir:/dados/material}") String diretorio,
                                  @Value("${estudos.editais.dir:/dados/editais}") String editais) {
        this.diretorio = diretorio;
        this.editais = editais;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Path.toUri() só termina em "/" se o diretório já existir na subida; sem a
        // barra, o Spring trataria a pasta como arquivo e nada seria encontrado.
        // ADR-054: os blocos dos PDFs próprios ficam no volume editais (o único que o app escreve), servidos aqui.
        // Registrado antes de /material/**: o padrão mais específico responde.
        var proprios = Path.of(editais, "proprios").toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/material/proprios/**")
            .addResourceLocations(proprios.endsWith("/") ? proprios : proprios + "/");
        var local = Path.of(diretorio).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/material/**")
            .addResourceLocations(local.endsWith("/") ? local : local + "/");
    }
}
