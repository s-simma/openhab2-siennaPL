/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.gateway;

import org.eclipse.smarthome.core.thing.Bridge;
import org.openhab.binding.siennapl.internal.utils.CircularByteBuffer;

/**
 * Interfcace to SerialConnector
 *
 * @author s.simma - Initial contribution
 */
public interface ISerialConnector {

    /* provide receive buffer */
    CircularByteBuffer getReceiveBuffer();

    /* provide transmit buffer */
    CircularByteBuffer getTransmitBuffer();

    /* provide bridge to NVPacket handler, ExplicitPacket handler */
    Bridge getBridge();

    /* provide bridge to NVPacket handler, ExplicitPacket handler */
    String getBridgeNeuronId();

    /* Test if connection to bridge established */
    boolean isConnected();

    /* connect to sienna bridge */
    void connect();

    /* disconnect from sienna bridge */
    void disconnect();

    /* Get serial Status */
    boolean getSerialError();

    /* Set serial Status */
    void setSerialError(boolean status);

    /* read 1 byte from the circular read-buffer - wait if no data available */
    byte get(CircularByteBuffer buffer);

    /* read short from the circular read-buffer - wait if no data available */
    short getShort(CircularByteBuffer buffer);

    /* read all available data from the circular read-buffer - wait if no data available */
    void get(CircularByteBuffer buffer, byte[] data);

    /* save actual write pointer (for retransmission) used together with "reset" */
    void mark(CircularByteBuffer buffer);

    /* set write pointer to "marked" position */
    void reset(CircularByteBuffer buffer);

    /* send data to bridge (write buffer) */
    void write(byte[] data);

}
