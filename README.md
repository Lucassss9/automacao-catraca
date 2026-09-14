# Automação Catraca

Automação em Java que elimina o processo manual de conciliar a frequência de acesso (catraca) com o sistema de gestão de obras usado pela empresa, incluindo o cadastro automático de empreiteiras e funcionários quando necessário.

## 🎯 Problema que resolve

Todos os dias, alguém precisa:
1. Entrar no sistema de controle de acesso (catraca) e gerar o relatório de frequência de uma obra
2. Baixar o relatório em Excel
3. Entrar no sistema de gestão de obras, achar a obra certa e subir esse relatório no módulo de ponto
4. Para cada linha do relatório, vincular manualmente a empreiteira e o funcionário a um cadastro já existente — e se não existir, cadastrar na mão (endereço, documentação, nível de acesso, etc.)

Isso é repetitivo, sujeito a erro humano e toma tempo de quem poderia estar fazendo outra coisa. Esse projeto automatiza esse fluxo inteiro.

## ⚙️ Como funciona

```
Sistema de acesso ──► Exporta relatório (Excel) ──► Automação ──► Sistema de gestão
                                                          │
                                                          ├── Lê e processa o relatório
                                                          ├── Verifica se empreiteira/funcionário já existem
                                                          ├── Se não existirem, busca os dados e cadastra
                                                          └── Importa o relatório no módulo de ponto
```

1. **Login e extração** — acessa o sistema de controle de acesso, seleciona a obra e a data, gera e baixa o relatório de frequência em Excel.
2. **Login no sistema de gestão** — autentica no sistema e navega até o módulo de ponto.
3. **Verificação de cadastro** — para cada empreiteira/funcionário do relatório, verifica se já existe cadastro no sistema de gestão.
4. **Cadastro automático (quando necessário)** — busca os dados completos no sistema de acesso (empreiteira: Razão Social, CNPJ, Nome Fantasia, contrato, endereço, contato; funcionário: CPF, RG, turno, função, datas) e cadastra no sistema de gestão, aplicando regras de negócio definidas (ex: telefone ausente vira zeros, e-mail ausente é gerado a partir do nome).
5. **Importação do relatório** — sobe o Excel processado no módulo de ponto da obra selecionada.

## 🛠️ Tech Stack

- **Java** — linguagem principal
- **Selenium WebDriver** — automação de navegação e interação com TRC Mobile e CF Obras
- **Maven** — gerenciamento de dependências e build
- **Banco de dados** — apoio para verificação/cache de cadastros já processados

## 📁 Estrutura do projeto

```
src/
├── main/java/...     # código-fonte da automação
docs/                  # documentação do projeto
pom.xml                # configuração Maven
```

## 🚀 Status

Projeto concluído e em uso — atualmente automatiza o processo para uma obra, com arquitetura pensada para expandir para múltiplas obras.

## 📌 Contexto

Desenvolvido para resolver uma necessidade real do meu dia a dia profissional, onde sou responsável pela gestão do sistema interno de obras da empresa. Projetado e implementado sozinho, do levantamento de requisitos à arquitetura até o código.
