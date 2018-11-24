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

import java.io.DataOutputStream;
import java.io.IOException;

import org.openhab.binding.siennapl.internal.handler.BridgeHandler;
import org.openhab.binding.siennapl.internal.utils.ByteArray;
import org.openhab.binding.siennapl.internal.utils.CircularByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event Handler for writing data to serial device
 *
 * @author s.simma - Initial contribution
 */
public class SerialWriter extends Thread {

    public static final int MAXWAITCOUNT = 5;
    public static final int MAXRETRYCOUNT = 2;
    private Logger logger = LoggerFactory.getLogger(SerialWriter.class);
    private SerialConnector connector;
    private BridgeHandler bridgeHandler;
    private CircularByteBuffer transBuffer;
    DataOutputStream out;
    private byte waitCommand;
    private byte waitCommand_rec;
    private char waitGroup;
    private int waitElement;
    private int waitElementOffs;
    private int waitCount = 0;
    private int retCount = 0;
    private boolean answerOk = false;
    private boolean execute;

    public SerialWriter(SerialConnector connector, CircularByteBuffer t_buffer, DataOutputStream out,
            CircularByteBuffer r_buffer) {
        this.connector = connector;
        this.bridgeHandler = (BridgeHandler) connector.getBridge().getHandler();
        this.transBuffer = t_buffer;
        this.out = out;
    }

    public void stopWriter() {
        this.execute = false;
    }

    public boolean isRunning() {
        return this.execute;
    }

    @Override
    public void run() {
        this.execute = true;
        retCount = 0;

        while (execute) {
            try {
                byte data = connector.get(transBuffer);
                if (!execute) {
                    break;
                }
                if (data == START_BYTE) {
                    // Mark position of first byte of record in case it has to be resent
                    connector.mark(transBuffer);

                    ByteArray msg = new ByteArray();

                    // Start Byte
                    msg.addByte(data);

                    // Remaining
                    for (int i = 1; i < REC_LENGHT; i++) {
                        data = connector.get(transBuffer);
                        msg.addByte(data);
                    }

                    byte[] thisRecord = msg.getArray();

                    // The 1. byte after the stop byte is the expected answer for this command or 0
                    waitCommand = connector.get(transBuffer);
                    // The 2. byte after the stop byte is the element Id offset 0/1
                    waitElementOffs = connector.get(transBuffer);

                    // If send command is NV-command byte 2=group, byte 3=element)
                    if ((waitCommand != 0) && ((thisRecord[1] & 0x80) != 0)) {
                        waitGroup = (char) (byte) (thisRecord[2] + 0x40);
                        waitElement = thisRecord[3] - waitElementOffs;
                        bridgeHandler.resDeviceAnswCommand(("" + waitGroup), waitElement, waitElementOffs);
                    } else {
                        // Explecit commands are not handled at the moment
                        waitGroup = ' ';
                        waitElement = 0;
                        waitCommand = 0;
                    }

                    logger.debug("Sienna: Sent to bridge: {}", msg.toString());
                    connector.setSerialError(false);
                    out.write(thisRecord);
                    out.flush();

                    // Wait max. 1,5 sec for answer if we have to wait for an answer
                    try {
                        waitCount = 0;
                        answerOk = false;
                        while (waitCount < MAXWAITCOUNT) {
                            Thread.sleep(300);
                            // Repeat last command if serial error received
                            if (connector.getSerialError()) {
                                answerOk = false;
                                break;
                            }
                            // Test wait for correct answer for sent record
                            if (waitCommand != 0) {
                                waitCommand_rec = bridgeHandler.getDeviceAnswCommand(("" + waitGroup), waitElement,
                                        waitElementOffs);
                                if (waitCommand_rec == waitCommand) {
                                    // We have received the correct answer -> continue with next record
                                    answerOk = true;
                                    break;
                                }
                                // Continue waiting for correct answer
                            } else {
                                // We dont need to wait for answer from device -> continue with next record after delay
                                Thread.sleep(300);
                                answerOk = true;
                                break;
                            }
                            waitCount = waitCount + 1;
                        }

                        if (answerOk) {
                            retCount = 0;
                        } else {
                            if ((waitCount >= MAXWAITCOUNT) && (retCount >= MAXRETRYCOUNT)) {
                                logger.error("Sienna: Serial Error / No / Wrong answer received for last record");
                                retCount = 0;
                            } else {
                                logger.warn("Sienna: Resend last record............");
                                retCount = retCount + 1;
                                connector.reset(transBuffer);
                            }
                        }

                        Thread.sleep(100);
                    } catch (Exception e) {
                        logger.error("Sienna SerialWriter/run(): Error while sleep(200). Stopping", e);
                        connector.disconnect();
                    }
                }
            } catch (IOException e) {
                logger.error(
                        "Sienna SerialWriter/run(): Error while writing to physical device.  -> try to close connection");
                connector.disconnect();
            }
        }
        logger.warn("Sienna: SerialWriter: Stopped");
        this.execute = false;
    }

}
