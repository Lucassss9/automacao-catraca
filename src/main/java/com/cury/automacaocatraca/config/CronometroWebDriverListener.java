package com.cury.automacaocatraca.config;

import com.cury.automacaocatraca.orchestration.Cronometro;
import com.cury.automacaocatraca.orchestration.CronometroContexto;
import org.openqa.selenium.support.events.WebDriverListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class CronometroWebDriverListener implements WebDriverListener {

    private final CronometroContexto contexto;

    private final ThreadLocal<Deque<Long>> emAndamento = ThreadLocal.withInitial(ArrayDeque::new);
    private final ThreadLocal<Long> fimDaUltima = ThreadLocal.withInitial(() -> 0L);

    public CronometroWebDriverListener(CronometroContexto contexto) {
        this.contexto = contexto;
    }

    @Override
    public void beforeAnyCall(Object target, Method method, Object[] args) {
        Deque<Long> pilha = emAndamento.get();
        long agora = System.currentTimeMillis();

        if (pilha.isEmpty()) {
            long ultima = fimDaUltima.get();

            if (ultima > 0) {
                Cronometro cronometro = contexto.atual();

                if (cronometro != null) {
                    cronometro.registrarPausa(agora - ultima);
                }
            }
        }

        pilha.push(agora);
    }

    @Override
    public void afterAnyCall(Object target, Method method, Object[] args, Object result) {
        concluir(method, args, result, false);
    }

    @Override
    public void onError(Object target, Method method, Object[] args, InvocationTargetException e) {
        concluir(method, args, null, true);
    }

    private void concluir(Method method, Object[] args, Object resultado, boolean erro) {
        Deque<Long> pilha = emAndamento.get();
        Long comeco = pilha.poll();
        long agora = System.currentTimeMillis();

        if (pilha.isEmpty()) {
            fimDaUltima.set(agora);
        }

        if (comeco == null) {
            return;
        }

        Cronometro cronometro = contexto.atual();

        if (cronometro == null) {
            return;
        }

        String metodo = method.getName();
        boolean busca = metodo.startsWith("findElement");
        boolean listaVazia = resultado instanceof List<?> lista && lista.isEmpty();
        boolean semResultado = busca && (erro || listaVazia);

        cronometro.registrarChamada(categoria(metodo), alvo(metodo, args), agora - comeco, erro, semResultado);
    }

    private String categoria(String metodo) {
        if (metodo.startsWith("findElement")) {
            return "buscar elemento";
        }

        return switch (metodo) {
            case "click" -> "clicar";
            case "sendKeys", "clear", "submit" -> "digitar";
            case "get", "to", "back", "forward", "refresh" -> "abrir pagina";
            case "executeScript", "executeAsyncScript" -> "javascript";
            case "switchTo", "frame", "defaultContent", "parentFrame", "window", "alert" -> "trocar de contexto";
            case "getText", "getAttribute", "getDomAttribute", "getDomProperty", "getCssValue",
                 "isDisplayed", "isEnabled", "isSelected", "getTagName", "getRect", "getSize",
                 "getLocation" -> "ler elemento";
            case "getPageSource" -> "ler a pagina inteira";
            case "getCurrentUrl", "getTitle", "getWindowHandle", "getWindowHandles" -> "ler endereco";
            case "getScreenshotAs" -> "print de diagnostico";
            case "quit", "close" -> "fechar navegador";
            case "manage", "timeouts", "implicitlyWait", "navigate", "maximize", "setSize" -> "ajustar navegador";
            default -> "outras (" + metodo + ")";
        };
    }

    private String alvo(String metodo, Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return "";
        }

        String texto = String.valueOf(args[0]).replace('\n', ' ').replace('|', '/').trim();

        if (metodo.startsWith("execute") && texto.length() > 70) {
            return texto.substring(0, 70) + "...";
        }

        return texto.length() > 90 ? texto.substring(0, 90) + "..." : texto;
    }
}