/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author s.simma - initial contribution
 *
 */
public class CircularByteBuffer {

    private Logger logger = LoggerFactory.getLogger(CircularByteBuffer.class);

    private static final int WAIT_MS = 10;

    private int readPos = 0;

    private int writePos = 0;

    private int currentSize = 0;

    private int markedPos = 0;

    private byte[] buffer;

    private boolean running = true;

    public CircularByteBuffer(int size) {
        buffer = new byte[size];
    }

    public byte get() {
        waitForData();
        byte result;
        synchronized (buffer) {
            result = buffer[readPos];
            currentSize--;
            readPos++;
            if (readPos >= buffer.length) {
                readPos = 0;
            }
        }
        return result;
    }

    private void waitForData() {
        while (isEmpty() && running) {
            try {
                Thread.sleep(WAIT_MS);
            } catch (Exception e) {
                logger.error("Error while waiting for new data", e);
            }
        }
    }

    public short getShort() {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(get());
        bb.put(get());
        return bb.getShort(0);
    }

    public void get(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = get();
        }
    }

    public void put(byte b) {
        synchronized (buffer) {
            buffer[writePos] = b;
            writePos++;
            currentSize++;
            if (writePos >= buffer.length) {
                writePos = 0;
            }
        }
    }

    public void mark() {
        if (readPos != 0) {
            markedPos = readPos - 1;
        } else {
            markedPos = buffer.length - 1;
        }
    }

    public void reset() {
        if (markedPos <= readPos) {
            currentSize += Math.abs(readPos - markedPos);
        } else {
            currentSize += readPos + Math.abs(buffer.length - markedPos);
        }
        readPos = markedPos;
    }

    public void stop() {
        running = false;
    }

    private boolean isEmpty() {
        return currentSize <= 0;
    }

}
