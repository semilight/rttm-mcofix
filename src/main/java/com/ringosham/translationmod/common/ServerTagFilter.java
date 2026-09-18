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

//spots chat that is already translated so we don't do it twice.
//both the message going out and the translation coming back are tagged, THANK you slime
public final class ServerTagFilter {
    private ServerTagFilter() {
    }

    //ignores case and leading spaces
    public static boolean isServerTranslated(String body, Collection<String> prefixes) {
        if (body == null || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        String trimmed = body.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String raw : prefixes) {
            if (raw == null) {
                continue;
            }
            String prefix = raw.trim().toLowerCase(Locale.ROOT);
            //an empty entry would match everything and silence all chat
            if (prefix.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
