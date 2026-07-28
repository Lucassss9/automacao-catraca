package com.cury.automacaocatraca.cfobras.page;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class VinculoObraPage {

    private static final Logger log = LoggerFactory.getLogger(VinculoObraPage.class);

    private static final By PAINEL = By.id("painelVinculoLote");
    private static final By MATRIZ = By.id("matrizVinculoLote");
    private static final By BOTAO_SALVAR = By.id("btnSalvarVinculoLote");
    private static final By INFO = By.id("infoVinculoLote");

    public enum Resultado {
        JA_VINCULADA,
        VINCULADA_AGORA,
        VISIVEL_EM_TODAS,
        NAO_ENCONTRADA,
        PAINEL_INDISPONIVEL,
        FALHA
    }

    public record Situacao(
            boolean encontrada,
            String idCfObras,
            String fantasia,
            String razaoSocial,
            int totalObrasMarcadas,
            boolean marcadaNestaObra,
            boolean obraExisteNaMatriz,
            String obraIdCfObras
    ) {}

    public record LinhaMatriz(
            String idCfObras,
            String fantasia,
            String razaoSocial,
            List<String> obras
    ) {}

    private static final String NORMALIZADOR_JS =
            "function norm(t){ return (t||'').replace(/\\u00a0/g,' ').normalize('NFD')"
                    + "  .replace(/[\\u0300-\\u036f]/g,'')"
                    + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }";

    private final CfObrasNavigator navigator;

    public VinculoObraPage(CfObrasNavigator navigator) {
        this.navigator = navigator;
    }

    public void abrir(WebDriver driver) {
        navigator.abrirAba(driver, "empreiteiros");
        esperar(1200);
    }

    public boolean painelDisponivel(WebDriver driver) {
        return !driver.findElements(PAINEL).isEmpty()
                && !driver.findElements(MATRIZ).isEmpty()
                && !driver.findElements(By.cssSelector("#matrizVinculoLote tbody tr[data-emp]")).isEmpty();
    }

    public Situacao consultar(WebDriver driver, String nomeEmpreiteira, String nomeObra) {
        Object bruto = executar(driver, SCRIPT_CONSULTA, nomeEmpreiteira, nomeObra);

        if (!(bruto instanceof Map<?, ?> mapa)) {
            return new Situacao(false, null, null, null, 0, false, false, null);
        }

        return new Situacao(
                booleano(mapa.get("encontrada")),
                texto(mapa.get("idEmp")),
                texto(mapa.get("fantasia")),
                texto(mapa.get("razao")),
                inteiro(mapa.get("totalMarcadas")),
                booleano(mapa.get("marcadaNestaObra")),
                booleano(mapa.get("obraExiste")),
                texto(mapa.get("obraId"))
        );
    }

    public Resultado garantirVinculo(WebDriver driver, String nomeEmpreiteira, String nomeObra) {
        if (!painelDisponivel(driver)) {
            log.warn("Painel de vinculo em lote nao esta disponivel nesta sessao");
            return Resultado.PAINEL_INDISPONIVEL;
        }

        Situacao situacao = consultar(driver, nomeEmpreiteira, nomeObra);

        if (!situacao.obraExisteNaMatriz()) {
            log.warn("Obra '{}' nao existe como coluna na matriz de vinculos", nomeObra);
            return Resultado.FALHA;
        }

        if (!situacao.encontrada()) {
            log.warn("'{}' nao encontrada na matriz de vinculos", nomeEmpreiteira);
            return Resultado.NAO_ENCONTRADA;
        }

        if (situacao.marcadaNestaObra()) {
            log.info("'{}' ja vinculada a obra '{}'", nomeEmpreiteira, nomeObra);
            return Resultado.JA_VINCULADA;
        }

        if (situacao.totalObrasMarcadas() == 0) {
            log.info("'{}' esta sem nenhuma obra marcada — ja e visivel em todas, nao vou mexer",
                    nomeEmpreiteira);
            return Resultado.VISIVEL_EM_TODAS;
        }

        log.info("Marcando '{}' na obra '{}' (hoje em {} obra(s))",
                nomeEmpreiteira, nomeObra, situacao.totalObrasMarcadas());

        Object marcou = executar(driver, SCRIPT_MARCAR, situacao.idCfObras(), nomeObra);

        if (!Boolean.TRUE.equals(marcou)) {
            log.warn("Nao consegui marcar a caixa de '{}' x '{}'", nomeEmpreiteira, nomeObra);
            return Resultado.FALHA;
        }

        return salvar(driver, nomeEmpreiteira) ? Resultado.VINCULADA_AGORA : Resultado.FALHA;
    }

    private boolean salvar(WebDriver driver, String nomeEmpreiteira) {
        try {
            WebElement botao = new WebDriverWait(driver, Duration.ofSeconds(15))
                    .until(ExpectedConditions.elementToBeClickable(BOTAO_SALVAR));

            executar(driver, "arguments[0].scrollIntoView({block:'center'});", botao);
            esperar(300);

            try {
                botao.click();
            } catch (Exception e) {
                executar(driver, "arguments[0].click();", botao);
            }

            esperar(2000);

            String info = driver.findElements(INFO).isEmpty()
                    ? ""
                    : driver.findElements(INFO).get(0).getText();

            log.info("Vinculo de '{}' salvo — painel diz: '{}'", nomeEmpreiteira, info);
            return true;

        } catch (Exception e) {
            log.error("Falha ao salvar o vinculo de '{}': {}", nomeEmpreiteira, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<LinhaMatriz> lerMatrizCompleta(WebDriver driver) {
        List<LinhaMatriz> saida = new ArrayList<>();
        Object bruto = executar(driver, SCRIPT_MATRIZ);

        if (!(bruto instanceof List<?> lista)) {
            return saida;
        }

        for (Object item : (List<Object>) lista) {
            if (!(item instanceof Map<?, ?> mapa)) {
                continue;
            }

            List<String> obras = new ArrayList<>();
            Object marcadas = mapa.get("obras");

            if (marcadas instanceof List<?> nomes) {
                for (Object nome : nomes) {
                    obras.add(String.valueOf(nome));
                }
            }

            saida.add(new LinhaMatriz(
                    texto(mapa.get("idEmp")),
                    texto(mapa.get("fantasia")),
                    texto(mapa.get("razao")),
                    obras));
        }

        log.info("Matriz de vinculos lida por completo: {} empreiteiros", saida.size());

        return saida;
    }

    private static final String SCRIPT_MATRIZ =
            "var tabela = document.querySelector('#matrizVinculoLote table');"
                    + "if (!tabela) { return []; }"
                    + "var ths = tabela.querySelectorAll('thead th.vinc-col-obra');"
                    + "var titulos = [];"
                    + "for (var i = 0; i < ths.length; i++) {"
                    + "  titulos.push(((ths[i].getAttribute('title') || ths[i].textContent) || '')"
                    + "    .replace(/\\s+/g,' ').trim());"
                    + "}"
                    + "var linhas = tabela.querySelectorAll('tbody tr[data-emp]');"
                    + "var saida = [];"
                    + "for (var i = 0; i < linhas.length; i++) {"
                    + "  var caixas = linhas[i].querySelectorAll('input.vinc-chk');"
                    + "  var obras = [];"
                    + "  for (var j = 0; j < caixas.length; j++) {"
                    + "    if (caixas[j].checked && titulos[j]) { obras.push(titulos[j]); }"
                    + "  }"
                    + "  var nomeEl = linhas[i].querySelector('.vinc-nome');"
                    + "  var subEl = linhas[i].querySelector('.vinc-sub');"
                    + "  saida.push({"
                    + "    idEmp: linhas[i].getAttribute('data-emp'),"
                    + "    fantasia: nomeEl ? nomeEl.textContent.trim() : '',"
                    + "    razao: subEl ? subEl.textContent.trim() : '',"
                    + "    obras: obras"
                    + "  });"
                    + "}"
                    + "return saida;";

    private static final String SCRIPT_CONSULTA =
            NORMALIZADOR_JS
                    + "var alvo = norm(arguments[0]);"
                    + "var obraAlvo = norm(arguments[1]);"
                    + "var tabela = document.querySelector('#matrizVinculoLote table');"
                    + "if (!tabela) { return {encontrada:false, obraExiste:false}; }"
                    + "var ths = tabela.querySelectorAll('thead th.vinc-col-obra');"
                    + "var colObra = -1;"
                    + "for (var i = 0; i < ths.length; i++) {"
                    + "  var t = norm(ths[i].getAttribute('title') || ths[i].textContent);"
                    + "  if (t === obraAlvo || t.indexOf(obraAlvo) >= 0 || obraAlvo.indexOf(t) >= 0) {"
                    + "    colObra = i; break;"
                    + "  }"
                    + "}"
                    + "if (colObra < 0) { return {encontrada:false, obraExiste:false}; }"
                    + "var linhas = tabela.querySelectorAll('tbody tr[data-emp]');"
                    + "var exata = null, parcial = null;"
                    + "for (var i = 0; i < linhas.length; i++) {"
                    + "  var nomeEl = linhas[i].querySelector('.vinc-nome');"
                    + "  var subEl = linhas[i].querySelector('.vinc-sub');"
                    + "  var fantasia = nomeEl ? norm(nomeEl.textContent) : '';"
                    + "  var razao = subEl ? norm(subEl.textContent) : '';"
                    + "  if (fantasia === alvo || razao === alvo) { exata = linhas[i]; break; }"
                    + "  if (parcial === null && alvo.length > 4) {"
                    + "    if ((fantasia.length > 3 && (fantasia.indexOf(alvo) >= 0 || alvo.indexOf(fantasia) >= 0))"
                    + "        || (razao.length > 3 && (razao.indexOf(alvo) >= 0 || alvo.indexOf(razao) >= 0))) {"
                    + "      parcial = linhas[i];"
                    + "    }"
                    + "  }"
                    + "}"
                    + "var linha = exata || parcial;"
                    + "if (!linha) { return {encontrada:false, obraExiste:true}; }"
                    + "var caixas = linha.querySelectorAll('input.vinc-chk');"
                    + "var total = 0;"
                    + "for (var j = 0; j < caixas.length; j++) { if (caixas[j].checked) { total++; } }"
                    + "var alvoCaixa = caixas[colObra];"
                    + "var nomeEl2 = linha.querySelector('.vinc-nome');"
                    + "var subEl2 = linha.querySelector('.vinc-sub');"
                    + "return {"
                    + "  encontrada: true,"
                    + "  obraExiste: true,"
                    + "  idEmp: linha.getAttribute('data-emp'),"
                    + "  fantasia: nomeEl2 ? nomeEl2.textContent.trim() : '',"
                    + "  razao: subEl2 ? subEl2.textContent.trim() : '',"
                    + "  totalMarcadas: total,"
                    + "  marcadaNestaObra: alvoCaixa ? !!alvoCaixa.checked : false,"
                    + "  obraId: alvoCaixa ? (alvoCaixa.getAttribute('data-obra') || '') : ''"
                    + "};";

    private static final String SCRIPT_MARCAR =
            NORMALIZADOR_JS
                    + "var idEmp = arguments[0];"
                    + "var obraAlvo = norm(arguments[1]);"
                    + "var tabela = document.querySelector('#matrizVinculoLote table');"
                    + "if (!tabela) { return false; }"
                    + "var ths = tabela.querySelectorAll('thead th.vinc-col-obra');"
                    + "var colObra = -1;"
                    + "for (var i = 0; i < ths.length; i++) {"
                    + "  var t = norm(ths[i].getAttribute('title') || ths[i].textContent);"
                    + "  if (t === obraAlvo || t.indexOf(obraAlvo) >= 0 || obraAlvo.indexOf(t) >= 0) {"
                    + "    colObra = i; break;"
                    + "  }"
                    + "}"
                    + "if (colObra < 0) { return false; }"
                    + "var linha = tabela.querySelector(\"tbody tr[data-emp='\" + idEmp + \"']\");"
                    + "if (!linha) { return false; }"
                    + "var caixa = linha.querySelectorAll('input.vinc-chk')[colObra];"
                    + "if (!caixa || caixa.checked) { return false; }"
                    + "caixa.scrollIntoView({block:'center'});"
                    + "caixa.checked = true;"
                    + "caixa.dispatchEvent(new Event('input', {bubbles:true}));"
                    + "caixa.dispatchEvent(new Event('change', {bubbles:true}));"
                    + "return true;";

    private Object executar(WebDriver driver, String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private boolean booleano(Object valor) {
        return Boolean.TRUE.equals(valor);
    }

    private int inteiro(Object valor) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        return 0;
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}