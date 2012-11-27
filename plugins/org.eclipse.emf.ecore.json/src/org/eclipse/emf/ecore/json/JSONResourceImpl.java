/*******************************************************************
 * Copyright (c) 2010, Cloudsmith Inc.
 * The code, documentation and other materials contained herein
 * are the sole and exclusive property of Cloudsmith Inc. and may
 * not be disclosed, used, modified, copied or distributed without
 * prior written consent or license from Cloudsmith Inc.
 ******************************************************************/

package org.eclipse.emf.ecore.json;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.json.JSONWrapper.JValue;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

/**
 * An Resource for efficiently producing and consuming a compact JSON serialization that's suitable for long term storage.
 */
public class JSONResourceImpl extends ResourceImpl {
	// The JSONWrapper is different in the browser and in the server. The browser uses
	// the GWT antive JSON parser, int server it uses GSON.
	static final JSONWrapper wrapper = new JSONWrapper();

	public JSONResourceImpl(URI uri) {
		super(uri);
	}

	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
		if(inputStream instanceof URIConverter.Loadable) {
			((URIConverter.Loadable) inputStream).loadResource(this);
		}
		else {
			ByteArrayOutputStream bld = new ByteArrayOutputStream();
			byte buf[] = new byte[2048];
			int cnt;
			while((cnt = inputStream.read(buf)) > 0)
				bld.write(buf, 0, cnt);
			doLoad(new String(bld.toByteArray(), "UTF-8"), options);
		}
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
		if(outputStream instanceof URIConverter.Saveable) {
			((URIConverter.Saveable) outputStream).saveResource(this);
		}
		else {
			outputStream.write(doSave(options).getBytes("UTF-8"));
		}
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
}
