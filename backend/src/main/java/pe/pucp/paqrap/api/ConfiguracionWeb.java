package pe.pucp.paqrap.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS de la API: el frontend desplegado en Vercel (u otro dominio) puede
 * llamar directamente a la API. Los origenes permitidos se configuran con
 * PAQRAP_CORS_ORIGENES (lista separada por comas; "*" por defecto).
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final String[] origenes;

    public ConfiguracionWeb(@Value("${paqrap.cors-origenes:*}") String origenes) {
        this.origenes = origenes.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origenes)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
