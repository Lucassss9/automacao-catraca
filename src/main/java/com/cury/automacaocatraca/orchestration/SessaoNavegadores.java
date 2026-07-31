package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.cfobras.page.CfObrasAuthenticator;
import com.cury.automacaocatraca.config.ObrasRegistry;
import com.cury.automacaocatraca.config.WebDriverFactory;
import com.cury.automacaocatraca.trc.TrcAuthenticator;
import com.cury.automacaocatraca.trc.TrcEmployeeExtractor;
import com.cury.automacaocatraca.trc.TrcFuncionarioExtractor;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SessaoNavegadores {

    private static final Logger log = LoggerFactory.getLogger(SessaoNavegadores.class);

    private static final int TENTATIVAS_DE_LOGIN = 3;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 10000;

    private final ObrasRegistry obrasRegistry;
    private final WebDriverFactory webDriverFactory;
    private final TrcAuthenticator trcAuth;
    private final CfObrasAuthenticator cfAuth;
    private final TrcEmployeeExtractor trcEmployeeExtractor;
    private final TrcFuncionarioExtractor trcFuncionarioExtractor;

    private WebDriver trc;
    private WebDriver cfObras;

    public SessaoNavegadores(ObrasRegistry obrasRegistry,
                             WebDriverFactory webDriverFactory,
                             TrcAuthenticator trcAuth,
                             CfObrasAuthenticator cfAuth,
                             TrcEmployeeExtractor trcEmployeeExtractor,
                             TrcFuncionarioExtractor trcFuncionarioExtractor) {
        this.obrasRegistry = obrasRegistry;
        this.webDriverFactory = webDriverFactory;
        this.trcAuth = trcAuth;
        this.cfAuth = cfAuth;
        this.trcEmployeeExtractor = trcEmployeeExtractor;
        this.trcFuncionarioExtractor = trcFuncionarioExtractor;
    }

    public void novaObra(String codigoObra) {
        trcEmployeeExtractor.limparCache();
        trcFuncionarioExtractor.limparCache();

        log.info("Caches do TRC limpos para a obra {} — as listagens do TRC sao por obra logada", codigoObra);
    }

    public WebDriver trc() {
        if (trc != null && trcAuth.sessaoAtiva(trc)) {
            log.info("Reaproveitando a sessao do TRC ja aberta");
            return trc;
        }

        if (trc != null) {
            log.info("A sessao do TRC caiu — abrindo um navegador novo");
            descartarTrc();
        }

        trc = webDriverFactory.criar(obrasRegistry.pastaDownload());
        entrar("TRC", () -> trcAuth.login(trc));

        return trc;
    }

    public WebDriver cfObras() {
        if (cfObras != null && cfAuth.sessaoAtiva(cfObras)) {
            log.info("Reaproveitando a sessao do CF Obras ja aberta");
            return cfObras;
        }

        if (cfObras != null) {
            log.info("A sessao do CF Obras caiu — abrindo um navegador novo");
            descartarCfObras();
        }

        cfObras = webDriverFactory.criar(obrasRegistry.pastaDownload());
        entrar("CF Obras", () -> cfAuth.login(cfObras));

        return cfObras;
    }

    public WebDriver trcAberto() {
        return trc;
    }

    public WebDriver cfObrasAberto() {
        return cfObras;
    }

    public void descartarTudo() {
        descartarCfObras();
        descartarTrc();
    }

    @PreDestroy
    public void fechar() {
        if (trc == null && cfObras == null) {
            return;
        }

        log.info("Fechando os navegadores da rodada");
        descartarTudo();
    }

    private void entrar(String nome, Runnable login) {
        RuntimeException ultimaFalha = null;

        for (int tentativa = 1; tentativa <= TENTATIVAS_DE_LOGIN; tentativa++) {
            try {
                login.run();
                return;

            } catch (RuntimeException e) {
                ultimaFalha = e;

                log.warn("Login no {} falhou na tentativa {} de {}: {}",
                        nome, tentativa, TENTATIVAS_DE_LOGIN, primeiraLinha(e));

                if (tentativa < TENTATIVAS_DE_LOGIN) {
                    esperar(ESPERA_ENTRE_TENTATIVAS_MS);
                }
            }
        }

        throw ultimaFalha;
    }

    private String primeiraLinha(Throwable erro) {
        String mensagem = erro.getMessage();

        if (mensagem == null || mensagem.isBlank()) {
            return erro.getClass().getSimpleName();
        }

        int quebra = mensagem.indexOf('\n');

        return quebra < 0 ? mensagem : mensagem.substring(0, quebra);
    }

    private void descartarTrc() {
        fechar(trc, "TRC");
        trc = null;
    }

    private void descartarCfObras() {
        fechar(cfObras, "CF Obras");
        cfObras = null;
    }

    private void fechar(WebDriver driver, String nome) {
        if (driver == null) {
            return;
        }

        try {
            driver.quit();
            log.info("Navegador do {} fechado", nome);
        } catch (Exception e) {
            log.warn("Falha ao fechar o navegador do {}: {}", nome, e.getMessage());
        }
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}