package com.cury.automacaocatraca.orchestration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AnalisadorDeTempos {

    private final int esperaImplicitaSegundos;

    public AnalisadorDeTempos(
            @Value("${automacao.navegador.espera-implicita-segundos:3}") int esperaImplicitaSegundos) {
        this.esperaImplicitaSegundos = Math.max(0, esperaImplicitaSegundos);
    }

    public List<String> analisarObra(Cronometro cronometro) {
        List<String> achados = new ArrayList<>();

        if (cronometro == null) {
            return achados;
        }

        long total = cronometro.totalMs();

        if (total <= 0) {
            return achados;
        }

        List<Cronometro.Estatistica> etapas = cronometro.etapas();

        if (!etapas.isEmpty()) {
            Cronometro.Estatistica maior = etapas.get(0);

            achados.add("A etapa mais cara foi **" + maior.nome + "** com "
                    + Cronometro.formatar(maior.totalMs) + " ("
                    + Cronometro.percentual(maior.totalMs, total) + " do tempo da obra)"
                    + (maior.itens > 0
                    ? ", " + maior.itens + " item(ns) a " + Cronometro.formatar(maior.mediaItemMs()) + " cada"
                    : "")
                    + ".");

            if (maior.itens > 0 && maior.piorItemMs > 0) {
                achados.add("O item mais lento dessa etapa foi `" + maior.piorItem + "` com "
                        + Cronometro.formatar(maior.piorItemMs) + ".");
            }
        }

        for (Cronometro.Estatistica etapa : etapas) {
            if (etapa.itens >= 3 && etapa.mediaItemMs() > 25000) {
                achados.add("Cada item de **" + etapa.nome + "** custa em media "
                        + Cronometro.formatar(etapa.mediaItemMs())
                        + " — acima disso quase sempre e formulario com espera fixa entre os campos. "
                        + "Reduzir as pausas dessa tela derruba o tempo total em "
                        + Cronometro.percentual(etapa.totalMs, total) + ".");
            }
        }

        long parado = cronometro.totalPausaMs();
        long navegador = cronometro.totalNavegadorMs();

        if (parado > total * 0.25) {
            achados.add("O robo passou " + Cronometro.formatar(parado) + " ("
                    + Cronometro.percentual(parado, total) + ") **parado entre uma acao e outra**, "
                    + "ou seja, em Thread.sleep fixo e gravacao no banco. Trocar os sleeps por espera "
                    + "condicional (esperar o elemento aparecer) e o ganho mais barato do projeto.");
        }

        Cronometro.Pausa maiorFaixa = null;

        for (Cronometro.Pausa pausa : cronometro.pausas()) {
            if (maiorFaixa == null || pausa.totalMs > maiorFaixa.totalMs) {
                maiorFaixa = pausa;
            }
        }

        if (maiorFaixa != null && maiorFaixa.totalMs > 30000) {
            achados.add("A faixa de pausa que mais pesou foi **" + maiorFaixa.faixa + "**: "
                    + maiorFaixa.vezes + " paradas somando " + Cronometro.formatar(maiorFaixa.totalMs) + ".");
        }

        long custoBuscasVazias = cronometro.buscasVazias() * esperaImplicitaSegundos * 1000L;

        if (custoBuscasVazias > 30000) {
            achados.add(cronometro.buscasVazias() + " buscas de elemento voltaram vazias. "
                    + "Como a espera implicita e de " + esperaImplicitaSegundos + "s, cada uma paga o tempo "
                    + "inteiro antes de desistir: ate " + Cronometro.formatar(custoBuscasVazias)
                    + " jogados fora. Vale conferir os seletores que nunca acham nada e baixar a espera "
                    + "implicita para 1s onde houver espera explicita.");
        }

        if (navegador > 0 && navegador < total * 0.4) {
            achados.add("So " + Cronometro.percentual(navegador, total) + " do tempo foi gasto conversando "
                    + "com o navegador (" + Cronometro.formatar(navegador) + " de "
                    + Cronometro.formatar(total) + "). O resto e espera do robo, nao lentidao do site.");
        }

        for (Cronometro.UsoNavegador uso : cronometro.navegador()) {
            if (uso.maiorMs > 30000) {
                achados.add("A chamada isolada mais demorada foi um **" + uso.categoria + "** de "
                        + Cronometro.formatar(uso.maiorMs) + " em `" + uso.piorAlvo + "` — "
                        + "esse e o ponto onde o robo mais trava esperando a tela responder.");
                break;
            }
        }

        long errosNavegador = cronometro.errosNavegador();

        if (errosNavegador > 40) {
            achados.add(errosNavegador + " chamadas do navegador terminaram em excecao (elemento nao "
                    + "encontrado, elemento velho). Seletor instavel custa tempo e gera retentativa.");
        }

        long importacao = cronometro.tempoDaEtapa(Cronometro.ETAPA_IMPORTACAO);

        if (importacao > 120000) {
            achados.add("Upload e importacao levaram " + Cronometro.formatar(importacao)
                    + ". Boa parte disso e o proprio CF Obras processando o arquivo — o que da para "
                    + "economizar aqui e subir a planilha uma vez por rodada em vez de uma vez por "
                    + "empreendimento (caso do Carrao, que importa o mesmo arquivo duas vezes).");
        }

        long trc = cronometro.tempoDaEtapa(Cronometro.ETAPA_TRC);

        if (trc > 90000) {
            achados.add("A etapa do TRC levou " + Cronometro.formatar(trc)
                    + " — login, troca de obra e geracao do relatorio 03.1 sao caros e sao refeitos "
                    + "para cada obra. Reaproveitar a sessao entre obras da mesma rodada economiza "
                    + "isso quase inteiro a partir da segunda obra.");
        }

        return achados;
    }

    public List<String> analisarRodada(List<CronometroContexto.Fechamento> fechamentos) {
        List<String> achados = new ArrayList<>();

        if (fechamentos == null || fechamentos.isEmpty()) {
            return achados;
        }

        Cronometro maisLenta = null;
        Cronometro maisRapida = null;
        long somaTrc = 0;
        long somaTotal = 0;

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro == null) {
                continue;
            }

            somaTotal += cronometro.totalMs();
            somaTrc += cronometro.tempoDaEtapa(Cronometro.ETAPA_TRC);

            if (maisLenta == null || cronometro.totalMs() > maisLenta.totalMs()) {
                maisLenta = cronometro;
            }
            if (maisRapida == null || cronometro.totalMs() < maisRapida.totalMs()) {
                maisRapida = cronometro;
            }
        }

        if (maisLenta == null) {
            return achados;
        }

        achados.add("Obra mais demorada: **" + maisLenta.codigoObra() + "** com "
                + Cronometro.formatar(maisLenta.totalMs()) + " ("
                + Cronometro.percentual(maisLenta.totalMs(), somaTotal) + " da rodada).");

        if (maisRapida != null && maisRapida != maisLenta) {
            achados.add("Obra mais rapida: **" + maisRapida.codigoObra() + "** com "
                    + Cronometro.formatar(maisRapida.totalMs()) + ".");
        }

        int funcionariosDaMaisLenta = funcionariosProcessados(fechamentos, maisLenta);
        int funcionariosDaMaisRapida = maisRapida == null ? 0 : funcionariosProcessados(fechamentos, maisRapida);

        if (funcionariosDaMaisLenta > 0 && funcionariosDaMaisRapida > 0 && maisRapida != maisLenta) {
            long porFuncionarioLenta = maisLenta.totalMs() / funcionariosDaMaisLenta;
            long porFuncionarioRapida = maisRapida.totalMs() / funcionariosDaMaisRapida;

            if (porFuncionarioLenta > porFuncionarioRapida * 1.4) {
                achados.add("A diferenca **nao e so volume**: " + maisLenta.codigoObra() + " gasta "
                        + Cronometro.formatar(porFuncionarioLenta) + " por funcionario do relatorio contra "
                        + Cronometro.formatar(porFuncionarioRapida) + " de " + maisRapida.codigoObra()
                        + ". Olhar as pendencias dessa obra: cada nome que nao casa vira busca extra no TRC.");
            } else {
                achados.add("A diferenca de tempo entre as obras acompanha o **volume do relatorio** ("
                        + Cronometro.formatar(porFuncionarioLenta) + " por funcionario na mais lenta contra "
                        + Cronometro.formatar(porFuncionarioRapida) + " na mais rapida), entao o robo "
                        + "esta se comportando igual em todas — quem manda no relogio e a quantidade de gente.");
            }
        }

        if (fechamentos.size() > 1 && somaTrc > 60000) {
            achados.add("Somando as obras, **" + Cronometro.formatar(somaTrc) + "** foram gastos so em "
                    + "login/troca de obra/geracao do relatorio no TRC. Um unico navegador do TRC reaproveitado "
                    + "na rodada inteira devolveria perto de "
                    + Cronometro.formatar(somaTrc - somaTrc / fechamentos.size()) + ".");
        }

        long pausaTotal = 0;
        long navegadorTotal = 0;

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro != null) {
                pausaTotal += cronometro.totalPausaMs();
                navegadorTotal += cronometro.totalNavegadorMs();
            }
        }

        if (pausaTotal > 0) {
            achados.add("Na rodada inteira: " + Cronometro.formatar(navegadorTotal)
                    + " agindo no navegador e " + Cronometro.formatar(pausaTotal) + " parado esperando ("
                    + Cronometro.percentual(pausaTotal, somaTotal) + " do total).");
        }

        return achados;
    }

    private int funcionariosProcessados(List<CronometroContexto.Fechamento> fechamentos, Cronometro cronometro) {
        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            if (fechamento.cronometro() == cronometro && fechamento.dados() != null) {
                return Math.max(1, fechamento.dados().funcionariosNoRelatorio);
            }
        }

        return 0;
    }
}