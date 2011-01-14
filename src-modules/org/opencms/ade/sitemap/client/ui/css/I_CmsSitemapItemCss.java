/*
 * File   : $Source: /alkacon/cvs/opencms/src-modules/org/opencms/ade/sitemap/client/ui/css/Attic/I_CmsSitemapItemCss.java,v $
 * Date   : $Date: 2011/01/14 14:19:54 $
 * Version: $Revision: 1.13 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2009 Alkacon Software (http://www.alkacon.com)
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

package org.opencms.ade.sitemap.client.ui.css;

import com.google.gwt.resources.client.CssResource;

/**
 * The CSS bundle for sitemap items.<p>
 * 
 * @author Georg Westenberger
 * 
 * @version $Revision: 1.13 $
 * 
 * @since 8.0.0
 */

public interface I_CmsSitemapItemCss extends CssResource {

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String brokenLink();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String contentHide();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String deletedEntryLabel();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String highlight();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String lockClosed();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String lockIcon();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String lockOpen();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String lockSharedClosed();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String lockSharedOpen();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String marker();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String markUnchanged();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     **/
    String positionIndicator();

    /**
     * CSS class accessor.<p>
     * 
     * @return a CSS class
     */
    String sitemapEntryDecoration();

}
