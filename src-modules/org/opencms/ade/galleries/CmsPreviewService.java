/*
 * File   : $Source: /alkacon/cvs/opencms/src-modules/org/opencms/ade/galleries/Attic/CmsPreviewService.java,v $
 * Date   : $Date: 2011/01/20 07:10:58 $
 * Version: $Revision: 1.7 $
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

package org.opencms.ade.galleries;

import org.opencms.ade.galleries.shared.CmsImageInfoBean;
import org.opencms.ade.galleries.shared.CmsResourceInfoBean;
import org.opencms.ade.galleries.shared.rpc.I_CmsPreviewService;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.gwt.CmsGwtService;
import org.opencms.gwt.CmsRpcException;
import org.opencms.loader.CmsImageScaler;
import org.opencms.lock.CmsLock;
import org.opencms.main.CmsException;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.CmsWorkplaceMessages;
import org.opencms.workplace.explorer.CmsExplorerTypeSettings;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Handles all RPC services related to the gallery preview dialog.<p>
 * 
 * @author Polina Smagina
 * @author Tobias Herrmann
 * 
 * @version $Revision: 1.7 $ 
 * 
 * @since 8.0.0
 */
public class CmsPreviewService extends CmsGwtService implements I_CmsPreviewService {

    /** Serialization uid. */
    private static final long serialVersionUID = -8175522641937277445L;

    /**
     * Retrieves the resource information and puts it into the provided resource info bean.<p>
     * 
     * @param cms the initialized cms object
     * @param resource the resource
     * @param resInfo the resource info bean
     * 
     * @throws CmsException if something goes wrong
     */
    public static void readResourceInfo(CmsObject cms, CmsResource resource, CmsResourceInfoBean resInfo)
    throws CmsException {

        I_CmsResourceType type = OpenCms.getResourceManager().getResourceType(resource.getTypeId());

        resInfo.setTitle(resource.getName());
        resInfo.setDescription(CmsWorkplaceMessages.getResourceTypeName(
            OpenCms.getWorkplaceManager().getWorkplaceLocale(cms),
            type.getTypeName()));
        resInfo.setResourcePath(cms.getSitePath(resource));
        resInfo.setResourceType(type.getTypeName());
        resInfo.setSize(resource.getLength() / 1024 + " kb");
        resInfo.setLastModified(new Date(resource.getDateLastModified()));

        // reading default explorer-type properties
        CmsExplorerTypeSettings setting = OpenCms.getWorkplaceManager().getExplorerTypeSetting(type.getTypeName());
        List<String> properties = setting.getProperties();
        String reference = setting.getReference();

        // looking up properties from referenced explorer types if properties list is empty

        while ((properties.size() == 0) && !CmsStringUtil.isEmptyOrWhitespaceOnly(reference)) {
            setting = OpenCms.getWorkplaceManager().getExplorerTypeSetting(reference);
            properties = setting.getProperties();
            reference = setting.getReference();
        }
        Map<String, String> props = new LinkedHashMap<String, String>();
        Iterator<String> propIt = properties.iterator();
        while (propIt.hasNext()) {
            String propertyName = propIt.next();
            CmsProperty property = cms.readPropertyObject(resource, propertyName, false);
            if (!property.isNullProperty()) {
                props.put(property.getName(), property.getValue());
            } else {
                props.put(propertyName, null);
            }
        }
        resInfo.setProperties(props);
    }

    /**
     * @see org.opencms.ade.galleries.shared.rpc.I_CmsPreviewService#getImageInfo(java.lang.String)
     */
    public CmsImageInfoBean getImageInfo(String resourcePath) throws CmsRpcException {

        CmsObject cms = getCmsObject();
        CmsImageInfoBean resInfo = new CmsImageInfoBean();
        try {
            int pos = resourcePath.indexOf("?");
            String resName = resourcePath;
            if (pos > -1) {
                resName = resourcePath.substring(0, pos);
            }
            CmsResource resource = cms.readResource(resName);
            readResourceInfo(cms, resource, resInfo);
            resInfo.setHash(resource.getStructureId().hashCode());
            CmsImageScaler scaler = new CmsImageScaler(cms, resource);
            int height = -1;
            int width = -1;
            if (scaler.isValid()) {
                height = scaler.getHeight();
                width = scaler.getWidth();
            }
            resInfo.setHeight(height);
            resInfo.setWidth(width);
            CmsProperty property = cms.readPropertyObject(resource, CmsPropertyDefinition.PROPERTY_COPYRIGHT, false);
            if (!property.isNullProperty()) {
                resInfo.setCopyright(property.getValue());
            }
        } catch (Exception e) {
            error(e);
        }
        return resInfo;
    }

    /**
     * @see org.opencms.ade.galleries.shared.rpc.I_CmsPreviewService#getResourceInfo(java.lang.String)
     */
    public CmsResourceInfoBean getResourceInfo(String resourcePath) throws CmsRpcException {

        CmsObject cms = getCmsObject();
        CmsResourceInfoBean resInfo = new CmsResourceInfoBean();
        try {
            int pos = resourcePath.indexOf("?");
            String resName = resourcePath;
            if (pos > -1) {
                resName = resourcePath.substring(0, pos);
            }
            CmsResource resource = cms.readResource(resName);
            readResourceInfo(cms, resource, resInfo);
        } catch (CmsException e) {
            error(e);
        }
        return resInfo;
    }

    /**
     * @see org.opencms.ade.galleries.shared.rpc.I_CmsPreviewService#updateImageProperties(java.lang.String, java.util.Map)
     */
    public CmsImageInfoBean updateImageProperties(String resourcePath, Map<String, String> properties)
    throws CmsRpcException {

        try {
            saveProperties(resourcePath, properties);
        } catch (CmsException e) {
            error(e);
        }
        return getImageInfo(resourcePath);
    }

    /**
     * @see org.opencms.ade.galleries.shared.rpc.I_CmsPreviewService#updateResourceProperties(java.lang.String, java.util.Map)
     */
    public CmsResourceInfoBean updateResourceProperties(String resourcePath, Map<String, String> properties)
    throws CmsRpcException {

        try {
            saveProperties(resourcePath, properties);
        } catch (CmsException e) {
            error(e);
        }
        return getResourceInfo(resourcePath);
    }

    /**
     * Saves the given properties to the resource.<p>
     * 
     * @param resourcePath the resource path
     * @param properties the properties
     * 
     * @throws CmsException if something goes wrong
     */
    private void saveProperties(String resourcePath, Map<String, String> properties) throws CmsException {

        CmsResource resource;
        CmsObject cms = getCmsObject();
        int pos = resourcePath.indexOf("?");
        String resName = resourcePath;
        if (pos > -1) {
            resName = resourcePath.substring(0, pos);
        }
        resource = cms.readResource(resName);

        if (properties != null) {
            for (Entry<String, String> entry : properties.entrySet()) {
                String propertyName = entry.getKey();
                String propertyValue = entry.getValue();
                if (CmsStringUtil.isEmptyOrWhitespaceOnly(propertyValue)) {
                    propertyValue = "";
                }
                try {
                    CmsProperty currentProperty = cms.readPropertyObject(resource, propertyName, false);
                    // detect if property is a null property or not
                    if (currentProperty.isNullProperty()) {
                        // create new property object and set key and value
                        currentProperty = new CmsProperty();
                        currentProperty.setName(propertyName);
                        if (OpenCms.getWorkplaceManager().isDefaultPropertiesOnStructure()) {
                            // set structure value
                            currentProperty.setStructureValue(propertyValue);
                            currentProperty.setResourceValue(null);
                        } else {
                            // set resource value
                            currentProperty.setStructureValue(null);
                            currentProperty.setResourceValue(propertyValue);
                        }
                    } else if (currentProperty.getStructureValue() != null) {
                        // structure value has to be updated
                        currentProperty.setStructureValue(propertyValue);
                        currentProperty.setResourceValue(null);
                    } else {
                        // resource value has to be updated
                        currentProperty.setStructureValue(null);
                        currentProperty.setResourceValue(propertyValue);
                    }
                    CmsLock lock = cms.getLock(resource);
                    if (lock.isUnlocked()) {
                        // lock resource before operation
                        cms.lockResource(resName);
                    }
                    // write the property to the resource
                    cms.writePropertyObject(resName, currentProperty);
                    // unlock the resource
                    cms.unlockResource(resName);
                } catch (CmsException e) {
                    // writing the property failed, log error
                    log(e.getLocalizedMessage());
                }
            }
        }
    }

}