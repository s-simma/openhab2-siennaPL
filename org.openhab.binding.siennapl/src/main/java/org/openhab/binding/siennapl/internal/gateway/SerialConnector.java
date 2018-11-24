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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import org.eclipse.smarthome.core.thing.Bridge;
import org.openhab.binding.siennapl.internal.utils.CircularByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

/**
 * Handles the serial connection to the Sienna-Powerline Bridge,
 * create circular read and write buffer,
 * create the Event-Handler to receive data (SerialReader)
 * create the Event-HAndler to send data (SerialWriter)
 * provide methods to send data to the bridge (to circular buffer),
 * provide method to read data from the bridge (from circular buffer),
 * provide methods to connect/disconnect from bridge
 *
 *
 * @author s.simma - initial contribution
 *
 */
public class SerialConnector implements ISerialConnector {

    private Logger logger = LoggerFactory.getLogger(SerialConnector.class);

    private Bridge bridge;
    private String deviceName;
    private int deviceBaudrate;
    private String neuronId;

    private CommPort commPort = null;
    private InputStream in;
    private DataOutputStream out;
    private CircularByteBuffer recBuffer;
    private CircularByteBuffer transBuffer;
    private SerialWriter serialwriter;

    private boolean connected = false;
    private boolean serialError = false;

    /* Initialize */
    public SerialConnector(Bridge bridge, String device, int baudRate, String neuronId) {
        this.bridge = bridge;
        this.deviceName = device;
        this.deviceBaudrate = baudRate;
        this.neuronId = neuronId;
    }

    @Override
    public synchronized void connect() {
        if (isConnected()) {
            logger.debug("Sienna SerialConnector/connect(): Sienna bridge is already connected ");
            return;
        }

        try {
            // Search for valid serial ports
            Enumeration<?> ports = null;
            ports = CommPortIdentifier.getPortIdentifiers();
            boolean portFound = false;
            while (ports.hasMoreElements()) {
                CommPortIdentifier curPort = (CommPortIdentifier) ports.nextElement();
                if (curPort.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                    if (deviceName.equals(curPort.getName())) {
                        portFound = true;
                        break;
                    }
                }
            }

            if (!portFound) {
                logger.debug("Sienna: Serial port {} is not available in your system", deviceName);
                this.connected = false;
                return;
            }
            CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(deviceName);

            commPort = portIdentifier.open(this.getClass().getName(), 5000);

            SerialPort serialPort = (SerialPort) commPort;
            serialPort.setSerialPortParams(deviceBaudrate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            serialPort.enableReceiveThreshold(1);
            serialPort.disableReceiveTimeout();

            in = serialPort.getInputStream();
            out = new DataOutputStream(serialPort.getOutputStream());
            out.flush();

            recBuffer = new CircularByteBuffer(BUFFER_SIZE);
            transBuffer = new CircularByteBuffer(BUFFER_SIZE);

            // Receive data handler
            serialPort.addEventListener(new SerialReader(this, in, recBuffer));
            serialPort.notifyOnDataAvailable(true);

            // Transmit data handler
            serialwriter = new SerialWriter(this, transBuffer, out, recBuffer);
            serialwriter.start();

            this.connected = true;
        } catch (NoSuchPortException noSuchPortException) {
            logger.error("Sienna SerialConnector/connect(): No Such Port Exception: ", noSuchPortException);
        } catch (PortInUseException portInUseException) {
            logger.error("Sienna SerialConnector/connect(): Port in Use Exception: ", portInUseException);
        } catch (UnsupportedCommOperationException unsupportedCommOperationException) {
            logger.error("Sienna SerialConnector/connect(): Unsupported Comm Operation Exception: ",
                    unsupportedCommOperationException);
        } catch (UnsupportedEncodingException unsupportedEncodingException) {
            logger.error("Sienna SerialConnector/connect(): Unsupported Encoding Exception: ",
                    unsupportedEncodingException);
        } catch (IOException ioException) {
            logger.error("Sienna SerialConnector/connect(): IO Exception: ", ioException);
        } catch (TooManyListenersException tooManyListenersException) {
            logger.error("Sienna SerialConnector/connect(): Too Many Listeners Exception: ", tooManyListenersException);
        }

        if (isConnected()) {
            logger.info("Sienna: Sienna Bridge connected");
            logger.info("Sienna: Serial Port: {}", deviceName);
            logger.info("Sienna: Baud:        {}", deviceBaudrate);
        }
    }

    @Override
    public void disconnect() {
        logger.warn("Sienna: disconnect(): Close serial connection to bridge");

        try {
            recBuffer.stop();
            transBuffer.stop();
            serialwriter.stopWriter();
            Thread.sleep(100);
        } catch (Exception e) {
        }

        if (this.commPort == null) {
            this.connected = false;
            return;
        }
        SerialPort serialPort = (SerialPort) this.commPort;

        if (serialPort != null) {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                logger.error("Sienna; disconnect(): Could not close in/out buffer", e);
            }
            logger.warn("Sienna: disconnect(): in/out closed");

            logger.warn("Sienna: disconnect(): Try to close serial port .....");
            serialPort.removeEventListener();
            serialPort.close();

            this.commPort = null;
            in = null;
            out = null;
            this.connected = false;
        }
        logger.warn("Sienna: disconnect(): Serial port successfully closed");
    }

    @Override
    public Bridge getBridge() {
        return bridge;
    }

    @Override
    public String getBridgeNeuronId() {
        return neuronId;
    }

    @Override
    public CircularByteBuffer getReceiveBuffer() {
        return this.recBuffer;
    }

    @Override
    public CircularByteBuffer getTransmitBuffer() {
        return this.transBuffer;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean getSerialError() {
        return serialError;
    }

    @Override
    public void setSerialError(boolean status) {
        serialError = status;
    }

    @Override
    public byte get(CircularByteBuffer buffer) {
        return buffer.get();
    }

    @Override
    public short getShort(CircularByteBuffer buffer) {
        return buffer.getShort();
    }

    @Override
    public void get(CircularByteBuffer buffer, byte[] data) {
        buffer.get(data);
    }

    @Override
    public void mark(CircularByteBuffer buffer) {
        buffer.mark();
    }

    @Override
    public void reset(CircularByteBuffer buffer) {
        buffer.reset();
    }

    @Override
    /* We have to insert record + 2 byte (answer answer + elementId offset */
    public void write(byte[] data) {
        if (data.length == REC_LENGHT + 2) {
            for (int i = 0; i < REC_LENGHT + 2; i++) {
                transBuffer.put(data[i]);
            }
        } else {
            logger.error("Sienna SerialConnector/write(): Record size is not required size (12+1 Bytes)");
        }
    }

}
