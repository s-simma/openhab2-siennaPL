/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.gateway;

import java.io.InputStream;

import org.openhab.binding.siennapl.internal.utils.CircularByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

/**
 * Event Handler for reading data from serial device
 *
 * @author s.simma - Initial contribution
 */

public class SerialReader implements SerialPortEventListener {

    private Logger logger = LoggerFactory.getLogger(SerialReader.class);
    private SerialConnector connector;
    private InputStream in;
    private CircularByteBuffer recBuffer;

    public SerialReader(SerialConnector connector, InputStream in, CircularByteBuffer RecBuffer) {
        this.in = in;
        this.recBuffer = RecBuffer;
    }

    @Override
    public void serialEvent(SerialPortEvent arg0) {
        int data;

        try {
            while ((data = in.read()) > -1) {
                recBuffer.put((byte) data);
            }
        } catch (Exception e) {
            logger.error("Sienna SerialReader: Error while reading from physical device. -> try to close connection");
            connector.disconnect();
        }
    }

}
