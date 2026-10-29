package pe.pucp.paqrap.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba de punta a punta de la API: configuracion, archivos, corrida dia a dia con un pedido manual, cierre. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paqrap-test;DB_CLOSE_DELAY=-1",
        "paqrap.datos-externos-dir=./no-existe"
})
@AutoConfigureMockMvc
class ApiIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void flujoCompleto() throws Exception {
        mvc.perform(get("/api/salud")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"));
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.flota.length()").value(3))
                .andExpect(jsonPath("$.almacenes.length()").value(3));
        mvc.perform(get("/api/datos/archivos").param("escenario", "SIMULACION_5D").param("fechaInicio", "2026-09-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valido").value(true));
        mvc.perform(get("/api/simulacion/estado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCorrida").value("SIN_CORRIDA"))
                .andExpect(jsonPath("$.vehiculos.length()").value(37));

        // archivo de bloqueos personalizado no valido: se rechaza
        mvc.perform(multipart("/api/datos/archivos/bloqueos").file(new MockMultipartFile("archivo", "bloqueo.2609.txt",
                        "text/plain", "linea mala\n".getBytes())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valido").value(false));

        // corrida dia a dia (tiempo real) con un pedido registrado a mano
        mvc.perform(post("/api/simulacion/iniciar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"escenario\":\"TIEMPO_REAL\",\"algoritmo\":\"ACO\",\"fechaInicio\":\"2026-10-10T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escenario").value("TIEMPO_REAL"));
        mvc.perform(post("/api/simulacion/iniciar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cliente\":\"c9999\",\"x\":30,\"y\":20,\"cantidad\":5,\"tipoEntrega\":\"PRIORIZADA_4H\",\"canal\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PW-1"));
        Thread.sleep(600);
        mvc.perform(get("/api/simulacion/pedidos/PW-1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiculos.length()").value(1));
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":300,\"y\":20,\"cantidad\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/simulacion/bloqueos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x1\":10,\"y1\":10,\"x2\":11,\"y2\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bloqueado").value(true));
        mvc.perform(post("/api/simulacion/averias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculo\":\"TB12\",\"tipo\":2}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/simulacion/vehiculos/TA01")).andExpect(status().isOk());
        mvc.perform(get("/api/simulacion/almacenes/ESTE")).andExpect(status().isOk());
        mvc.perform(get("/api/pedidos").param("q", "PW")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/pedidos/exportar")).andExpect(status().isOk());
        mvc.perform(get("/api/simulacion/estado")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCorrida").value("EN_CURSO"))
                .andExpect(jsonPath("$.tramosManuales.length()").value(1));

        mvc.perform(post("/api/simulacion/detener")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DETENIDA"))
                .andExpect(jsonPath("$.resumen.pedidosRegistrados").isNumber());
        mvc.perform(get("/api/simulacion/cierre/csv")).andExpect(status().isOk());
        Thread.sleep(2500);
        mvc.perform(get("/api/corridas")).andExpect(status().isOk()).andExpect(jsonPath("$[0].tieneCierre").value(true));
    }
}
