package com.kriptoradar.alerts

import com.kriptoradar.ui.Format
import kotlin.math.abs
import kotlin.random.Random

/**
 * "Kanka modu": bildirimleri resmi dil yerine arkadaş ağzıyla yazar.
 * Her olay için birkaç kalıp vardır; aynı cümle sürekli tekrar etmesin diye rastgele seçilir.
 */
object Voice {
    private fun pick(vararg s: String) = s[Random.nextInt(s.size)]
    private fun p(v: Double) = "%" + Format.num(abs(v))

    fun rapidMove(base: String, up: Boolean, pct: Double, min: Int, price: String, ch24: Double): Pair<String, String> =
        if (up) pick(
            "🚀 Kanka $base uçuyo amk!",
            "🔥 Kanka bak, $base coştu!",
            "📈 Kanka $base kalktı, haberin olsun",
            "🚀 $base füze gibi kanka",
        ) to pick(
            "$min dakikada ${p(pct)} artmış, şu an $price. Kaçırma ama FOMO'ya da kapılma.",
            "$min dkda ${p(pct)} yukarı, fiyat $price. Elinde varsa keyfine bak 😎",
            "Son $min dakikada ${p(pct)} zıpladı amk, $price oldu. 24 saatte ${Format.signedPct(ch24)}.",
        )
        else pick(
            "🔻 Kanka $base batıyo amk!",
            "😱 Kanka çabuk bak, $base çakılıyo",
            "🩸 $base kan ağlıyo kanka",
            "⚠️ Kanka $base düşüyo, dikkat",
        ) to pick(
            "$min dakikada ${p(pct)} düştü, şu an $price. Elinde varsa stop'unu kontrol et, istersen çık.",
            "$min dkda ${p(pct)} aşağı amk, fiyat $price. Panikleme ama planına bak.",
            "Son $min dakikada ${p(pct)} çakıldı, $price oldu. 24 saatte ${Format.signedPct(ch24)}. Kendini koru kanka.",
        )

    fun priceAbove(base: String, target: String, price: String) =
        pick("🎯 Kanka $base hedefi patlattı!", "🔔 Kanka $base istediğin yere geldi") to
            pick("$target seviyesini geçti amk, şu an $price.", "Beklediğin $target seviyesi geçildi, fiyat $price. Ne yapacaksan şimdi.")

    fun priceBelow(base: String, target: String, price: String) =
        pick("🔔 Kanka $base dibe vurdu", "⚠️ Kanka $base senin seviyenin altına indi") to
            pick("$target seviyesinin altına düştü, şu an $price.", "Belirlediğin $target kırıldı amk, fiyat $price. Planına bak.")

    fun change24(base: String, up: Boolean, pct: Double, price: String) =
        (if (up) "📈 Kanka $base bugün coştu" else "📉 Kanka $base bugün yerlerde") to
            "24 saatte ${if (up) "+" else "-"}${p(pct)}, şu an $price."

    fun moveWindow(base: String, pct: Double, min: Int, price: String) =
        (if (pct > 0) "🚀 Kanka $base hareketlendi!" else "🔻 Kanka $base sert düştü!") to
            "$min dakikada ${Format.signedPct(pct)} oynadı amk, fiyat $price."

    fun reversal(base: String, drawdown: Double, bounce: Double, price: String) =
        pick("🔄 Kanka $base dipten dönüyo gibi", "👀 Kanka bi bak, $base toparlanıyo", "🔄 $base çok düşmüştü, kalkmaya başladı kanka") to
            pick(
                "Son 30 günün zirvesinden ${p(drawdown)} aşağıdaydı, dipten ${p(bounce)} toparladı. Şu an $price. Garantisi yok ama grafik yukarı dönüyo.",
                "Epey ucuzlamış (zirveden -${p(drawdown)}), şimdi dipten ${p(bounce)} kalkmış amk. Fiyat $price. Az parayla takip et istersen.",
            )

    fun signals(list: List<Triple<String, Int, Double>>): Pair<String, String> {
        val first = list.first().first
        val title = if (list.size == 1) pick("📊 Kanka $first artıyo gibi", "📈 Kanka $first grafiği güzel görünüyo")
        else pick("📊 Kanka ${list.size} coin yükselişte gibi", "📈 Kanka bak şunlar artıyo")
        val text = list.take(6).joinToString("\n") { (b, score, ch) -> "• $b – puan $score, 24s ${Format.signedPct(ch)}" } +
            "\nİstatistiğe göre, kesin değil kanka."
        return title to text
    }

    fun volumeSpike(base: String, ratio: Double, ch: Double) =
        pick("👀 Kanka $base tarafında bi şeyler oluyo", "📈 Kanka herkes $base almaya dalmış") to
            "Hacim normalin ${Format.num(ratio)} katı amk. 24 saatte ${Format.signedPct(ch)}. Bi göz at."

    fun paperExecuted(base: String, kind: com.kriptoradar.data.OrderKind, pnl: Double, price: String): Pair<String, String> = when (kind) {
        com.kriptoradar.data.OrderKind.LIMIT_BUY -> pick("🛒 Kanka sanal $base aldık", "🛒 Kanka emrin çalıştı, $base alındı") to "Fiyat $price TL'ye indi, emrin gerçekleşti."
        com.kriptoradar.data.OrderKind.STOP_LOSS -> pick("🛑 Kanka zarar-kes patladı", "🛑 Kanka $base satıldı, kaçtık") to
            "$base $price TL'den satıldı, ${Format.num(pnl)} TL. Daha kötüsünden kurtardı amk (sanal)."
        else -> (if (pnl >= 0) pick("🎯 Kanka kârı aldık!", "💰 Kanka $base sattık, para cepte") else "💱 Kanka $base satıldı") to
            "$base $price TL'den satıldı, ${if (pnl >= 0) "+" else ""}${Format.num(pnl)} TL (sanal)."
    }
}
