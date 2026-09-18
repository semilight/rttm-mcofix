/*
 * Copyright (C) 2021 Ringosham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ringosham.translationmod.common;

import java.util.Collection;
import java.util.Locale;

//decides if a language is one you already understand
public final class LanguageFilter {
    private LanguageFilter() {
    }

    public static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    //the bit of a language tag before the region, so "zh" also covers "zh-CN"
    public static String primarySubtag(String code) {
        String normalised = normalise(code);
        for (int i = 0; i < normalised.length(); i++) {
            char c = normalised.charAt(i);
            if (c == '-' || c == '_') {
                return normalised.substring(0, i);
            }
        }
        return normalised;
    }

    //matches either the code or the english name
    public static boolean isBlocked(Collection<String> blocked, String isoCode, String languageName) {
        if (blocked == null || blocked.isEmpty()) {
            return false;
        }
        String code = primarySubtag(isoCode);
        String name = normalise(languageName);
        //nothing to go on, so dont block it.
        if (code.isEmpty() && name.isEmpty()) {
            return false;
        }
        for (String raw : blocked) {
            String entry = normalise(raw);
            if (entry.isEmpty()) {
                continue;
            }
            if (!code.isEmpty() && primarySubtag(entry).equals(code)) {
                return true;
            }
            if (!name.isEmpty() && entry.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
