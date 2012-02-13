/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
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

package org.opencms.ade.contenteditor.client;

import com.alkacon.acacia.client.ComplexTypeRenderer;
import com.alkacon.acacia.client.I_EntityRenderer;
import com.alkacon.acacia.client.I_WidgetFactory;
import com.alkacon.acacia.client.WidgetService;
import com.alkacon.acacia.client.css.I_LayoutBundle;
import com.alkacon.acacia.client.widgets.I_EditWidget;
import com.alkacon.acacia.client.widgets.StringWidget;
import com.alkacon.acacia.client.widgets.TinyMCEWidget;
import com.alkacon.acacia.shared.ContentDefinition;
import com.alkacon.vie.client.Entity;
import com.alkacon.vie.client.I_Vie;
import com.alkacon.vie.client.Vie;
import com.alkacon.vie.shared.I_Entity;
import com.alkacon.vie.shared.I_Type;

import org.opencms.ade.contenteditor.shared.rpc.I_CmsContentService;
import org.opencms.ade.contenteditor.shared.rpc.I_CmsContentServiceAsync;
import org.opencms.gwt.client.A_CmsEntryPoint;
import org.opencms.gwt.client.CmsCoreProvider;
import org.opencms.gwt.client.rpc.CmsRpcAction;
import org.opencms.gwt.client.rpc.CmsRpcPrefetcher;
import org.opencms.gwt.client.ui.CmsPushButton;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Content editor entry point.<p>
 */
public class ContentEditor extends A_CmsEntryPoint {

    /** The content service instance. */
    private static I_CmsContentServiceAsync SERVICE;

    /** The entity id. */
    protected String m_entityId;

    /** The content locale. */
    protected String m_locale;

    /** The save button. */
    protected CmsPushButton m_saveButton;

    /**
     * @see com.google.gwt.core.client.EntryPoint#onModuleLoad()
     */
    @Override
    public void onModuleLoad() {

        super.onModuleLoad();
        I_LayoutBundle.INSTANCE.style().ensureInjected();
        m_saveButton = new CmsPushButton();
        m_saveButton.setText("Save");
        m_saveButton.disable("Nothing to save yet");
        m_saveButton.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {

                saveEntity();
            }
        });
        I_Vie vie = Vie.getInstance();
        ContentDefinition definition = null;
        try {
            definition = (ContentDefinition)CmsRpcPrefetcher.getSerializedObjectFromDictionary(
                getService(),
                I_CmsContentService.DICT_CONTENT_DEFINITION);
        } catch (SerializationException e) {
            RootPanel.get().add(new Label(e.getMessage()));
            return;
        }
        m_entityId = definition.getEntity().getId();
        m_locale = definition.getLocale();
        FlowPanel panel = new FlowPanel();
        RootPanel.get().add(panel);
        panel.add(m_saveButton);
        HTML html = new HTML();
        panel.add(html);
        I_Type baseType = definition.getTypes().get(definition.getEntity().getTypeName());
        vie.registerTypes(baseType, definition.getTypes());
        I_Entity entity = vie.registerEntity(definition.getEntity());

        WidgetService service = new WidgetService();
        service.init(definition);
        service.registerWidgetFactory("string", new I_WidgetFactory() {

            public I_EditWidget createWidget(String configuration) {

                return new StringWidget();
            }
        });
        service.registerWidgetFactory("org.opencms.widgets.CmsHtmlWidget", new I_WidgetFactory() {

            public I_EditWidget createWidget(String configuration) {

                return new TinyMCEWidget(null);
            }
        });

        I_EntityRenderer inlineRenderer = new ComplexTypeRenderer(service, vie);
        service.setDefaultComplexRenderer(inlineRenderer);
        service.setDefaultSimpleRenderer(inlineRenderer);
        I_EntityRenderer renderer = service.getRendererForType(vie.getType(entity.getTypeName()));
        renderer.render(entity, html.getElement());

        ((Entity)entity).addValueChangeHandler(new ValueChangeHandler<I_Entity>() {

            public void onValueChange(ValueChangeEvent<I_Entity> event) {

                m_saveButton.enable();
            }
        });

    }

    /**
     * Returns the content service instance.<p>
     * 
     * @return the content service
     */
    protected I_CmsContentServiceAsync getService() {

        if (SERVICE == null) {
            SERVICE = GWT.create(I_CmsContentService.class);
            String serviceUrl = CmsCoreProvider.get().link("org.opencms.ade.contenteditor.CmsContentService.gwt");
            ((ServiceDefTarget)SERVICE).setServiceEntryPoint(serviceUrl);
        }
        return SERVICE;
    }

    /**
     * Saves the entity.<p>
     */
    protected void saveEntity() {

        I_Vie vie = Vie.getInstance();
        final I_Entity entity = vie.getEntity(m_entityId);
        if (entity != null) {
            CmsRpcAction<Void> action = new CmsRpcAction<Void>() {

                @Override
                public void execute() {

                    getService().saveEntity(com.alkacon.acacia.shared.Entity.serializeEntity(entity), m_locale, this);

                }

                @Override
                protected void onResponse(Void result) {

                    m_saveButton.disable("Nothing to save yet.");

                }
            };
            action.execute();
        }
    }

}
