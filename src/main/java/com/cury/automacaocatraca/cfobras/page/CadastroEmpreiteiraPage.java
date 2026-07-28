package com.cury.automacaocatraca.cfobras.page;

import com.cury.automacaocatraca.domain.dto.DadosCadastroEmpreiteira;
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
public class CadastroEmpreiteiraPage {

    private static final Logger log = LoggerFactory.getLogger(CadastroEmpreiteiraPage.class);

    private static final String TIPO_SERVICO_PADRAO = "outros";

    private static final By RAZAO_SOCIAL = By.id("inpRazaoSocialEmpr");
    private static final By NOME_FANTASIA = By.id("inpNomeFantasiaEmpr");
    private static final By CNPJ = By.id("inpCNPJEmpr");
    private static final By TELEFONE = By.id("inpTelefoneEmpr");
    private static final By WHATSAPP = By.id("inpWhatsAppEmpr");
    private static final By EMAIL = By.id("inpEmailEmpr");
    private static final By COR = By.id("inpCorEmpr");
    private static final By VALOR_UNITARIO = By.id("inpValorUnitarioEmpr");
    private static final By BOTAO_SALVAR = By.id("btnSalvarEmpreiteiro");
    private static final By BOTAO_ADICIONAR_SERVICO = By.id("btnAdicionarServico");
    private static final By STATUS = By.id("statusEmpreiteiros");

    private static final int TENTATIVAS_DE_CORRECAO = 3;

    private final CfObrasNavigator navigator;

    public CadastroEmpreiteiraPage(CfObrasNavigator navigator) {
        this.navigator = navigator;
    }

    public void abrir(WebDriver driver) {
        navigator.abrirAba(driver, "empreiteiros");
        esperar(1200);
    }

    public boolean jaCadastrada(WebDriver driver, String nome, String cnpj) {
        Object resultado = executar(driver,
                "function norm(t){ return (t||'').replace(/\\u00a0/g,' ').normalize('NFD')"
                        + "  .replace(/[\\u0300-\\u036f]/g,'')"
                        + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                        + "function compact(t){ return norm(t).replace(/[^A-Z0-9]/g,''); }"
                        + "var alvo = norm(arguments[0]);"
                        + "var alvoC = compact(arguments[0]);"
                        + "var cnpjAlvo = (arguments[1] || '').replace(/\\D/g,'');"
                        + "var cards = document.querySelectorAll('#listaEmpreiteiros .empreiteiro-card');"
                        + "for (var i = 0; i < cards.length; i++) {"
                        + "  if (cnpjAlvo.length === 14) {"
                        + "    var digitos = (cards[i].textContent || '').replace(/\\D/g,'');"
                        + "    if (digitos.indexOf(cnpjAlvo) >= 0) { return 'cnpj'; }"
                        + "  }"
                        + "  var nomeEl = cards[i].querySelector('.empreiteiro-nome');"
                        + "  var razaoEl = cards[i].querySelector('.empreiteiro-razao');"
                        + "  var textos = [nomeEl ? nomeEl.textContent : '', razaoEl ? razaoEl.textContent : ''];"
                        + "  for (var j = 0; j < textos.length; j++) {"
                        + "    if (!compact(textos[j])) { continue; }"
                        + "    if (norm(textos[j]) === alvo) { return 'nome'; }"
                        + "    if (compact(textos[j]) === alvoC) { return 'nome compacto'; }"
                        + "  }"
                        + "}"
                        + "return '';",
                nome, cnpj);

        String como = String.valueOf(resultado);

        if (como.isBlank() || "null".equals(como)) {
            return false;
        }

        log.info("'{}' ja existe no CF Obras (match por {})", nome, como);
        return true;
    }

    public String cadastrar(WebDriver driver, DadosCadastroEmpreiteira dados, String nomeObraCfObras) {
        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.visibilityOfElementLocated(RAZAO_SOCIAL));

        preencher(driver, RAZAO_SOCIAL, dados.razaoSocial());
        preencher(driver, NOME_FANTASIA, dados.nomeFantasia());
        preencher(driver, CNPJ, dados.cnpj());
        preencher(driver, TELEFONE, dados.telefone());
        preencher(driver, WHATSAPP, dados.whatsapp());
        preencher(driver, EMAIL, dados.email());
        preencher(driver, VALOR_UNITARIO, "0,00");
        definirCor(driver, dados.corIdentificacao());

        adicionarServicoOutros(driver);
        vincularObra(driver, nomeObraCfObras);

        List<String> faltando = conferirFormulario(driver);

        for (int tentativa = 1; tentativa <= TENTATIVAS_DE_CORRECAO && !faltando.isEmpty(); tentativa++) {
            log.warn("Formulario de '{}' incompleto: {} — corrigindo (tentativa {}/{})",
                    dados.razaoSocial(), String.join(", ", faltando), tentativa, TENTATIVAS_DE_CORRECAO);

            corrigir(driver, dados, nomeObraCfObras, faltando);
            faltando = conferirFormulario(driver);
        }

        if (!faltando.isEmpty()) {
            log.error("NAO SALVEI '{}': campos vazios: {}",
                    dados.razaoSocial(), String.join(", ", faltando));
            return "ERRO: formulario incompleto — faltou " + String.join(", ", faltando);
        }

        log.info("Formulario preenchido e conferido para '{}' — salvando", dados.razaoSocial());

        WebElement salvar = driver.findElement(BOTAO_SALVAR);
        executar(driver, "arguments[0].scrollIntoView({block:'center'});", salvar);
        esperar(400);

        try {
            salvar.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", salvar);
        }

        return aguardarResultado(driver, dados.razaoSocial());
    }

    private List<String> conferirFormulario(WebDriver driver) {
        List<String> faltando = new ArrayList<>();

        Map<String, String> campos = Map.of(
                "inpRazaoSocialEmpr", "razao social",
                "inpNomeFantasiaEmpr", "nome fantasia",
                "inpCNPJEmpr", "CNPJ",
                "inpTelefoneEmpr", "telefone",
                "inpWhatsAppEmpr", "whatsapp",
                "inpEmailEmpr", "email",
                "inpCorEmpr", "cor",
                "inpValorUnitarioEmpr", "valor unitario");

        for (Map.Entry<String, String> campo : campos.entrySet()) {
            if (vazioNaTela(driver, campo.getKey())) {
                faltando.add(campo.getValue());
            }
        }

        Object servico = executar(driver,
                "var sels = document.querySelectorAll('#servicosEmpreiteiro select.select-tipo-servico');"
                        + "for (var i = 0; i < sels.length; i++) {"
                        + "  if (sels[i].value) { return sels[i].value; }"
                        + "}"
                        + "return '';");

        if (servico == null || String.valueOf(servico).isBlank()) {
            faltando.add("tipo de servico");
        }

        Object obras = executar(driver,
                "return document.querySelectorAll("
                        + "'#obrasVinculadasEmpr input[type=checkbox]:checked').length;");

        if (obras == null || Integer.parseInt(String.valueOf(obras)) == 0) {
            faltando.add("obra vinculada");
        }

        return faltando;
    }

    private boolean vazioNaTela(WebDriver driver, String id) {
        Object valor = executar(driver,
                "var el = document.getElementById(arguments[0]);"
                        + "return el ? (el.value || '').trim() : '';",
                id);

        return valor == null || String.valueOf(valor).isBlank();
    }

    private void corrigir(WebDriver driver, DadosCadastroEmpreiteira dados,
                          String nomeObraCfObras, List<String> faltando) {
        if (faltando.contains("tipo de servico")) {

            clicarAdicionarServico(driver);
            adicionarServicoOutros(driver);
        }
        if (faltando.contains("obra vinculada")) {
            vincularObra(driver, nomeObraCfObras);
        }
        if (faltando.contains("razao social")) {
            preencher(driver, RAZAO_SOCIAL, dados.razaoSocial());
        }
        if (faltando.contains("nome fantasia")) {
            preencher(driver, NOME_FANTASIA, dados.nomeFantasia());
        }
        if (faltando.contains("CNPJ")) {
            preencher(driver, CNPJ, dados.cnpj());
        }
        if (faltando.contains("telefone")) {
            preencher(driver, TELEFONE, dados.telefone());
        }
        if (faltando.contains("whatsapp")) {
            preencher(driver, WHATSAPP, dados.whatsapp());
        }
        if (faltando.contains("email")) {
            preencher(driver, EMAIL, dados.email());
        }
        if (faltando.contains("cor")) {
            definirCor(driver, dados.corIdentificacao());
        }
        if (faltando.contains("valor unitario")) {
            preencher(driver, VALOR_UNITARIO, "0,00");
        }

        esperar(600);
    }

    private void clicarAdicionarServico(WebDriver driver) {
        List<WebElement> botoes = driver.findElements(BOTAO_ADICIONAR_SERVICO);

        if (botoes.isEmpty()) {
            return;
        }

        try {
            executar(driver, "arguments[0].scrollIntoView({block:'center'});", botoes.get(0));
            botoes.get(0).click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", botoes.get(0));
        }

        esperar(1200);
    }

    private void adicionarServicoOutros(WebDriver driver) {
        List<WebElement> botoes = driver.findElements(BOTAO_ADICIONAR_SERVICO);

        if (botoes.isEmpty()) {
            log.warn("Botao de adicionar servico nao encontrado");
            return;
        }

        boolean jaExiste = Boolean.TRUE.equals(executar(driver,
                "var sels = document.querySelectorAll('#servicosEmpreiteiro select.select-tipo-servico');"
                        + "for (var i = 0; i < sels.length; i++) {"
                        + "  if (sels[i].value) { return true; }"
                        + "}"
                        + "return false;"));

        if (!jaExiste) {
            boolean temSelectVazio = Boolean.TRUE.equals(executar(driver,
                    "return document.querySelectorAll('#servicosEmpreiteiro select.select-tipo-servico').length > 0;"));

            if (!temSelectVazio) {
                WebElement botao = botoes.get(0);
                executar(driver, "arguments[0].scrollIntoView({block:'center'});", botao);
                esperar(400);

                try {
                    botao.click();
                } catch (Exception e) {
                    executar(driver, "arguments[0].click();", botao);
                }

                esperar(1200);
            }
        }

        Object resultado = executar(driver,
                "var alvo = arguments[0];"
                        + "var sels = document.querySelectorAll('#servicosEmpreiteiro select.select-tipo-servico');"
                        + "if (sels.length === 0) { return 'sem select'; }"
                        + "for (var i = 0; i < sels.length; i++) {"
                        + "  var sel = sels[i];"
                        + "  if (sel.value) { continue; }"
                        + "  for (var j = 0; j < sel.options.length; j++) {"
                        + "    if (sel.options[j].value === alvo) {"
                        + "      sel.selectedIndex = j;"
                        + "      sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "      return sel.options[j].textContent.trim();"
                        + "    }"
                        + "  }"
                        + "}"
                        + "return 'ja preenchido';",
                TIPO_SERVICO_PADRAO);

        log.info("Tipo de servico definido: {}", resultado);
        esperar(600);
    }

    private void vincularObra(WebDriver driver, String nomeObraCfObras) {
        Object resultado = executar(driver,
                "function norm(t){ return (t||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'')"
                        + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                        + "var alvo = norm(arguments[0]);"
                        + "var itens = document.querySelectorAll('#obrasVinculadasEmpr label');"
                        + "for (var i = 0; i < itens.length; i++) {"
                        + "  if (norm(itens[i].textContent) !== alvo) { continue; }"
                        + "  var chk = itens[i].querySelector('input[type=checkbox]');"
                        + "  if (!chk) { return 'sem checkbox'; }"
                        + "  if (!chk.checked) { chk.click(); }"
                        + "  return chk.value;"
                        + "}"
                        + "return null;",
                nomeObraCfObras);

        if (resultado == null) {
            log.warn("Obra '{}' nao encontrada na lista de Obras Vinculadas", nomeObraCfObras);
        } else {
            log.info("Obra '{}' vinculada ({})", nomeObraCfObras, resultado);
        }

        esperar(400);
    }

    private void preencher(WebDriver driver, By campo, String valor) {
        WebElement elemento = driver.findElement(campo);
        elemento.clear();

        if (valor != null && !valor.isBlank()) {
            elemento.sendKeys(valor);
        }
    }

    private void definirCor(WebDriver driver, String hex) {
        WebElement input = driver.findElement(COR);
        executar(driver,
                "arguments[0].value = arguments[1];"
                        + "arguments[0].dispatchEvent(new Event('input', {bubbles:true}));"
                        + "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                input, hex);
    }

    private String aguardarResultado(WebDriver driver, String razaoSocial) {
        esperar(2500);

        String mensagem = textoDoStatus(driver);
        boolean formularioLimpo = campoVazio(driver, RAZAO_SOCIAL);

        log.info("Apos salvar '{}': status='{}' | formulario limpo={}",
                razaoSocial, mensagem.isBlank() ? "(vazio)" : mensagem, formularioLimpo);

        if (!mensagem.isBlank()) {
            return mensagem;
        }

        return formularioLimpo ? "cadastrado (formulario limpo)" : "ERRO: nada mudou apos salvar";
    }

    private boolean campoVazio(WebDriver driver, By campo) {
        List<WebElement> elementos = driver.findElements(campo);
        if (elementos.isEmpty()) {
            return false;
        }
        String valor = elementos.get(0).getDomProperty("value");
        return valor == null || valor.isBlank();
    }

    private String textoDoStatus(WebDriver driver) {
        List<WebElement> status = driver.findElements(STATUS);
        return status.isEmpty() ? "" : status.get(0).getText().trim();
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