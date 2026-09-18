package com.ringosham.translationmod.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


//map lang codes used by Google and LibreTranslate onto the codes NLLB-200 needs (es -> spa_Latn).

public final class NllbCodes {

    private static final Map<String, String> MAP = new HashMap<String, String>();

    static {
        MAP.put("en", "eng_Latn"); MAP.put("es", "spa_Latn"); MAP.put("ru", "rus_Cyrl");
        MAP.put("pt", "por_Latn"); MAP.put("fr", "fra_Latn"); MAP.put("de", "deu_Latn");
        MAP.put("it", "ita_Latn"); MAP.put("nl", "nld_Latn"); MAP.put("pl", "pol_Latn");
        MAP.put("id", "ind_Latn"); MAP.put("ms", "zsm_Latn"); MAP.put("tl", "tgl_Latn");
        MAP.put("hi", "hin_Deva"); MAP.put("ro", "ron_Latn"); MAP.put("sk", "slk_Latn");
        MAP.put("sl", "slv_Latn"); MAP.put("cs", "ces_Latn"); MAP.put("fi", "fin_Latn");
        MAP.put("sv", "swe_Latn"); MAP.put("da", "dan_Latn"); MAP.put("nb", "nob_Latn");
        MAP.put("no", "nob_Latn"); MAP.put("uk", "ukr_Cyrl"); MAP.put("bg", "bul_Cyrl");
        MAP.put("tr", "tur_Latn"); MAP.put("el", "ell_Grek"); MAP.put("hu", "hun_Latn");
        MAP.put("he", "heb_Hebr"); MAP.put("iw", "heb_Hebr"); MAP.put("ar", "arb_Arab");
        MAP.put("fa", "pes_Arab"); MAP.put("th", "tha_Thai"); MAP.put("vi", "vie_Latn");
        MAP.put("ja", "jpn_Jpan"); MAP.put("ko", "kor_Hang");
        MAP.put("zh", "zho_Hans"); MAP.put("zh-cn", "zho_Hans"); MAP.put("zh-hans", "zho_Hans");
        MAP.put("zh-tw", "zho_Hant"); MAP.put("zh-hant", "zho_Hant");
    }

    private NllbCodes() {
    }

    //the NLLB code for an ISO code, or null when there is no mapping
    public static String of(String isoCode) {
        if (isoCode == null) {
            return null;
        }
        return MAP.get(isoCode.trim().toLowerCase(Locale.ROOT));
    }
    public static boolean isKnown(String isoCode) {
        return of(isoCode) != null;
    }
}
