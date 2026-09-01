package org.horodenko.calendar.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serve o manifesto do aplicativo web com o tipo que ele exige.
 *
 * <p>Entregue como recurso estatico comum, ele sairia como
 * {@code application/octet-stream}: o servidor deduz o tipo pela extensao, e
 * {@code .webmanifest} nao esta na tabela padrao. Registrar a extensao na negociacao
 * de conteudo nao resolve -- aquele caminho nao passa por ela. Dai o endpoint proprio.
 *
 * <p>Sem o tipo certo o Edge pode descartar o manifesto ao instalar o site como
 * aplicativo, e e justamente dele que vem o nome e o icone da janela.
 */
@RestController
public class ManifestController {

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public Resource manifest() {
        return new ClassPathResource("static/manifest.webmanifest");
    }
}
