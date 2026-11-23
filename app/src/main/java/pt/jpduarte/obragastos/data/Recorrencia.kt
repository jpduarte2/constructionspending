package pt.jpduarte.obragastos.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/*
 * As contas das despesas recorrentes: em que dia cai cada débito, e que
 * despesas ainda faltam lançar. Funções puras, testadas sem Android.
 *
 * Uma recorrente repete-se de [DespesaFixa.intervalo] em intervalo
 * [DespesaFixa.unidade] (dias, semanas, meses ou anos) a partir de
 * [DespesaFixa.inicioEpochDay]. A ocorrência k (0 = a despesa que a criou) tem
 * data calculada sempre a partir do início, e não da anterior — assim um débito
 * ao dia 31 continua ao dia 31 depois de passar por fevereiro.
 */

const val UNIDADE_DIA = "dia"
const val UNIDADE_SEMANA = "semana"
const val UNIDADE_MES = "mes"
const val UNIDADE_ANO = "ano"

/** Um mês como número (ano × 12 + mês − 1). Só para ler ficheiros CSV da versão 3. */
fun YearMonth.indiceMes(): Int = year * 12 + monthValue - 1

fun mesDoIndice(indice: Int): YearMonth = YearMonth.of(Math.floorDiv(indice, 12), Math.floorMod(indice, 12) + 1)

/** Sábado e domingo passam para a segunda-feira seguinte — é o que os bancos fazem. */
fun diaUtil(d: LocalDate): LocalDate = when (d.dayOfWeek) {
    DayOfWeek.SATURDAY -> d.plusDays(2)
    DayOfWeek.SUNDAY -> d.plusDays(1)
    else -> d
}

/**
 * O dia do débito num mês: o [dia] pedido (ou o último do mês, se o mês for
 * mais curto), passado para segunda-feira se calhar ao fim de semana.
 */
fun dataDoDebito(mes: YearMonth, dia: Int): LocalDate = diaUtil(mes.atDay(dia.coerceIn(1, mes.lengthOfMonth())))

private val DespesaFixa.porMeses: Boolean get() = unidade == UNIDADE_MES || unidade == UNIDADE_ANO

/** A data da ocorrência [k] tal como a regra a dá, antes de fugir ao fim de semana. */
fun DespesaFixa.dataNominal(k: Int): LocalDate {
    val inicio = LocalDate.ofEpochDay(inicioEpochDay)
    return when (unidade) {
        UNIDADE_DIA -> inicio.plusDays(k.toLong() * intervalo)
        UNIDADE_SEMANA -> inicio.plusWeeks(k.toLong() * intervalo)
        else -> {
            val meses = k.toLong() * intervalo * (if (unidade == UNIDADE_ANO) 12 else 1)
            val mes = YearMonth.from(inicio).plusMonths(meses)
            mes.atDay(dia.coerceIn(1, mes.lengthOfMonth()))
        }
    }
}

/**
 * A data do débito da ocorrência [k]. Só as repetições mensais e anuais fogem
 * ao fim de semana: numa diária ou semanal o dia foi escolhido de propósito, e
 * "todos os dias" acumularia três débitos à segunda-feira.
 */
fun DespesaFixa.dataDaOcorrencia(k: Int): LocalDate =
    dataNominal(k).let { if (porMeses) diaUtil(it) else it }

/** O que falta lançar de uma recorrente, e como fica depois disso. */
data class Lancamento(val despesas: List<Despesa>, val proximo: Int, val lancadas: Int)

/** Limite de segurança por chamada; o resto lança-se na seguinte. */
private const val MAX_POR_VEZ = 120

/**
 * As despesas que a recorrente já devia ter lançado até [hoje] e ainda não
 * lançou: a partir da ocorrência [DespesaFixa.proximo], enquanto a data já
 * tiver passado, não for depois do fim e não se tiver chegado ao número máximo
 * de vezes.
 *
 * Numa recorrente de valor variável a despesa entra com o último valor
 * conhecido e marcada "a confirmar".
 */
fun lancamentosEmFalta(f: DespesaFixa, hoje: LocalDate): Lancamento {
    if (!f.ativa) return Lancamento(emptyList(), f.proximo, f.lancadas)
    val novas = mutableListOf<Despesa>()
    var k = f.proximo
    while (novas.size < MAX_POR_VEZ) {
        if (f.maxOcorrencias != null && f.lancadas + novas.size >= f.maxOcorrencias) break
        val d = f.dataDaOcorrencia(k)
        if (d.isAfter(hoje)) break
        if (f.fimEpochDay != null && d.toEpochDay() > f.fimEpochDay) break
        novas += Despesa(
            rubrica = f.rubrica,
            descricao = f.descricao,
            dataEpochDay = d.toEpochDay(),
            valorCent = f.valorCent,
            capitalCent = f.capitalCent,
            fixaId = f.id,
            aConfirmar = f.valorVariavel,
        )
        k++
    }
    return Lancamento(novas, k, f.lancadas + novas.size)
}

/** O próximo débito ainda por lançar, ou null se estiver em pausa ou já tiver terminado. */
fun proximoDebito(f: DespesaFixa): LocalDate? {
    if (!f.ativa) return null
    if (f.maxOcorrencias != null && f.lancadas >= f.maxOcorrencias) return null
    val d = f.dataDaOcorrencia(f.proximo)
    return if (f.fimEpochDay != null && d.toEpochDay() > f.fimEpochDay) null else d
}

/**
 * Ao retomar uma recorrente em pausa, salta os débitos que passaram durante a
 * pausa: devolve a primeira ocorrência que ainda não venceu.
 */
fun ocorrenciaAoRetomar(f: DespesaFixa, hoje: LocalDate): Int {
    var k = f.proximo
    var guarda = 0
    while (f.dataDaOcorrencia(k).isBefore(hoje) && guarda++ < 100_000) k++
    return k
}

/**
 * A mesma recorrente com outra regra de repetição. Recomeça a contar a partir
 * do próximo débito que estava previsto, para a regra nova não reinterpretar
 * as ocorrências já lançadas; o número de vezes já lançadas mantém-se (conta
 * para o "termina depois de N vezes").
 */
fun DespesaFixa.comRegra(unidade: String, intervalo: Int, dia: Int): DespesaFixa {
    if (unidade == this.unidade && intervalo == this.intervalo && dia == this.dia) return this
    return copy(
        unidade = unidade,
        intervalo = intervalo,
        dia = dia,
        inicioEpochDay = dataNominal(proximo).toEpochDay(),
        proximo = 0,
    )
}

/**
 * Como uma despesa se repete e quando acaba — o que o editor de repetição
 * produz. [dia] só conta nas repetições mensais e anuais.
 */
data class Regra(
    val unidade: String = UNIDADE_MES,
    val intervalo: Int = 1,
    val dia: Int,
    val fim: LocalDate? = null,
    val maxVezes: Int? = null,
)

fun DespesaFixa.regra(): Regra = Regra(
    unidade = unidade,
    intervalo = intervalo,
    dia = dia,
    fim = fimEpochDay?.let { LocalDate.ofEpochDay(it) },
    maxVezes = maxOcorrencias,
)
