/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.utils;

/**
 *
 * @author s.simma - initial contribution
 *
 */
public class ByteStringConverter {

    static final String HEXES = "0123456789ABCDEF";

    public ByteStringConverter() {
    }

    public String byteArrayToString(byte[] thisArray) {
        StringBuilder sb = new StringBuilder(thisArray.length * 2);
        for (byte b : thisArray) {
            int i = unsignedByte(b);
            sb.append(HEXES.charAt((i & 0xF0) >> 4)).append(HEXES.charAt((i & 0x0F)));
        }
        return sb.toString();
    }

    // If String-length not even -> return = empty byte[]
    public byte[] stringToByteArray(String thisString) {
        int p = 0;
        int j1 = 0;
        int j2 = 0;
        ByteArray ba = new ByteArray();

        int strlen = thisString.length();
        if (strlen % 2 == 0) {
            strlen = strlen / 2;
            for (int i = 0; i < strlen; i++) {
                j1 = Character.getNumericValue(thisString.charAt(p));
                j2 = Character.getNumericValue(thisString.charAt(p + 1));
                j1 = j1 << 4;
                j1 = j1 + j2;
                ba.addByte((byte) j1);
                p = p + 2;
            }
        }
        return ba.getArray();
    }

    private int unsignedByte(byte b) {
        int unsbyte = b;
        if (unsbyte < 0) {
            unsbyte = 128 + (128 - Math.abs(unsbyte));
            return unsbyte;
        } else {
            return unsbyte;
        }
    }

}
