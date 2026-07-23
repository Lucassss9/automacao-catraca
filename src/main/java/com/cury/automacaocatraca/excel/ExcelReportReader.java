package com.cury.automacaocatraca.excel;

import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Component
public class ExcelReportReader {

    private static final int LINHA_INICIAL = 6;
    private static final int COL_OBRA = 0;
    private static final int COL_TIPO = 6;

    private final DataFormatter dataFormatter = new DataFormatter();

    public RelatorioFrequencia ler(Path arquivo) {
        try (Workbook workbook = WorkbookFactory.create(arquivo.toFile())) {
            Sheet aba = workbook.getSheetAt(0);
            validarLayout(aba);
            return new RelatorioFrequencia("Teste", Map.of());
        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler o relatorio: " + arquivo, e);
        }
    }

    private void validarLayout(Sheet aba) {
        Row linha = aba.getRow(LINHA_INICIAL);

        if (linha == null) {
            throw new RuntimeException(
                    "Layout inesperado: linha " + (LINHA_INICIAL + 1) + " nao existe na planilha");
        }

        String obra = textoDa(linha, COL_OBRA);
        if (obra.isBlank()) {
            throw new RuntimeException(
                    "Layout inesperado: esperava o nome da obra na coluna A da linha " + (LINHA_INICIAL + 1));
        }

        String tipo = NormalizadorNome.normalizar(textoDa(linha, COL_TIPO));
        if (!tipo.equals("ENTRADA") && !tipo.equals("SAIDA")) {
            throw new RuntimeException(
                    "Layout inesperado: esperava ENTRADA ou SAIDA na coluna G da linha "
                            + (LINHA_INICIAL + 1) + ", encontrei: '" + tipo + "'");
        }
    }

    private String textoDa(Row linha, int coluna) {
        return dataFormatter.formatCellValue(linha.getCell(coluna)).trim();
    }
}