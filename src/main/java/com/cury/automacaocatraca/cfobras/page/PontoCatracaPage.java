package com.cury.automacaocatraca.cfobras.page;

import com.cury.automacaocatraca.cfobras.dto.EmpreiteiraMapeada;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PontoCatracaPage {

    private static final Logger log = LoggerFactory.getLogger(PontoCatracaPage.class);

    private static final By INPUT_ARQUIVO = By.id("inpArquivoPontoCatraca");
    private static final By PASSO_2 = By.id("passo2PontoCatraca");
    private static final By MAPEAMENTO = By.id("mapeamentoEmpreiteirasCatraca");
    private static final By CHK_CRIAR_FUNCIONARIOS = By.id("chkCriarFuncionariosCatraca");
    private static final By PASSO_3 = By.id("passo3PontoCatraca");
    private static final By BOTAO_IMPORTAR = By.id("btnImportarPontoCatraca");
    private static final By STATUS = By.id("statusPontoCatraca");
    private static final By RESUMO_NOVOS = By.id("resumoNovosCatraca");
    private static final By RESUMO_DUPLICADOS = By.id("resumoDuplicadosCatraca");
    private static final By RESUMO_FUNCIONARIOS = By.id("resumoFuncionariosCatraca");

    private final CfObrasNavigator navigator;

    public PontoCatracaPage(CfObrasNavigator navigator) {
        this.navigator = navigator;
    }

    public void abrir(WebDriver driver) {
        navigator.abrirAba(driver, "ponto-catraca");
    }

    public void selecionarObra(WebDriver driver, String nomeObraCfObras) {
        String alvo = NormalizadorNome.normalizar(nomeObraCfObras);

        Boolean achou = (Boolean) executar(driver,
                "var alvo = arguments[0];"
                        + "var sel = document.getElementById('selObraPontoCatraca');"
                        + "if (!sel) { return false; }"
                        + "for (var i = 0; i < sel.options.length; i++) {"
                        + "  var t = sel.options[i].textContent"
                        + "      .normalize('NFD').replace(/[\\u0300-\\u036f]/g, '')"
                        + "      .toUpperCase().replace(/\\s+/g, ' ').trim();"
                        + "  if (t === alvo) {"
                        + "    sel.selectedIndex = i;"
                        + "    sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "    return true;"
                        + "  }"
                        + "}"
                        + "return false;",
                alvo);

        if (!Boolean.TRUE.equals(achou)) {
            throw new RuntimeException(
                    "Obra '" + nomeObraCfObras + "' nao encontrada no seletor do Ponto Catraca.");
        }

        log.info("Obra selecionada no CF Obras: {}", nomeObraCfObras);
    }

    public void enviarArquivo(WebDriver driver, Path arquivo) {
        WebElement input = driver.findElement(INPUT_ARQUIVO);

        executar(driver,
                "arguments[0].style.display = 'block';"
                        + "arguments[0].style.visibility = 'visible';"
                        + "arguments[0].style.width = '1px';"
                        + "arguments[0].style.height = '1px';",
                input);

        input.sendKeys(arquivo.toAbsolutePath().toString());
        log.info("Arquivo enviado: {}", arquivo.getFileName());

        try {
            new WebDriverWait(driver, Duration.ofSeconds(45))
                    .until(ExpectedConditions.visibilityOfElementLocated(PASSO_2));

        } catch (Exception e) {
            String status = textoDe(driver, STATUS);

            throw new IllegalStateException(
                    "O mapeamento (passo 2) nao abriu depois de enviar '"
                            + arquivo.getFileName() + "'. A causa mais comum e planilha"
                            + " sem registros. Status na tela: '"
                            + (status.isBlank() ? "(vazio)" : status) + "'", e);
        }

        esperar(1500);
        log.info("Passo 2 (mapeamento) carregado");
    }

    @SuppressWarnings("unchecked")
    public List<EmpreiteiraMapeada> lerMapeamento(WebDriver driver) {
        Object resultado = executar(driver,
                "var linhas = document.querySelectorAll('#mapeamentoEmpreiteirasCatraca tbody tr');"
                        + "var saida = [];"
                        + "for (var i = 0; i < linhas.length; i++) {"
                        + "  var tr = linhas[i];"
                        + "  var sel = tr.querySelector('select.sel-map-catraca');"
                        + "  var badge = tr.querySelector('.situacao-catraca');"
                        + "  var opt = (sel && sel.selectedIndex >= 0) ? sel.options[sel.selectedIndex] : null;"
                        + "  saida.push({"
                        + "    nomeArquivo: tr.cells[0] ? tr.cells[0].textContent.trim() : '',"
                        + "    norm: tr.getAttribute('data-norm') || '',"
                        + "    id: (sel && sel.value) ? sel.value : '',"
                        + "    nomeSel: (opt && sel.value) ? opt.textContent.trim() : '',"
                        + "    situacao: badge ? badge.textContent.trim() : ''"
                        + "  });"
                        + "}"
                        + "return saida;");

        List<Map<String, Object>> linhas = (List<Map<String, Object>>) resultado;
        List<EmpreiteiraMapeada> mapeadas = new ArrayList<>();

        for (Map<String, Object> linha : linhas) {
            mapeadas.add(new EmpreiteiraMapeada(
                    texto(linha.get("nomeArquivo")),
                    texto(linha.get("norm")),
                    texto(linha.get("id")),
                    texto(linha.get("nomeSel")),
                    texto(linha.get("situacao"))));
        }

        log.info("Mapeamento lido: {} empreiteiras no arquivo", mapeadas.size());
        return mapeadas;
    }

    public boolean definirMapeamento(WebDriver driver, String nomeNormalizado, String idEmpreiteiro) {
        Boolean ok = (Boolean) executar(driver,
                "var norm = arguments[0];"
                        + "var id = arguments[1];"
                        + "var sels = document.querySelectorAll('select.sel-map-catraca');"
                        + "for (var i = 0; i < sels.length; i++) {"
                        + "  if (sels[i].getAttribute('data-norm') !== norm) { continue; }"
                        + "  for (var j = 0; j < sels[i].options.length; j++) {"
                        + "    if (sels[i].options[j].value === id) {"
                        + "      sels[i].selectedIndex = j;"
                        + "      sels[i].dispatchEvent(new Event('change', {bubbles:true}));"
                        + "      return true;"
                        + "    }"
                        + "  }"
                        + "}"
                        + "return false;",
                nomeNormalizado, idEmpreiteiro);

        if (Boolean.TRUE.equals(ok)) {
            log.info("Empreiteira '{}' mapeada para o empreiteiro {}", nomeNormalizado, idEmpreiteiro);
        } else {
            log.warn("Nao consegui mapear a empreiteira '{}'", nomeNormalizado);
        }

        return Boolean.TRUE.equals(ok);
    }

    public String textoDoMapeamento(WebDriver driver) {
        List<WebElement> blocos = driver.findElements(MAPEAMENTO);
        return blocos.isEmpty() ? "(mapeamento nao encontrado)" : blocos.get(0).getText();
    }

    public String htmlDoMapeamento(WebDriver driver) {
        List<WebElement> blocos = driver.findElements(MAPEAMENTO);
        return blocos.isEmpty() ? "(mapeamento nao encontrado)" : blocos.get(0).getDomProperty("outerHTML");
    }

    public void marcarCriarFuncionarios(WebDriver driver) {
        List<WebElement> checkboxes = driver.findElements(CHK_CRIAR_FUNCIONARIOS);

        if (checkboxes.isEmpty()) {
            log.warn("Checkbox de criar funcionarios nao encontrado");
            return;
        }

        WebElement chk = checkboxes.get(0);
        if (!chk.isSelected()) {
            executar(driver, "arguments[0].click();", chk);
            log.info("Marcado 'Criar automaticamente os funcionarios que nao existem'");
        }
    }

    public String resumo(WebDriver driver) {
        if (driver.findElements(PASSO_3).stream().noneMatch(WebElement::isDisplayed)) {
            return "(passo 3 ainda nao visivel)";
        }

        return "novos=" + textoDe(driver, RESUMO_NOVOS)
                + " duplicados=" + textoDe(driver, RESUMO_DUPLICADOS)
                + " funcionarios=" + textoDe(driver, RESUMO_FUNCIONARIOS);
    }

    public boolean importar(WebDriver driver) {
        String historicoAntes = primeiraLinhaDoHistorico(driver);

        WebElement botao = new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.elementToBeClickable(BOTAO_IMPORTAR));

        executar(driver, "arguments[0].scrollIntoView({block:'center'});", botao);

        try {
            botao.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", botao);
        }

        log.info("Importacao acionada — aguardando o historico registrar");

        confirmarModal(driver);

        try {
            new WebDriverWait(driver, Duration.ofMinutes(5))
                    .until(d -> concluiu(d, historicoAntes));

            log.info("Importacao concluida — historico: {}", primeiraLinhaDoHistorico(driver));

            String status = textoDe(driver, STATUS);

            if (!status.isBlank()) {
                log.info("Status na tela: {}", status);
            }

            return true;

        } catch (Exception e) {
            log.error("A importacao nao apareceu no historico em 5 minutos. "
                            + "status='{}' | historico='{}'",
                    textoDe(driver, STATUS), primeiraLinhaDoHistorico(driver));
            return false;
        }
    }

    private boolean concluiu(WebDriver driver, String historicoAntes) {
        String agora = primeiraLinhaDoHistorico(driver);

        if (!agora.isBlank() && !agora.equals(historicoAntes)) {
            return true;
        }

        return !textoDe(driver, STATUS).isBlank();
    }

    public String primeiraLinhaDoHistorico(WebDriver driver) {
        Object resultado = executar(driver,
                "var linha = document.querySelector('#historicoImportacoesCatraca tbody tr');"
                        + "if (!linha) { return ''; }"
                        + "var partes = [];"
                        + "for (var i = 0; i < linha.cells.length; i++) {"
                        + "  partes.push((linha.cells[i].textContent || '').replace(/\\s+/g,' ').trim());"
                        + "}"
                        + "return partes.join(' | ');");

        return resultado == null ? "" : String.valueOf(resultado).trim();
    }

    private void confirmarModal(WebDriver driver) {
        esperar(1200);

        for (String id : new String[] {"cfConfirmOk", "cfPromptOk"}) {
            List<WebElement> botoes = driver.findElements(By.id(id));

            if (botoes.isEmpty() || !botoes.get(0).isDisplayed()) {
                continue;
            }

            log.info("Modal de confirmacao da importacao respondido (#{})", id);

            try {
                botoes.get(0).click();
            } catch (Exception e) {
                executar(driver, "arguments[0].click();", botoes.get(0));
            }

            esperar(800);
            return;
        }
    }

    public int mapearPorNome(WebDriver driver) {
        Object resultado = executar(driver,
                "function norm(t){ return (t||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'')"
                        + "  .toUpperCase().replace(/[^A-Z0-9]/g,''); }"
                        + "var linhas = document.querySelectorAll('#mapeamentoEmpreiteirasCatraca tbody tr');"
                        + "var mapeadas = 0;"
                        + "for (var i = 0; i < linhas.length; i++) {"
                        + "  var sel = linhas[i].querySelector('select.sel-map-catraca');"
                        + "  if (!sel || sel.value) { continue; }"
                        + "  var alvo = norm(linhas[i].cells[0] ? linhas[i].cells[0].textContent : '');"
                        + "  if (!alvo) { continue; }"
                        + "  for (var j = 0; j < sel.options.length; j++) {"
                        + "    if (!sel.options[j].value) { continue; }"
                        + "    var op = norm(sel.options[j].textContent);"
                        + "    if (op === alvo || (op.length > 6 && alvo.length > 6"
                        + "        && (op.indexOf(alvo) >= 0 || alvo.indexOf(op) >= 0))) {"
                        + "      sel.selectedIndex = j;"
                        + "      sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "      mapeadas++;"
                        + "      break;"
                        + "    }"
                        + "  }"
                        + "}"
                        + "return mapeadas;");

        int total = ((Number) resultado).intValue();
        log.info("Mapeamento automatico por nome: {} empreiteiras vinculadas", total);
        return total;
    }

    private String textoDe(WebDriver driver, By seletor) {
        List<WebElement> elementos = driver.findElements(seletor);
        return elementos.isEmpty() ? "" : elementos.get(0).getText().trim();
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private Object executar(WebDriver driver, String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}