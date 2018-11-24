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
public class ByteBitSet {

    private byte data = 0;

    public ByteBitSet() {
    }

    public ByteBitSet(byte data) {
        this.data = data;
    }

    public void setBit(int pos, boolean bit) {
        data = Bits.setBit(data, pos, bit);
    }

    public byte getByte() {
        return data;
    }
}
