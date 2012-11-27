/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.Callback;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.json.JSONWrapper.JValue;

public class JSONResourceImpl extends ResourceImpl {

	private static URIConverter defaultURIConverter;

	static final JSONWrapper wrapper = new JSONWrapper();

	protected static URIConverter getDefaultClientURIConverter() {
		if(defaultURIConverter == null)
			defaultURIConverter = new ExtensibleURIConverterImpl();
		return defaultURIConverter;
	}

	public JSONResourceImpl(URI uri) {
		super(uri);
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		// Use the JSON String, not byte based IO
		throw new UnsupportedOperationException();
	}

	protected void doLoad(String jsonValue, Map<?, ?> options) throws IOException {
		JSONReader jsonReader = new JSONReader(options);
		jsonReader.fromJSON(wrapper.parse(jsonValue), this);
	}

	protected String doSave(Map<?, ?> options) throws IOException {
		JSONWriter jsonWriter = new JSONWriter(options);
		JValue json = jsonWriter.toJSON(this);
		return wrapper.serialize(json);
	}

	@Override
	protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
		// Use the JSON String, not byte based IO
		throw new UnsupportedOperationException();
	}

	public final void fromJson(String jsonString, Map<?, ?> options) throws IOException {
		if(isLoaded)
			return;

		Notification notification = setLoaded(true);
		isLoading = true;

		if(errors != null)
			errors.clear();

		if(warnings != null)
			warnings.clear();

		try {
			doLoad(jsonString, mergeMaps(options, defaultLoadOptions));
		}
		finally {
			isLoading = false;

			if(notification != null) {
				eNotify(notification);
			}

			setModified(false);
		}
	}

	/**
	 * Saves the resource to the output stream using the specified options.
	 * <p>
	 * This implementation is <code>final</code>; clients should override {@link #doSave doSave}.
	 * </p>
	 * 
	 * @param options
	 *            the save options.
	 * @see #save(Map)
	 * @see #doSave(OutputStream, Map)
	 * @see #load(InputStream, Map)
	 */
	public final String toJson(Map<?, ?> options) throws IOException {
		if(errors != null)
			errors.clear();

		if(warnings != null)
			warnings.clear();

		String json = doSave(mergeMaps(options, defaultSaveOptions));
		setModified(false);
		return json;
	}

	@Override
	protected URIConverter getURIConverter() {
		return getResourceSet() == null
				? getDefaultClientURIConverter()
				: getResourceSet().getURIConverter();
	}

	@Override
	public void load(final Map<?, ?> options, Callback<Resource> callback) throws IOException {
		if(isLoaded) {
			callback.onSuccess(this);
			return;
		}

		if(loadingCallbacks != null) {
			loadingCallbacks.add(callback);
			return;
		}

		isLoading = true;
		loadingCallbacks = new ArrayList<Callback<Resource>>();
		if(callback != null) {
			loadingCallbacks.add(callback);
		}

		URIConverter uriConverter = getURIConverter();
		Map<?, ?> response = null;
		if(options != null)
			response = (Map<?, ?>) options.get(URIConverter.OPTION_RESPONSE);

		if(response == null)
			response = new HashMap<Object, Object>();

		final Map<Object, Object> effectiveOptions = new HashMap<Object, Object>();
		effectiveOptions.put(URIConverter.OPTION_RESPONSE, response);
		if(options != null)
			effectiveOptions.putAll(options);

		uriConverter.createJSON(getURI(), effectiveOptions, new Callback<Map<?, ?>>() {
			protected void dispatchOnFailure(Throwable caught) {
				if(loadingCallbacks != null) {
					for(Callback<Resource> callback : loadingCallbacks) {
						if(callback != null)
							callback.onFailure(caught);
					}
					loadingCallbacks = null;
				}
			}

			protected void dispatchOnSuccess(Resource resource) {
				if(loadingCallbacks != null) {
					for(Callback<Resource> callback : loadingCallbacks) {
						if(callback != null)
							callback.onSuccess(resource);
					}
					loadingCallbacks = null;
				}
			}

			public void onFailure(Throwable caught) {
				Notification notification = setLoaded(true);
				if(errors != null) {
					errors.clear();
				}
				if(warnings != null) {
					warnings.clear();
				}
				isLoading = false;
				if(notification != null) {
					eNotify(notification);
				}
				setModified(false);
				dispatchOnFailure(caught);
			}

			public void onSuccess(Map<?, ?> result) {
				Map<?, ?> response = (Map<?, ?>) result.get(URIConverter.OPTION_RESPONSE);
				String jsonString = (String) response.get(URIConverter.RESPONSE_RESULT);
				try {
					fromJson(jsonString, options);
				}
				catch(IOException exception) {
					dispatchOnFailure(exception);
					return;
				}
				finally {
					isLoading = false;
					handleLoadResponse(response, effectiveOptions);
				}
				dispatchOnSuccess(JSONResourceImpl.this);
			}
		});
	}

	@Override
	public void save(final Map<?, ?> options, final Callback<Resource> callback) throws IOException {
		Map<?, ?> response = null;
		if(options != null)
			response = (Map<?, ?>) options.get(URIConverter.OPTION_RESPONSE);

		if(response == null)
			response = new HashMap<Object, Object>();

		URIConverter uriConverter = getURIConverter();
		Map<Object, Object> effectiveOptions = new HashMap<Object, Object>();
		effectiveOptions.put(URIConverter.OPTION_RESPONSE, response);
		if(options != null)
			effectiveOptions.putAll(options);

		uriConverter.store(getURI(), toJson(options), effectiveOptions, new Callback<Map<?, ?>>() {
			public void onFailure(Throwable caught) {
				callback.onFailure(caught);
			}

			public void onSuccess(Map<?, ?> result) {
				Map<?, ?> response = (Map<?, ?>) result.get(URIConverter.OPTION_RESPONSE);
				handleSaveResponse(response, options);
				callback.onSuccess(JSONResourceImpl.this);
			}
		});
	}
}
