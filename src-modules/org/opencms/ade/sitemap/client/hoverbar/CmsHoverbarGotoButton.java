/*
 * File   : $Source: /alkacon/cvs/opencms/src-modules/org/opencms/ade/sitemap/client/hoverbar/Attic/CmsHoverbarGotoButton.java,v $
 * Date   : $Date: 2011/01/14 14:19:54 $
 * Version: $Revision: 1.5 $
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

package org.opencms.ade.sitemap.client.hoverbar;

import org.opencms.ade.sitemap.client.Messages;
import org.opencms.ade.sitemap.client.ui.css.I_CmsImageBundle;
import org.opencms.gwt.client.ui.CmsPushButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;

/**
 * Sitemap hoverbar "go to page" button.<p>
 * 
 * @author Michael Moossen
 * 
 * @version $Revision: 1.5 $ 
 * 
 * @since 8.0.0
 */
public class CmsHoverbarGotoButton extends CmsPushButton {

    /**
     * Constructor.<p>
     * 
     * @param hoverbar the hoverbar 
     */
    public CmsHoverbarGotoButton(final CmsSitemapHoverbar hoverbar) {

        // TODO: this should be a link so it can be opened in a new window or tab by the user
        setImageClass(I_CmsImageBundle.INSTANCE.buttonCss().hoverbarGoto());
        setTitle(Messages.get().key(Messages.GUI_HOVERBAR_GOTO_0));
        setShowBorder(false);
        addClickHandler(new ClickHandler() {

            /**
             * @see com.google.gwt.event.dom.client.ClickHandler#onClick(com.google.gwt.event.dom.client.ClickEvent)
             */
            public void onClick(ClickEvent event) {

                hoverbar.hide();
                hoverbar.getController().leaveEditor(hoverbar.getSitePath());
            }
        });
    }
}
