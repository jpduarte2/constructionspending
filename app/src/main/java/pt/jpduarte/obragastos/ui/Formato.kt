package pt.jpduarte.obragastos.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT: Locale = Locale.forLanguageTag("pt-PT")

private val moeda: NumberFormat = NumberFormat.getCurrencyInstance(PT).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 2
}

private val moedaRedonda: NumberFormat = NumberFormat.getCurrencyInstance(PT).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}

private val numero: NumberFormat = NumberFormat.getNumberInstance(PT).apply {
    maximumFractionDigits = 0
}

/** 1234.5 -> "1 234,50 €" */
fun Double.eur(): String = moeda.format(this)

/** 1234.5 -> "1 235 €" — para eixos e rótulos onde os cêntimos são ruído. */
fun Double.eurCurto(): String = moedaRedonda.format(this)

/** Compacta valores grandes em eixos: 12500 -> "12,5 mil €". */
fun Double.eurEixo(): String = when {
    this >= 1_000_000 -> String.format(PT, "%.1fM €", this / 1_000_000)
    this >= 10_000 -> "${numero.format(this / 1000)} mil €"
    else -> moedaRedonda.format(this)
}

fun Double.pct(decimais: Int = 0): String = String.format(PT, "%.${decimais}f%%", this)

/** "1 pagamento" / "4 pagamentos" — evita o "pagamento(s)". */
fun plural(n: Int, singular: String, plural: String): String =
    if (n == 1) "$n $singular" else "$n $plural"

/** Remove o ",0" das taxas inteiras: 23.0 -> "23", 13.5 -> "13,5". */
fun Double.taxaTexto(): String =
    if (this % 1.0 == 0.0) this.toInt().toString() else String.format(PT, "%.1f", this)

private val diaMesAno = DateTimeFormatter.ofPattern("d MMM yyyy", PT)
private val diaMes = DateTimeFormatter.ofPattern("d MMM", PT)
private val mesAnoLongo = DateTimeFormatter.ofPattern("MMMM yyyy", PT)
private val mesCurto = DateTimeFormatter.ofPattern("MMM", PT)

fun LocalDate.texto(): String = format(diaMesAno)
fun LocalDate.textoCurto(): String = format(diaMes)

fun YearMonth.texto(): String = atDay(1).format(mesAnoLongo).replaceFirstChar { it.uppercase() }
fun YearMonth.textoCurto(): String = atDay(1).format(mesCurto).replaceFirstChar { it.uppercase() }
