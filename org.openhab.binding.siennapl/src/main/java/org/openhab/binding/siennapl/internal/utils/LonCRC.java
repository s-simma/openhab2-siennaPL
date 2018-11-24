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
public class LonCRC {

    public LonCRC() {
    }

    public byte calcLonCRC(byte[] b) {
        int crc = 0x0;
        for (int j = 0; j < b.length; j++) {
            int inbyte = b[j];

            for (int k = 0; k < 8; k++) {
                int mix = (crc & 0x01) ^ (inbyte & 0x01);
                crc >>= 1;
                if (mix > 0) {
                    crc ^= 0x8c;
                }
                inbyte >>= 1;
            }
        }
        return (byte) crc;
    }
}
