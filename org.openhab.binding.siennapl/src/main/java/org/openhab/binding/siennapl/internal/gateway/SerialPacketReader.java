/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.gateway;

import static org.openhab.binding.internal.siennapl.BindingConstants.*;

import org.openhab.binding.siennapl.internal.packet.ExplicitPacket;
import org.openhab.binding.siennapl.internal.packet.NVPacket;
import org.openhab.binding.siennapl.internal.utils.ByteArray;
import org.openhab.binding.siennapl.internal.utils.CircularByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads data from the circular buffer, creates packet (Explicit/NV) when complete record received
 * Activates the packet handler (ExplicitPacket/NVPacket) to update the state
 *
 * @author s.simma - initial contribution
 *
 */

public class SerialPacketReader extends Thread {

    private Logger logger = LoggerFactory.getLogger(SerialPacketReader.class);

    private SerialConnector connector;
    private boolean execute;

    public SerialPacketReader(SerialConnector connector) {
        this.connector = connector;
    }

    public void stopReader() {
        this.execute = false;
    }

    public boolean isRunning() {
        return execute;
    }

    @Override
    public void run() {
        this.execute = true;

        CircularByteBuffer rBuffer = connector.getReceiveBuffer();

        /* create packet handler */
        ExplicitPacket expPacket = new ExplicitPacket(connector);
        NVPacket nvPacket = new NVPacket(connector);

        while (connector.isConnected() && execute) {
            /* Wait for previous package handeled */
            ByteArray msg = new ByteArray();
            byte data = connector.get(rBuffer);

            if (data == START_BYTE) {
                msg.addByte(data);

                /* Read Command code from receive buffer */
                data = connector.get(rBuffer);

                // We have to handle explicit packages or network variable (NV) packages. (depends on Bit8)
                if ((data & 0x80) == 0) {
                    // Handle explicit package
                    // Command
                    expPacket.setCommand(data);
                    msg.addByte(data);

                    /* Module Type or NeuronId[0] */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setModuleCode(data);

                    /* Software Type of the module or NeuronID[1] */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setSoftwCode(data);

                    /* State or NeuronId[2] */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setState(data);

                    /* NeuronId3 */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setNeuron3(data);

                    /* NeuronId4 */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setNeuron4(data);

                    /* State1 */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setState1(data);

                    /* G-Byte */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setGByte(data);

                    /* E-Byte */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setEByte(data);

                    /* State2 */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setState2(data);

                    /* Read CRC from receive buffer */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    expPacket.setCrc(data);

                    /* End of record byte */
                    data = connector.get(rBuffer);
                    msg.addByte(data);

                    logger.debug("Sienna: Received explicit package: {}", msg.toString());

                    /* Read last Byte -> Packet valid if last byte = ETX and lon-crc ok */
                    if ((data == STOP_BYTE) && (expPacket.isValid())) {
                        expPacket.handleCommand();
                    } else {
                        logger.error("Sienna: Received explicit package is NOT valid (CRC)");
                    }
                } else {
                    // Handle NV package
                    nvPacket.setCommand(data);
                    msg.addByte(data);

                    /* Group-Id from receive buffer */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    nvPacket.setGroup(data);

                    /* Element-Id from receive buffer */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    nvPacket.setElement(data);

                    /* Payload-Byte from receive buffer */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    nvPacket.setPayload(data);

                    /* Read 6 arbitrary bytes from receive buffer */
                    ByteArray arb = new ByteArray();
                    for (int i = 0; i < 6; i++) {
                        data = connector.get(rBuffer);
                        msg.addByte(data);
                        arb.addByte(data);
                    }
                    nvPacket.setArbitrary(arb);

                    /* Read CRC from receive buffer */
                    data = connector.get(rBuffer);
                    msg.addByte(data);
                    nvPacket.setCrc(data);

                    /* End of record byte */
                    data = connector.get(rBuffer);
                    msg.addByte(data);

                    logger.debug("Sienna: Received NV package: {}", msg.toString());

                    /* Read last Byte -> Packet valid if last byte = ETX and lon-crc ok */
                    if ((data == STOP_BYTE) && (nvPacket.isValid())) {
                        nvPacket.handleCommand();
                    } else {
                        logger.error("Sienna: Received NV package is NOT valid (CRC)");
                    }
                }
            }
        }

        this.execute = false;
        logger.warn("Sienna: SerialPacketReader: stopped");
    }

}
