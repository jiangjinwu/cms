/*
 *   Copyright 2014 Webpagebytes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.webpagebytes.cms.controllers;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.webpagebytes.cms.appinterfaces.WPBAdminDataStorage;
import com.webpagebytes.cms.appinterfaces.WPBCacheFactory;
import com.webpagebytes.cms.appinterfaces.WPBWebPagesCache;
import com.webpagebytes.cms.appinterfaces.WPBAdminDataStorage.AdminQueryOperator;
import com.webpagebytes.cms.appinterfaces.WPBAdminDataStorage.AdminSortOperator;
import com.webpagebytes.cms.cache.DefaultWPBCacheFactory;
import com.webpagebytes.cms.cmsdata.WPBParameter;
import com.webpagebytes.cms.cmsdata.WPBResource;
import com.webpagebytes.cms.cmsdata.WPBUri;
import com.webpagebytes.cms.cmsdata.WPBWebPage;
import com.webpagebytes.cms.datautility.WPBAdminDataStorageFactory;
import com.webpagebytes.cms.datautility.WPBAdminDataStorageListener;
import com.webpagebytes.cms.datautility.JSONToFromObjectConverter;
import com.webpagebytes.cms.datautility.local.WPBLocalAdminDataStorage;
import com.webpagebytes.cms.exception.WPBException;
import com.webpagebytes.cms.exception.WPBIOException;
import com.webpagebytes.cms.utility.HttpServletToolbox;

public class PageController extends Controller implements WPBAdminDataStorageListener {

	private static final Logger log = Logger.getLogger(WPBLocalAdminDataStorage.class.getName());
	private WPBAdminDataStorage adminStorage;
	private PageValidator pageValidator;
	private WPBWebPagesCache wbWebPageCache;
	
	public PageController()
	{
		adminStorage = WPBAdminDataStorageFactory.getInstance();
		pageValidator = new PageValidator();
		WPBCacheFactory wbCacheFactory = DefaultWPBCacheFactory.getInstance();
		wbWebPageCache = wbCacheFactory.getWebPagesCacheInstance(); 
		
		adminStorage.addStorageListener(this);
	}
	
	public<T> void notify (T t, AdminDataStorageOperation o, Class<? extends Object> type)
	{
		try
		{
			if (type.equals(WPBWebPage.class))
			{
				log.log(Level.INFO, "WbWebPage datastore notification, going to refresh the cache");
				wbWebPageCache.Refresh();
			}
		} catch (WPBIOException e)
		{
			// TBD
		}
	}
	
	public void create(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WPBException
	{
		try
		{
			String jsonRequest = httpServletToolbox.getBodyText(request);
			WPBWebPage webPage = (WPBWebPage)jsonObjectConverter.objectFromJSONString(jsonRequest, WPBWebPage.class);
			Map<String, String> errors = pageValidator.validateCreate(webPage);
			
			if (errors.size()>0)
			{
				httpServletToolbox.writeBodyResponseAsJson(response, "{}", errors);
				return;
			}
			webPage.setHash( WPBWebPage.crc32(webPage.getHtmlSource()));
			webPage.setLastModified(Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTime());
			webPage.setExternalKey(adminStorage.getUniqueId());
			WPBWebPage newWebPage = adminStorage.add(webPage);
			
			WPBResource resource = new WPBResource(newWebPage.getExternalKey(), newWebPage.getName(), WPBResource.PAGE_TYPE);
			try
			{
				adminStorage.addWithKey(resource);
			} catch (Exception e)
			{
				// do not propagate further
			}
			org.json.JSONObject returnJson = new org.json.JSONObject();
			returnJson.put(DATA, jsonObjectConverter.JSONFromObject(newWebPage));			
			httpServletToolbox.writeBodyResponseAsJson(response, returnJson, null);

		} catch (Exception e)
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WPBErrors.WB_CANT_CREATE_RECORD);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonObjectConverter.JSONObjectFromMap(null), errors);			
		}
	}
	public void getAll(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WPBException
	{
		try
		{
			Map<String, Object> additionalInfo = new HashMap<String, Object> ();			
			String sortParamDir = request.getParameter(SORT_PARAMETER_DIRECTION);
			String sortParamProp = request.getParameter(SORT_PARAMETER_PROPERTY);
			List<WPBWebPage> allRecords = null;
			if (sortParamDir != null && sortParamProp != null)
			{
				if (sortParamDir.equals(SORT_PARAMETER_DIRECTION_ASC))
				{
					additionalInfo.put(SORT_PARAMETER_DIRECTION, SORT_PARAMETER_DIRECTION_ASC);
					additionalInfo.put(SORT_PARAMETER_PROPERTY, sortParamProp);
					allRecords = adminStorage.getAllRecords(WPBWebPage.class, sortParamProp, AdminSortOperator.ASCENDING);					
				} else if (sortParamDir.equals(SORT_PARAMETER_DIRECTION_DSC))
				{
					additionalInfo.put(SORT_PARAMETER_DIRECTION, SORT_PARAMETER_DIRECTION_DSC);
					additionalInfo.put(SORT_PARAMETER_PROPERTY, sortParamProp);
					allRecords = adminStorage.getAllRecords(WPBWebPage.class, sortParamProp, AdminSortOperator.DESCENDING);
				} else
				{
					allRecords = adminStorage.getAllRecords(WPBWebPage.class);					
				}
			} else
			{
				allRecords = adminStorage.getAllRecords(WPBWebPage.class);				
			}
					
			List<WPBWebPage> result = filterPagination(request, allRecords, additionalInfo);
			
			org.json.JSONObject returnJson = new org.json.JSONObject();
			returnJson.put(DATA, jsonObjectConverter.JSONArrayFromListObjects(result));
			returnJson.put(ADDTIONAL_DATA, jsonObjectConverter.JSONObjectFromMap(additionalInfo));
			httpServletToolbox.writeBodyResponseAsJson(response, returnJson, null);

			
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WPBErrors.WB_CANT_GET_RECORDS);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonObjectConverter.JSONObjectFromMap(null), errors);			
		}
	}
	
	private org.json.JSONObject get(HttpServletRequest request, HttpServletResponse response, WPBWebPage webPage) throws WPBException
	{
		try
		{
			org.json.JSONObject returnJson = new org.json.JSONObject();
			returnJson.put(DATA, jsonObjectConverter.JSONFromObject(webPage));			
	
			String includeLinks = request.getParameter("include_links");
			if (includeLinks != null && includeLinks.equals("1"))
			{
				List<WPBUri> uris = adminStorage.query(WPBUri.class, "resourceExternalKey", AdminQueryOperator.EQUAL, webPage.getExternalKey());
				org.json.JSONArray arrayUris = jsonObjectConverter.JSONArrayFromListObjects(uris);
				org.json.JSONObject additionalData = new org.json.JSONObject();
				additionalData.put("uri_links", arrayUris);
				returnJson.put(ADDTIONAL_DATA, additionalData);			
			}
	
			return returnJson;
	
		} catch (Exception e)		
		{
			throw new WPBException("cannot get web page details ", e);
		}		
		
	}
	
	public void get(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WPBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			WPBWebPage webPage = adminStorage.get(key, WPBWebPage.class);
			org.json.JSONObject returnJson = get(request, response, webPage);
			httpServletToolbox.writeBodyResponseAsJson(response, returnJson, null);

		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WPBErrors.WB_CANT_GET_RECORDS);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonObjectConverter.JSONObjectFromMap(null), errors);			
		}		
	}

	public void getExt(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WPBException
	{
		try
		{
			String extKey = (String)request.getAttribute("key");
			List<WPBWebPage> webPages = adminStorage.query(WPBWebPage.class, "externalKey", AdminQueryOperator.EQUAL, extKey);			
			WPBWebPage webPage = (webPages.size()>0) ? webPages.get(0) : null; 		
			org.json.JSONObject returnJson = get(request, response, webPage);
			httpServletToolbox.writeBodyResponseAsJson(response, returnJson, null);

		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WPBErrors.WB_CANT_GET_RECORDS);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonObjectConverter.JSONObjectFromMap(null), errors);			
		}		
	}

	public void delete(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WPBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			WPBWebPage tempPage = adminStorage.get(key, WPBWebPage.class);
			adminStorage.delete(key, WPBWebPage.class);
			
			// delete the owned parameters
			adminStorage.delete(WPBParameter.class, "ownerExternalKey", AdminQueryOperator.EQUAL, tempPage.getExternalKey());
			try
			{
				adminStorage.delete(tempPage.getExternalKey(), WPBResource.class);
			} catch (Exception e)
			{
				// do not propagate further
			}
			WPBWebPage page = new WPBWebPage();
			page.setPrivkey(key);
			org.json.JSONObject returnJson = new org.json.JSONObject();
			returnJson.put(DATA, jsonObjectConverter.JSONFromObject(page));			
			httpServletToolbox.writeBodyResponseAsJson(response, returnJson, null);
			
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WPBErrors.WB_CANT_DELETE_RECORD);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonObjectConverter.JSONObjectFromMap(null), errors);			
		}		
	}

	public void update(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WPBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			String jsonRequest = httpServletToolbox.getBodyText(request);
			WPBWebPage webPage = (WPBWebPage)jsonObjectConverter.objectFromJSONString(jsonRequest, WPBWebPage.class);
			webPage.setPrivkey(key);
			Map<String, String> errors = pageValidator.validateUpdate(webPage);
			
			if (errors.size()>0)
			{
				httpServletToolbox.writeBodyResponseAsJson(response, "{}", errors);
				return;
			}
			CRC32 crc = new CRC32();
			crc.update(webPage.getHtmlSource().getBytes());
			webPage.setHash( crc.getValue() );

			webPage.setLastModified(Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTime());
			WPBWebPage newWebPage = adminStorage.update(webPage);
			
			WPBResource resource = new WPBResource(newWebPage.getExternalKey(), newWebPage.getName(), WPBResource.PAGE_TYPE);
			try
			{
				adminStorage.update(resource);
			} catch (Exception e)
			{
				// do not propate further
			}
			org.json.JSONObject returnJson = new org.json.JSONObject();
			returnJson.put(DATA, jsonObjectConverter.JSONFromObject(newWebPage));			
			httpServletToolbox.writeBodyResponseAsJson(response, returnJson, null);
	
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WPBErrors.WB_CANT_UPDATE_RECORD);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonObjectConverter.JSONObjectFromMap(null), errors);			
		}		
	}
		

	public void setPageValidator(PageValidator pageValidator) {
		this.pageValidator = pageValidator;
	}

	public void setHttpServletToolbox(HttpServletToolbox httpServletToolbox) {
		this.httpServletToolbox = httpServletToolbox;
	}

	public void setJsonObjectConverter(
			JSONToFromObjectConverter jsonObjectConverter) {
		this.jsonObjectConverter = jsonObjectConverter;
	}

	public void setAdminStorage(WPBAdminDataStorage adminStorage) {
		this.adminStorage = adminStorage;
	}
	public void setPageCache(WPBWebPagesCache pageCache)
	{
		this.wbWebPageCache = pageCache;
	}
	
	
}
