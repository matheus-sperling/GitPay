package br.estudante.contadigital

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Tipos usados para deixar as regras do sistema mais fáceis de ler.
private enum class Pessoa { FISICA, JURIDICA }
private enum class Tela { LOGIN, CADASTRO, HOME, EXTRATO, INVESTIMENTOS, PERFIL }
private enum class TipoOperacao { DEPOSITO, SAQUE, TRANSFERENCIA, INVESTIMENTO }

// Confere os dígitos verificadores de CPF ou CNPJ antes de criar a conta.
private fun documentoValido(pessoa: Pessoa, documento: String): Boolean {
    val digitos = documento.filter(Char::isDigit)
    if (digitos.isEmpty() || digitos.all { it == digitos.first() }) return false
    return when (pessoa) {
        Pessoa.FISICA -> {
            if (digitos.length != 11) return false
            val somaPrimeiro = (0..8).sumOf { indice -> (digitos[indice] - '0') * (10 - indice) }
            val primeiroDigito = (somaPrimeiro * 10 % 11) % 10
            val somaSegundo = (0..9).sumOf { indice -> (digitos[indice] - '0') * (11 - indice) }
            val segundoDigito = (somaSegundo * 10 % 11) % 10
            digitos[9] - '0' == primeiroDigito && digitos[10] - '0' == segundoDigito
        }
        Pessoa.JURIDICA -> {
            if (digitos.length != 14) return false
            val pesosPrimeiro = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
            val pesosSegundo = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
            fun calcularDigito(pesos: IntArray): Int {
                val soma = pesos.indices.sumOf { indice -> (digitos[indice] - '0') * pesos[indice] }
                val resto = soma % 11
                return if (resto < 2) 0 else 11 - resto
            }
            digitos[12] - '0' == calcularDigito(pesosPrimeiro) && digitos[13] - '0' == calcularDigito(pesosSegundo)
        }
    }
}

// Aplica a validação mínima de formato usada pelos campos de e-mail.
private fun emailValido(email: String): Boolean = email.trim().contains("@") && email.trim().contains(".")

// Valida os dados editáveis do perfil antes de gravá-los na conta.
private fun validarPerfil(nome: String, telefone: String, email: String) {
    require(nome.trim().length >= 3) { "Informe um nome válido." }
    require(telefone.trim().length >= 8) { "Informe um telefone válido." }
    require(emailValido(email)) { "Informe um e-mail válido." }
}

// Registra uma movimentação financeira realizada pela conta.
private data class Operacao(val tipo: TipoOperacao, val valor: Double, val descricao: String, val data: LocalDateTime = LocalDateTime.now())

// Guarda o produto, a taxa anual e o valor aplicado em um investimento.
private data class Investimento(val produto: String, val taxa: Double, val valor: Double)

// Modelo simples de conta digital. As listas guardam o extrato e as aplicações.
private class Conta(
    val numero: Int,
    val pessoa: Pessoa,
    var nome: String,
    val documento: String,
    var telefone: String,
    var email: String,
    val detalhe: String,
    val senha: String,
    var saldo: Double = 0.0
) {
    val operacoes = mutableListOf<Operacao>()
    val investimentos = mutableListOf<Investimento>()
}

// Repositório em memória. É propositalmente simples para um projeto de estudante.
private class Sistema {
    private val contas = mutableListOf<Conta>()
    private var proximoNumero = 5001
    val bancosExternos = listOf("Banco Horizonte - 104", "Banco Popular - 290", "Banco Central - 777")

    fun cadastrar(pessoa: Pessoa, nome: String, documento: String, telefone: String, email: String, detalhe: String, senha: String): Conta {
        require(nome.trim().length >= 3) { "Informe um nome válido." }
        require(documentoValido(pessoa, documento)) { "Informe um CPF ou CNPJ válido." }
        require(telefone.trim().length >= 8) { "Informe um telefone válido." }
        require(emailValido(email)) { "Informe um e-mail válido." }
        require(detalhe.trim().isNotEmpty()) { "Preencha o campo complementar." }
        require(senha.length >= 4) { "A senha deve ter pelo menos 4 caracteres." }
        require(contas.none { it.documento == documento.trim() }) { "Este CPF/CNPJ já está cadastrado." }
        return Conta(proximoNumero++, pessoa, nome.trim(), documento.trim(), telefone.trim(), email.trim(), detalhe.trim(), senha.gerarHash()).also(contas::add)
    }

    fun autenticar(documento: String, senha: String): Conta? = contas.firstOrNull { it.documento == documento.trim() && it.senha == senha.gerarHash() }
    fun procurar(numero: String): Conta? = contas.firstOrNull { it.numero.toString() == numero.trim() }
    fun excluir(conta: Conta) { contas.remove(conta) }
}

// Converte a senha em SHA-256 antes de armazená-la ou compará-la.
private fun String.gerarHash(): String = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

// Formata valores monetários seguindo o padrão brasileiro.
private fun Double.emReais(): String = "R$ %.2f".format(Locale("pt", "BR"), this)

// Formato usado para exibir data e hora no extrato.
private val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private val verdeClaro = lightColorScheme(
    primary = Color(0xFF176B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7EBD6),
    onPrimaryContainer = Color(0xFF062016),
    secondary = Color(0xFF4D6354),
    secondaryContainer = Color(0xFFD0E8D5),
    background = Color(0xFFF7FBF7),
    surface = Color.White,
    error = Color(0xFFB3261E)
)

@Composable
// Controla a navegação geral e mantém a conta atual enquanto a janela está aberta.
fun Aplicacao() {
    val sistema = remember { Sistema() }
    var tela by remember { mutableStateOf(Tela.LOGIN) }
    var conta by remember { mutableStateOf<Conta?>(null) }
    var mensagem by remember { mutableStateOf("") }

    MaterialTheme(colorScheme = verdeClaro) {
        when (tela) {
            Tela.LOGIN -> Login({ documento, senha ->
                conta = sistema.autenticar(documento, senha)
                if (conta == null) mensagem = "Documento ou senha incorretos." else { mensagem = ""; tela = Tela.HOME }
            }, { tela = Tela.CADASTRO; mensagem = "" }, mensagem)
            Tela.CADASTRO -> Cadastro({ pessoa, nome, documento, telefone, email, detalhe, senha ->
                try {
                    conta = sistema.cadastrar(pessoa, nome, documento, telefone, email, detalhe, senha)
                    mensagem = "Conta ${conta!!.numero} criada."
                    tela = Tela.HOME
                } catch (erro: IllegalArgumentException) { mensagem = erro.message ?: "Confira os dados." }
            }, { tela = Tela.LOGIN }, mensagem)
            else -> conta?.let { Principal(it, sistema, tela, { tela = it }, { conta = null; tela = Tela.LOGIN }, { mensagem = it }, mensagem) }
        }
    }
}

@Composable
// Centraliza formulários e permite rolagem quando a altura da janela é menor.
private fun Centralizado(conteudo: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { conteudo() }
    }
}

@Composable
// Exibe a entrada do sistema e encaminha o usuário para login ou cadastro.
private fun Login(entrar: (String, String) -> Unit, cadastrar: () -> Unit, mensagem: String) {
    var documento by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    Centralizado {
        Text("Minha Conta", style = MaterialTheme.typography.displaySmall)
        Text("Simulador de operações bancárias")
        Spacer(Modifier.height(15.dp))
        OutlinedTextField(documento, { documento = it }, label = { Text("CPF ou CNPJ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(senha, { senha = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({ entrar(documento, senha) }, Modifier.fillMaxWidth()) { Text("Entrar") }
        OutlinedButton(cadastrar, Modifier.fillMaxWidth()) { Text("Criar conta") }
        if (mensagem.isNotEmpty()) Text(mensagem, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
// Coleta os dados de uma pessoa física ou jurídica e cria a conta local.
private fun Cadastro(criar: (Pessoa, String, String, String, String, String, String) -> Unit, voltar: () -> Unit, mensagem: String) {
    var pessoa by remember { mutableStateOf(Pessoa.FISICA) }
    var nome by remember { mutableStateOf("") }
    var documento by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var detalhe by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    Centralizado {
        Text("Nova conta", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pessoa == Pessoa.FISICA) Button({ pessoa = Pessoa.FISICA }) { Text("Pessoa Física") } else OutlinedButton({ pessoa = Pessoa.FISICA }) { Text("Pessoa Física") }
            if (pessoa == Pessoa.JURIDICA) Button({ pessoa = Pessoa.JURIDICA }) { Text("Pessoa Jurídica") } else OutlinedButton({ pessoa = Pessoa.JURIDICA }) { Text("Pessoa Jurídica") }
        }
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(documento, { documento = it }, label = { Text(if (pessoa == Pessoa.FISICA) "CPF" else "CNPJ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(detalhe, { detalhe = it }, label = { Text(if (pessoa == Pessoa.FISICA) "Data de nascimento" else "Razão social") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(telefone, { telefone = it }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(senha, { senha = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({ criar(pessoa, nome, documento, telefone, email, detalhe, senha) }, Modifier.fillMaxWidth()) { Text("Cadastrar") }
        TextButton(voltar) { Text("Voltar") }
        if (mensagem.isNotEmpty()) Text(mensagem, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
// Monta a estrutura responsiva da conta, com menu lateral ou menu rolável.
private fun Principal(conta: Conta, sistema: Sistema, tela: Tela, navegar: (Tela) -> Unit, sair: () -> Unit, avisar: (String) -> Unit, mensagem: String) {
    Scaffold { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val compacto = maxWidth < 820.dp
            if (compacto) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cabecalho(conta)
                    MenuNavegacao(tela, navegar, sair)
                    ConteudoPrincipal(conta, sistema, tela, avisar, mensagem, sair)
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.widthIn(min = 180.dp, max = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Cabecalho(conta)
                        MenuNavegacao(tela, navegar, sair, vertical = true)
                    }
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ConteudoPrincipal(conta, sistema, tela, avisar, mensagem, sair)
                    }
                }
            }
        }
    }
}

@Composable
// Mostra a identificação da conta no topo da navegação.
private fun Cabecalho(conta: Conta) {
    Text("Minha Conta", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Text("Conta nº ${conta.numero}", style = MaterialTheme.typography.labelLarge)
}

@Composable
// Renderiza os botões de navegação na orientação adequada ao tamanho da janela.
private fun MenuNavegacao(tela: Tela, navegar: (Tela) -> Unit, sair: () -> Unit, vertical: Boolean = false) {
    val modifier = if (vertical) Modifier else Modifier.horizontalScroll(rememberScrollState())
    val itens = listOf(Tela.HOME to "Resumo", Tela.EXTRATO to "Extrato", Tela.INVESTIMENTOS to "Investimentos", Tela.PERFIL to "Meu perfil")
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itens.forEach { (destino, nome) ->
                if (tela == destino) Button({ navegar(destino) }, Modifier.fillMaxWidth()) { Text(nome) } else OutlinedButton({ navegar(destino) }, Modifier.fillMaxWidth()) { Text(nome) }
            }
            TextButton(sair) { Text("Sair") }
        }
    } else Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itens.forEach { (destino, nome) ->
            if (tela == destino) Button({ navegar(destino) }) { Text(nome) } else OutlinedButton({ navegar(destino) }) { Text(nome) }
        }
        TextButton(sair) { Text("Sair") }
    }
}

@Composable
// Seleciona a tela ativa e mostra mensagens de operação ao usuário.
private fun ConteudoPrincipal(conta: Conta, sistema: Sistema, tela: Tela, avisar: (String) -> Unit, mensagem: String, sair: () -> Unit) {
    when (tela) {
        Tela.HOME -> Resumo(conta, sistema, avisar)
        Tela.EXTRATO -> Extrato(conta)
        Tela.INVESTIMENTOS -> Investimentos(conta, avisar)
        Tela.PERFIL -> Perfil(conta, sistema, sair, avisar)
        else -> Unit
    }
    if (mensagem.isNotEmpty()) Text(mensagem, color = MaterialTheme.colorScheme.primary)
}

@Composable
// Apresenta saldo, ações financeiras e as operações mais recentes.
private fun Resumo(conta: Conta, sistema: Sistema, avisar: (String) -> Unit) {
    Text("Olá, ${conta.nome}", style = MaterialTheme.typography.headlineMedium)
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("Saldo disponível", style = MaterialTheme.typography.titleMedium); Text(conta.saldo.emReais(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary) } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 760.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Acao("Depositar") { valor -> if (valor > 0) { conta.saldo += valor; conta.operacoes.add(Operacao(TipoOperacao.DEPOSITO, valor, "Depósito")); avisar("Depósito realizado.") } else avisar("Valor inválido.") }
                Acao("Sacar") { valor -> if (valor > 0 && conta.saldo >= valor) { conta.saldo -= valor; conta.operacoes.add(Operacao(TipoOperacao.SAQUE, valor, "Saque")); avisar("Saque realizado.") } else avisar("Saldo insuficiente.") }
                Transferencia(conta, sistema, avisar)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Acao("Depositar") { valor -> if (valor > 0) { conta.saldo += valor; conta.operacoes.add(Operacao(TipoOperacao.DEPOSITO, valor, "Depósito")); avisar("Depósito realizado.") } else avisar("Valor inválido.") }
            Acao("Sacar") { valor -> if (valor > 0 && conta.saldo >= valor) { conta.saldo -= valor; conta.operacoes.add(Operacao(TipoOperacao.SAQUE, valor, "Saque")); avisar("Saque realizado.") } else avisar("Saldo insuficiente.") }
            Transferencia(conta, sistema, avisar)
        }
    }
    Text("Últimas operações", style = MaterialTheme.typography.titleLarge)
    conta.operacoes.takeLast(4).reversed().forEach { Linha(it) }
}

@Composable
// Processa transferências internas e externas com banco selecionável.
private fun Transferencia(conta: Conta, sistema: Sistema, avisar: (String) -> Unit) {
    var destino by remember { mutableStateOf("") }
    var texto by remember { mutableStateOf("") }
    var bancoAberto by remember { mutableStateOf(false) }
    var bancoSelecionado by remember { mutableStateOf(sistema.bancosExternos.first()) }
    Column(Modifier.fillMaxWidth().widthIn(min = 210.dp, max = 360.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Transferir", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(destino, { destino = it }, label = { Text("Conta destino") })
        OutlinedTextField(texto, { texto = it }, label = { Text("Valor") })
        OutlinedButton({ bancoAberto = true }) { Text(bancoSelecionado) }
        DropdownMenu(bancoAberto, { bancoAberto = false }) {
            sistema.bancosExternos.forEach { banco ->
                DropdownMenuItem({ Text(banco) }, { bancoSelecionado = banco; bancoAberto = false })
            }
        }
        Button({
            val valor = texto.replace(',', '.').toDoubleOrNull() ?: 0.0
            val outraConta = sistema.procurar(destino)
            if (valor <= 0 || conta.saldo < valor) {
                avisar("Saldo insuficiente ou valor inválido.")
            } else if (outraConta != null && outraConta != conta) {
                conta.saldo -= valor
                outraConta.saldo += valor
                conta.operacoes.add(Operacao(TipoOperacao.TRANSFERENCIA, valor, "Transferência para conta ${outraConta.numero}"))
                outraConta.operacoes.add(Operacao(TipoOperacao.TRANSFERENCIA, valor, "Recebimento da conta ${conta.numero}"))
                avisar("Transferência interna realizada.")
            } else {
                conta.saldo -= valor
                conta.operacoes.add(Operacao(TipoOperacao.TRANSFERENCIA, valor, "Transferência externa para $bancoSelecionado"))
                avisar("Transferência externa simulada.")
            }
        }) { Text("Enviar") }
    }
}

@Composable
// Renderiza um campo de valor reutilizável para depósito ou saque.
private fun Acao(nome: String, executar: (Double) -> Unit) {
    var texto by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().widthIn(min = 180.dp, max = 260.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(nome, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(texto, { texto = it }, label = { Text("Valor") })
        Button({ executar(texto.replace(',', '.').toDoubleOrNull() ?: 0.0) }) { Text("Confirmar") }
    }
}

@Composable
// Filtra e apresenta o histórico de operações por período.
private fun Extrato(conta: Conta) {
    var inicio by remember { mutableStateOf("") }
    var fim by remember { mutableStateOf("") }
    val dia = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val inicioData = runCatching { LocalDate.parse(inicio, dia).atStartOfDay() }.getOrNull()
    val fimData = runCatching { LocalDate.parse(fim, dia).plusDays(1).atStartOfDay() }.getOrNull()
    val filtradas = conta.operacoes.reversed().filter { operacao ->
        (inicioData == null || !operacao.data.isBefore(inicioData)) && (fimData == null || operacao.data.isBefore(fimData))
    }
    Text("Extrato", style = MaterialTheme.typography.headlineMedium)
    Text("Saldo atual: ${conta.saldo.emReais()}")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 560.dp) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(inicio, { inicio = it }, label = { Text("Início dd/mm/aaaa") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fim, { fim = it }, label = { Text("Fim dd/mm/aaaa") }, modifier = Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(inicio, { inicio = it }, label = { Text("Início dd/mm/aaaa") }, modifier = Modifier.weight(1f))
            OutlinedTextField(fim, { fim = it }, label = { Text("Fim dd/mm/aaaa") }, modifier = Modifier.weight(1f))
        }
    }
    if (filtradas.isEmpty()) Text("Nenhuma operação neste período.") else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { filtradas.forEach { Linha(it) } }
}

@Composable
// Exibe uma operação individual com descrição, data e valor.
private fun Linha(operacao: Operacao) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(operacao.descricao); Text(operacao.data.format(formato), style = MaterialTheme.typography.bodySmall) }
        Text(operacao.valor.emReais())
    }
}

@Composable
// Permite escolher um produto de investimento e mostra a estimativa anual.
private fun Investimentos(conta: Conta, avisar: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    var produto by remember { mutableStateOf("CDB - 10% ao ano") }
    var texto by remember { mutableStateOf("") }
    Text("Investimentos", style = MaterialTheme.typography.headlineMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val controles = @Composable {
            Column(Modifier.fillMaxWidth().widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ aberto = true }, Modifier.fillMaxWidth()) { Text(produto) }
                OutlinedTextField(texto, { texto = it }, label = { Text("Valor") }, modifier = Modifier.fillMaxWidth())
                Button({
                    val valor = texto.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (valor > 0 && conta.saldo >= valor) {
                        val taxa = if (produto.contains("10%")) .10 else if (produto.contains("6%")) .06 else .12
                        conta.saldo -= valor
                        conta.investimentos.add(Investimento(produto.substringBefore(" -"), taxa, valor))
                        conta.operacoes.add(Operacao(TipoOperacao.INVESTIMENTO, valor, "Aplicação em ${produto.substringBefore(" -")}"))
                        avisar("Investimento criado.")
                    } else avisar("Saldo insuficiente ou valor inválido.")
                }, Modifier.fillMaxWidth()) { Text("Aplicar") }
            }
        }
        if (maxWidth < 560.dp) controles() else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { controles() }
    }
    DropdownMenu(aberto, { aberto = false }) { listOf("CDB - 10% ao ano", "Poupança - 6% ao ano", "Fundo - 12% ao ano").forEach { item -> DropdownMenuItem({ Text(item) }, { produto = item; aberto = false }) } }
    conta.investimentos.forEach { app -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(app.produto); Text("Aplicado: ${app.valor.emReais()} | Taxa: ${(app.taxa * 100).toInt()}% ao ano"); Text("Estimativa em 1 ano: ${(app.valor * (1 + app.taxa)).emReais()}") } } }
}

@Composable
// Permite editar dados pessoais ou excluir a conta atual.
private fun Perfil(conta: Conta, sistema: Sistema, sair: () -> Unit, avisar: (String) -> Unit) {
    var nome by remember(conta) { mutableStateOf(conta.nome) }
    var telefone by remember(conta) { mutableStateOf(conta.telefone) }
    var email by remember(conta) { mutableStateOf(conta.email) }
    Text("Meus dados", style = MaterialTheme.typography.headlineMedium)
    Text("Documento: ${conta.documento} | Conta: ${conta.numero}")
    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(telefone, { telefone = it }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                try {
                    validarPerfil(nome, telefone, email)
                    conta.nome = nome.trim()
                    conta.telefone = telefone.trim()
                    conta.email = email.trim()
                    avisar("Dados salvos.")
                } catch (erro: IllegalArgumentException) {
                    avisar(erro.message ?: "Confira os dados.")
                }
            }) { Text("Salvar") }
            OutlinedButton({ sistema.excluir(conta); sair() }) { Text("Excluir conta") }
        }
    }
}

// Inicia a janela desktop e conecta o conteúdo principal ao ciclo de vida da aplicação.
fun main() = application { Window(onCloseRequest = ::exitApplication, title = "Minha Conta") { Aplicacao() } }
