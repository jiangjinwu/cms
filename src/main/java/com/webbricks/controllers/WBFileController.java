package com.webbricks.controllers;

import java.util.Calendar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;

import com.google.appengine.api.blobstore.BlobstoreService;
import com.webbricks.cache.DefaultWBCacheFactory;
import com.webbricks.cache.WBCacheFactory;
import com.webbricks.cache.WBFilesCache;
import com.webbricks.cmsdata.WBFile;
import com.webbricks.cmsdata.WBWebPage;
import com.webbricks.cmsdata.WBWebPageModule;
import com.webbricks.datautility.AdminDataStorage;
import com.webbricks.datautility.AdminDataStorage.AdminQueryOperator;
import com.webbricks.datautility.AdminDataStorageListener;
import com.webbricks.datautility.GaeAdminDataStorage;
import com.webbricks.datautility.WBBlobHandler;
import com.webbricks.datautility.WBBlobInfo;
import com.webbricks.datautility.WBGaeBlobHandler;
import com.webbricks.datautility.WBJSONToFromObjectConverter;
import com.webbricks.datautility.AdminDataStorageListener.AdminDataStorageOperation;
import com.webbricks.exception.WBException;
import com.webbricks.exception.WBIOException;
import com.webbricks.utility.HttpServletToolbox;

public class WBFileController extends WBController implements AdminDataStorageListener<WBFile> {

	private static final String UPLOAD_RETURN_URL = "/wbfileupload"; 
	private HttpServletToolbox httpServletToolbox;
	private WBJSONToFromObjectConverter jsonObjectConverter;
	private AdminDataStorage adminStorage;
	private WBBlobHandler blobHandler;
	private WBFileValidator validator;
	private WBFilesCache imageCache;
	private static final String IMAGE_CONTENT_NAME = "image";
	private static final String VIDEO_CONTENT_NAME = "video";
	private static final String AUDIO_CONTENT_NAME = "audio";
	private static final String APP_CONTENT_NAME   = "application";
	
		
	private String contentTypeToShortType(String contentType)
	{
		contentType = contentType.toLowerCase();
		if (contentType.startsWith(IMAGE_CONTENT_NAME))
		{
			return IMAGE_CONTENT_NAME;
		} else
		if (contentType.startsWith(VIDEO_CONTENT_NAME))
		{
			return VIDEO_CONTENT_NAME;
		} else
		if (contentType.startsWith(AUDIO_CONTENT_NAME))
		{
			return AUDIO_CONTENT_NAME;
		} else
		if (contentType.startsWith(APP_CONTENT_NAME))
		{
			return APP_CONTENT_NAME;
		}
		return APP_CONTENT_NAME;		
	}
	
	public WBFileController()
	{
		httpServletToolbox = new HttpServletToolbox();
		jsonObjectConverter = new WBJSONToFromObjectConverter();
		adminStorage = new GaeAdminDataStorage();
		blobHandler = new WBGaeBlobHandler();
		validator = new WBFileValidator();
		
		WBCacheFactory wbCacheFactory = new DefaultWBCacheFactory();
		imageCache = wbCacheFactory.createWBImagesCacheInstance();
		
		adminStorage.addStorageListener(this);
	}

	public void upload(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{	
			JSONObject obj = new JSONObject();
			obj.put("url", blobHandler.getUploadUrl(getAdminUriPart() + UPLOAD_RETURN_URL));
			httpServletToolbox.writeBodyResponseAsJson(response, obj.toString(), null);		
		} catch (Exception e)
		{
			throw new WBIOException(e.getMessage());
		}
	}

	public void serveImage(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		int size = 0;
		String blobKey = request.getParameter("blobKey");
		String sizeStr = request.getParameter("size");
		if (sizeStr != null && sizeStr.length() > 0)
		{
			try
			{
				size = Integer.valueOf(sizeStr);
			} catch (NumberFormatException e)
			{
				size = 0;
			}			
		}
		String url = blobHandler.serveBlobUrl(blobKey, size);
		response.addHeader("Location", url);
		response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);		
	}
	//content disposition header
	public void serveResource(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			WBFile wbimage = adminStorage.get(key, WBFile.class);
			blobHandler.serveBlob(wbimage.getBlobKey(), response);
						
		} catch (Exception e)		
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
	}
	
	public void downloadResource(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			WBFile wbimage = adminStorage.get(key, WBFile.class);
			blobHandler.serveBlob(wbimage.getBlobKey(), response);
			response.setHeader("Content-Disposition", "attachment; filename=\"" + wbimage.getFileName() + "\"");
						
		} catch (Exception e)		
		{
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		}
		
	}
	
	public void serveImageUrl(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		int size = 0;
		String blobKey = request.getParameter("blobKey");
		String sizeStr = request.getParameter("size");
		if (sizeStr != null && sizeStr.length() > 0)
		{
			try
			{
				size = Integer.valueOf(sizeStr);
			} catch (NumberFormatException e)
			{
				size = 0;
			}			
		}
		String url = blobHandler.serveBlobUrl(blobKey, size);
		try
		{	
			JSONObject obj = new JSONObject();
			obj.put("url", url);
			httpServletToolbox.writeBodyResponseAsJson(response, obj.toString(), null);		
		} catch (Exception e)
		{
			throw new WBIOException(e.getMessage());
		}
		
	}
	
	public void uploadSubmit(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			WBFile image = null;
			String oldBlobKey = null;
			String imgKey = request.getParameter("key");
			if (imgKey != null && imgKey.length()> 0)
			{
				try {
				Long key = Long.valueOf(imgKey);
				image = adminStorage.get(key, WBFile.class);
				
				// store old blob key 
				oldBlobKey = image.getBlobKey();
				
				} catch (NumberFormatException e)
				{
					throw new WBIOException("cannot get Long from key " + imgKey);
				}				
			} else
			{
				image = new WBFile();
				image.setExternalKey(adminStorage.getUniqueId());
				if (request.getParameter("name") != null)
				{
					image.setName(request.getParameter("name"));
				}
			}
			
			WBBlobInfo blobInfo = blobHandler.storeBlob(request);
			if (blobInfo != null)
			{
				image.setBlobKey(blobInfo.getBlobKey());
				image.setContentType(blobInfo.getContentType().toLowerCase());
				image.setAdjustedContentType(blobInfo.getContentType().toLowerCase());
				image.setShortType( contentTypeToShortType(blobInfo.getContentType()) );
				image.setHash(blobInfo.getHash());
				image.setFileName(blobInfo.getFileName());
				if (image.getShortType().equals("text"))
				{
					if (image.getFileName().toLowerCase().endsWith(".js"))
					{
						image.setAdjustedContentType("text/javascript");
					}
					if (image.getFileName().toLowerCase().endsWith(".css"))
					{
						image.setAdjustedContentType("text/css");
					}
					if (image.getFileName().toLowerCase().endsWith(".html"))
					{
						image.setAdjustedContentType("text/html");
					}					
				}
				{
					
				}
				image.setSize(blobInfo.getSize());
				image.setLastModified(Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTime());				
				WBFile storedImage = adminStorage.add(image);
				String referer = request.getHeader("Referer");
				
				if (oldBlobKey != null)
				{
					blobHandler.deleteBlob(oldBlobKey);
				}
				
				if (referer!= null)
				{
					response.addHeader("Location", referer);
					response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
				}
			}
		} catch (Exception e)
		{
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	public void get(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			WBFile wbimage = adminStorage.get(key, WBFile.class);
			String jsonReturn = jsonObjectConverter.JSONStringFromObject(wbimage, null);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonReturn, null);
			
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WBErrors.WB_CANT_GET_RECORDS);
			httpServletToolbox.writeBodyResponseAsJson(response, "", errors);			
		}		
	}
	
	public void getAll(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			String shortType = request.getParameter("type");
			List<WBFile> images = null;
			
			if (null == shortType)
			{
				images = adminStorage.getAllRecords(WBFile.class);
			} else
			{
				shortType = shortType.toLowerCase();
				images = adminStorage.query(WBFile.class, "shortType", AdminQueryOperator.EQUAL, shortType);
			}
			String jsonReturn = jsonObjectConverter.JSONStringFromListObjects(images);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonReturn, null);
			
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WBErrors.WB_CANT_GET_RECORDS);
			httpServletToolbox.writeBodyResponseAsJson(response, "", errors);			
		}
	}

	public void delete(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			WBFile tempImage = adminStorage.get(key, WBFile.class);
			if (tempImage.getBlobKey() != null)
			{
				blobHandler.deleteBlob(tempImage.getBlobKey());
			}
			
			adminStorage.delete(key, WBFile.class);
			
			WBFile param = new WBFile();
			param.setKey(key);
			String jsonReturn = jsonObjectConverter.JSONStringFromObject(param, null);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonReturn, null);
			
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WBErrors.WB_CANT_DELETE_RECORD);
			httpServletToolbox.writeBodyResponseAsJson(response, "", errors);			
		}		
	}

	public void notify (WBFile t, AdminDataStorageOperation o)
	{
		try
		{
			imageCache.Refresh();
		} catch (WBIOException e)
		{
			// TBD
		}
	}

	public void update(HttpServletRequest request, HttpServletResponse response, String requestUri) throws WBException
	{
		try
		{
			Long key = Long.valueOf((String)request.getAttribute("key"));
			String jsonRequest = httpServletToolbox.getBodyText(request);
			WBFile wbimage = (WBFile)jsonObjectConverter.objectFromJSONString(jsonRequest, WBFile.class);
			wbimage.setKey(key);
			Map<String, String> errors = validator.validateUpdate(wbimage);
			
			if (errors.size()>0)
			{
				httpServletToolbox.writeBodyResponseAsJson(response, "", errors);
				return;
			}
			WBFile existingImage = adminStorage.get(key, WBFile.class);
			existingImage.setLastModified(Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTime());
			existingImage.setName(wbimage.getName());
			existingImage.setAdjustedContentType(wbimage.getAdjustedContentType());
			WBFile newImage = adminStorage.update(existingImage);
			
			String jsonReturn = jsonObjectConverter.JSONStringFromObject(newImage, null);
			httpServletToolbox.writeBodyResponseAsJson(response, jsonReturn.toString(), errors);
	
		} catch (Exception e)		
		{
			Map<String, String> errors = new HashMap<String, String>();		
			errors.put("", WBErrors.WB_CANT_UPDATE_RECORD);
			httpServletToolbox.writeBodyResponseAsJson(response, "", errors);			
		}				
	}


}
