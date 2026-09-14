# Minha Conta Digital

Projeto acadêmico que simula uma conta digital usando Kotlin e Compose Desktop.
Esta é uma implementação própria, feita de forma simples para praticar programação orientada a objetos.

## Funcionalidades

- Cadastro de Pessoa Física com CPF, telefone, e-mail e data de nascimento.
- Cadastro de Pessoa Jurídica com CNPJ, telefone, e-mail e razão social.
- Número de conta gerado automaticamente e documento único.
- Login com senha protegida por SHA-256.
- Edição e exclusão da própria conta.
- Depósito, saque e transferência para bancos externos simulados.
- Registro das operações com tipo, valor, data e hora.
- Extrato da conta e três tipos de investimento com taxas diferentes.
- Validação de valor positivo e saldo insuficiente.

## Executar

Com Java 21 instalado, use:

```powershell
.\gradlew.bat :app:run
```

Esta é uma aplicação desktop local. Ela não depende de servidor, Firebase ou banco de dados remoto.
Os dados ficam somente em memória nesta versão didática e são apagados quando o programa fecha.
Depois que as dependências forem baixadas pelo Gradle, o programa não precisa de conexão externa para funcionar.

## Estrutura

O programa está em `app/src/main/kotlin/br/estudante/contadigital/Main.kt`.
As classes `Conta`, `Sistema`, `Operacao` e `Investimento` representam os dados da aplicação.
