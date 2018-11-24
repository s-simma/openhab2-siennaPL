/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.handler;

import org.openhab.binding.internal.siennapl.DeviceConfiguration;

/**
 * Interface to Device Handler
 *
 * @author s.simma - Initial contribution
 */
public interface IDeviceHandler {

    public void onDevice1OnOffChanged(int command, char cgroup, int element);

    public void onDeviceNchChanged(int command, char cgroup, int element, int offset);

    public void onDeviceDimmerChanged(int command, char cgroup, int element, double value);

    public void onDeviceUpDownChanged(int command, char cgroup, int element, double value);

    public void onDeviceEnoContactChanged(int command, char cgroup, int element);

    public void onDeviceEnoMotionChanged(int command, char cgroup, int element);

    public void onDeviceEnoTempChanged(int command, char cgroup, int element, double value);

    // Set Device Status Offline
    public void setThingOffline();

    // Set Device Status Online
    public void setThingOnline();

    // Get Device configuration for the device
    public DeviceConfiguration getDeviceConfiguration();

}
