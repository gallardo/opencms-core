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

package org.opencms.jsp.jsonpart;

import org.opencms.flex.CmsFlexController;
import org.opencms.main.CmsLog;

import java.io.IOException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import javax.servlet.jsp.tagext.TryCatchFinally;

import org.apache.commons.logging.Log;

/**
 * Tag used to convert the HTML output of this tag's contents to encoded JSON.<p>
 *
 * It only makes sense to use this tag in combination with the servlet filter org.opencms.jsp.jsonpart.CmsJsonPartFilter.
 * This tag converts the text generated by its contained JSP code and converts it into a special encoded form, which is then
 * used by the filter to generate JSON. The 'element' attribute on this tag can be used to control the JSON key which will be used for
 * the content.
 */
public class CmsJspTagJsonPart extends TagSupport implements TryCatchFinally {

    /** Serial version id. */
    private static final long serialVersionUID = 1L;

    /** Log instance for this class. */
    private static final Log LOG = CmsLog.getLog(CmsJspTagJsonPart.class);

    /** The name to be used as a key for the JSON part. */
    private String m_element;

    /** Variable to keep track of whether we still need to write the end marker. */
    private boolean m_needEnd;

    /**
     * @see javax.servlet.jsp.tagext.TryCatchFinally#doCatch(java.lang.Throwable)
     */
    public void doCatch(Throwable arg0) throws Throwable {

        throw arg0;
    }

    /**
     * @see javax.servlet.jsp.tagext.TagSupport#doEndTag()
     */
    @Override
    public int doEndTag() throws JspException {

        if (CmsFlexController.isCmsRequest(pageContext.getRequest())
            && CmsJsonPartFilter.isJsonRequest(pageContext.getRequest())) {
            try {
                pageContext.getOut().write(CmsJsonPart.END);
                m_needEnd = false;
            } catch (IOException e) {
                throw new JspException(e);
            }
        }
        return EVAL_PAGE;
    }

    /**
     * @see javax.servlet.jsp.tagext.TryCatchFinally#doFinally()
     */
    public void doFinally() {

        if (m_needEnd) { // Exception happened before we could write the end marker
            m_needEnd = false;
            try {
                pageContext.getOut().write(CmsJsonPart.END);
            } catch (IOException e) {
                LOG.error(e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * @see javax.servlet.jsp.tagext.TagSupport#doStartTag()
     */
    @Override
    public int doStartTag() throws JspException {

        if (null != findAncestorWithClass(this, CmsJspTagJsonPart.class)) {
            throw new JspException("cms:jsonpart tag must not be nested!");
        }

        if (CmsFlexController.isCmsRequest(pageContext.getRequest())
            && CmsJsonPartFilter.isJsonRequest(pageContext.getRequest())) {
            try {
                pageContext.getOut().write(CmsJsonPart.getHeader(getElement()));
                m_needEnd = true;
            } catch (IOException e) {
                throw new JspException(e);
            }
        }
        return EVAL_BODY_INCLUDE;
    }

    /**
     * Returns the name to be used as the JSON key.<p>
     *
     * @return the name to be used as a JSON key
     */
    public String getElement() {

        return m_element;
    }

    /**
     * Sets the name to be used as a JSON key.<p>
     *
     * @param elementName the name to be used as a JSON key
     */
    public void setElement(String elementName) {

        m_element = elementName;
    }

}
