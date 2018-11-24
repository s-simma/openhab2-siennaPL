/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.internal.siennapl;

/**
 * Configuration class for the Sienna RS232 Serial interface bridge, used to connect to the Sienna system.
 *
 * @author s.simma - Initial contribution
 */

public class BridgeConfiguration {

    // Sienna Bridge Thing constants
    public static final String SERIAL_PORT = "serialPort";
    public static final String BAUD = "baud";
    public static final String POLL_INTERVAL = "pollingInterval";
    public static final String NEURON_ID = "neuronId";

    /**
     * Sienna port name for a serial connection. Valid values are e.g. COM1 for Windows and /dev/ttyS0 or /dev/ttyUSB0
     * for Linux.
     */
    public String serialPort;

    /**
     * Sienna baud rate for serial connections. Valid values are 9600 (default), 19200, 38400, 57600, and 115200.
     */
    public Integer baud;

    /**
     * The Sienna Poll Period. Can be set in range 1-15 minutes. Default is 1 minute;
     */
    public Integer pollingInterval;

    /**
     * The Sienna Neuron-ID of the Bridge;
     */
    public String neuronId;

}
