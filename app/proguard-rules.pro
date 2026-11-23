# Com o R8 ligado (isMinifyEnabled). As entidades do Room ficam com os nomes
# intactos por segurança: os nomes das colunas vêm deles, e o Room e o CSV
# dependem disso. São meia dúzia de classes; não pesam no tamanho.
-keep class pt.jpduarte.obragastos.data.** { *; }
