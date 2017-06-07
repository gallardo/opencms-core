/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH & Co. KG (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui.shared.components;

import com.vaadin.shared.AbstractComponentState;

/**
 * The max height shared state.<p>
 */
public class CmsMaxHeightState extends AbstractComponentState {

    /** The serial version id. */
    private static final long serialVersionUID = 2763461561746931699L;

    /** The max height. */
    private int m_maxHeight;

    /**
     * Returns the max height.<p>
     *
     * @return the max height
     */
    public int getMaxHeight() {

        return m_maxHeight;
    }

    /**
     * Sets the max height.<p>
     *
     * @param maxHeight the max height
     */
    public void setMaxHeight(int maxHeight) {

        m_maxHeight = maxHeight;
    }
}
